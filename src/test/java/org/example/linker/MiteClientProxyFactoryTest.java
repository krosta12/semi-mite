package org.example.linker;

import org.example.linker.NativeLibraryLinker.LinkedLibrary;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MiteClientProxyFactoryTest {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final NativeMarshaller MARSHALLER = new NativeMarshaller();

    private final MiteClientProxyFactory factory = new MiteClientProxyFactory();

    public interface Calc {
        int add(int a, int b);
    }

    public interface Greeter {
        String greet(String name);
    }

    public interface StructClient {
        PointDto identity(PointDto p);
    }

    public interface ArrayClient {
        void doubleInPlace(int[] values);
    }

    public interface NoBinding {
        int missing();
    }

    public interface Thrower {
        int boom(int x);
    }


    static int nativeAdd(int a, int b) {
        return a + b;
    }

    static MemorySegment nativeGreet(MemorySegment nameSeg) {
        String name = MARSHALLER.readNullTerminatedString(nameSeg);
        return Arena.global().allocateFrom("Hello, " + name);
    }

    static MemorySegment nativeIdentity(MemorySegment seg) {
        return seg;
    }

    static void nativeDoubleInts(MemorySegment seg) {
        int count = (int) (seg.byteSize() / 4);
        for (int i = 0; i < count; i++) {
            int v = seg.getAtIndex(ValueLayout.JAVA_INT, i);
            seg.setAtIndex(ValueLayout.JAVA_INT, i, v * 2);
        }
    }

    static int nativeThrows(int x) {
        throw new RuntimeException("boom");
    }

    private ManagedLibrary singleHandleLibrary(String name, MethodHandle handle) {
        return new ManagedLibrary(new LinkedLibrary(Map.of(name, handle), Arena.ofShared()));
    }

    private MethodHandle handleOf(String methodName, MethodType type) throws Exception {
        return LOOKUP.findStatic(MiteClientProxyFactoryTest.class, methodName, type);
    }

    @Test
    void primitiveArgsAndReturnPassThroughUnmarshalled() throws Exception {
        MethodHandle h = handleOf("nativeAdd", MethodType.methodType(int.class, int.class, int.class));
        Calc calc = factory.create(Calc.class, singleHandleLibrary("add", h));

        assertEquals(7, calc.add(3, 4));
    }

    @Test
    void greetMarshalsStringArgAndUnmarshalsStringReturn() throws Exception {
        MethodHandle h = handleOf("nativeGreet", MethodType.methodType(MemorySegment.class, MemorySegment.class));
        Greeter greeter = factory.create(Greeter.class, singleHandleLibrary("greet", h));

        assertEquals("Hello, World", greeter.greet("World"));
    }

    @Test
    void structArgAndReturnRoundTripsViaAliasCache() throws Exception {
        MethodHandle h = handleOf("nativeIdentity", MethodType.methodType(MemorySegment.class, MemorySegment.class));
        StructClient client = factory.create(StructClient.class, singleHandleLibrary("identity", h));

        PointDto input = new PointDto();
        input.x = 1.5f;
        input.y = 2.5f;
        input.label = "p1";

        PointDto result = client.identity(input);

        assertSame(input, result);
    }

    @Test
    void intArrayIsCopiedBackAfterNativeCall() throws Exception {
        MethodHandle h = handleOf("nativeDoubleInts", MethodType.methodType(void.class, MemorySegment.class));
        ArrayClient client = factory.create(ArrayClient.class, singleHandleLibrary("doubleInPlace", h));

        int[] values = {1, 2, 3};
        client.doubleInPlace(values);

        assertArrayEquals(new int[]{2, 4, 6}, values);
    }

    @Test
    void throwsWhenNoNativeBindingForMethod() {
        NoBinding client = factory.create(NoBinding.class,
                new ManagedLibrary(new LinkedLibrary(Map.of(), Arena.ofShared())));

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, client::missing);
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void throwsWhenLibraryIsBeingReloaded() throws Exception {
        MethodHandle h = handleOf("nativeAdd", MethodType.methodType(int.class, int.class, int.class));
        ManagedLibrary lib = singleHandleLibrary("add", h);
        lib.retire();

        Calc calc = factory.create(Calc.class, lib);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> calc.add(1, 2));
        assertTrue(ex.getMessage().toLowerCase().contains("reload"));
    }

    @Test
    void wrapsNativeExceptionAndStillReleasesLibrary() throws Exception {
        MethodHandle h = handleOf("nativeThrows", MethodType.methodType(int.class, int.class));
        ManagedLibrary lib = singleHandleLibrary("boom", h);
        Thrower thrower = factory.create(Thrower.class, lib);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> thrower.boom(1));
        assertNotNull(ex.getCause());
        assertEquals("boom", ex.getCause().getMessage());

        assertTrue(lib.acquire(), "library should not be leaked/stuck after a native call failure");
        lib.release();
    }

    @Test
    void supplierAllowsHotSwapAcrossCalls() throws Exception {
        MethodHandle h = handleOf("nativeAdd", MethodType.methodType(int.class, int.class, int.class));
        ManagedLibrary libV1 = singleHandleLibrary("add", h);
        ManagedLibrary libV2 = singleHandleLibrary("add", h);

        AtomicInteger callCount = new AtomicInteger(0);
        Calc calc = factory.create(Calc.class, () ->
                callCount.getAndIncrement() == 0 ? libV1 : libV2);

        assertEquals(7, calc.add(3, 4));
        assertEquals(7, calc.add(3, 4));
        assertEquals(2, callCount.get());

        libV1.retire();
        assertFalse(libV1.acquire());
    }
}