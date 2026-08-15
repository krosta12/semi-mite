package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.MiteStruct;
import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;
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


class MiteClientProxyFactoryIntegrationTest {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final NativeMarshaller MARSHALLER = new NativeMarshaller();
    private final MiteClientProxyFactory factory = new MiteClientProxyFactory();


    public interface Calculator {
        int add(int a, int b);

        int multiply(int a, int b);

        double sqrt(double x);
    }

    public interface StringProcessor {
        String toUpperCase(String input);

        String concat(String a, String b);
    }

    public interface ArrayTransformer {
        void negate(int[] values);

        void scale(float[] values);

        void increment(double[] values);
    }

    public interface VoidConsumer {
        void consume(int x);
    }

    public interface FailingClient {
        int alwaysFails(int x);
    }

    public interface MultiCallClient {
        int compute(int x);
    }


    static int nativeAdd(int a, int b) {
        return a + b;
    }

    static int nativeMultiply(int a, int b) {
        return a * b;
    }

    static double nativeSqrt(double x) {
        return Math.sqrt(x);
    }

    static MemorySegment nativeToUpperCase(MemorySegment s) {
        String str = MARSHALLER.readNullTerminatedString(s);
        return Arena.global().allocateFrom(str.toUpperCase());
    }

    static MemorySegment nativeConcat(MemorySegment a, MemorySegment b) {
        String sa = MARSHALLER.readNullTerminatedString(a);
        String sb = MARSHALLER.readNullTerminatedString(b);
        return Arena.global().allocateFrom(sa + sb);
    }

    static void nativeNegate(MemorySegment seg) {
        int count = (int) (seg.byteSize() / 4);
        for (int i = 0; i < count; i++) {
            seg.setAtIndex(ValueLayout.JAVA_INT, i, -seg.getAtIndex(ValueLayout.JAVA_INT, i));
        }
    }

    static void nativeScale(MemorySegment seg) {
        int count = (int) (seg.byteSize() / 4);
        for (int i = 0; i < count; i++) {
            seg.setAtIndex(ValueLayout.JAVA_FLOAT, i, seg.getAtIndex(ValueLayout.JAVA_FLOAT, i) * 2f);
        }
    }

    static void nativeIncrement(MemorySegment seg) {
        int count = (int) (seg.byteSize() / 8);
        for (int i = 0; i < count; i++) {
            seg.setAtIndex(ValueLayout.JAVA_DOUBLE, i, seg.getAtIndex(ValueLayout.JAVA_DOUBLE, i) + 1.0);
        }
    }

    static void nativeConsume(int x) { /* no-op */ }

    static int nativeThrows(int x) {
        throw new RuntimeException("native-failure");
    }

    static final AtomicInteger CALL_COUNTER = new AtomicInteger(0);

    static int nativeCompute(int x) {
        CALL_COUNTER.incrementAndGet();
        return x * 2;
    }


    private MethodHandle handle(String name, MethodType type) throws Exception {
        return LOOKUP.findStatic(MiteClientProxyFactoryIntegrationTest.class, name, type);
    }

    private ManagedLibrary libraryWith(Map<String, MethodHandle> handles) {
        return new ManagedLibrary(new LinkedLibrary(handles, Arena.ofShared()));
    }


    @Test
    void multiMethodProxyDispatchesCorrectly() throws Exception {
        Map<String, MethodHandle> handles = Map.of(
                "add", handle("nativeAdd", MethodType.methodType(int.class, int.class, int.class)),
                "multiply", handle("nativeMultiply", MethodType.methodType(int.class, int.class, int.class)),
                "sqrt", handle("nativeSqrt", MethodType.methodType(double.class, double.class))
        );

        Calculator calc = factory.create(Calculator.class, libraryWith(handles));

        assertEquals(7, calc.add(3, 4));
        assertEquals(12, calc.multiply(3, 4));
        assertEquals(3.0, calc.sqrt(9.0), 1e-9);
    }


    @Test
    void multiStringMethodProxyWorks() throws Exception {
        Map<String, MethodHandle> handles = Map.of(
                "toUpperCase", handle("nativeToUpperCase",
                        MethodType.methodType(MemorySegment.class, MemorySegment.class)),
                "concat", handle("nativeConcat",
                        MethodType.methodType(MemorySegment.class, MemorySegment.class, MemorySegment.class))
        );

        StringProcessor proc = factory.create(StringProcessor.class, libraryWith(handles));

        assertEquals("HELLO WORLD", proc.toUpperCase("hello world"));
        assertEquals("FooBar", proc.concat("Foo", "Bar"));
    }


    @Test
    void arrayTransformerCopyBackForAllPrimitiveTypes() throws Exception {
        Map<String, MethodHandle> handles = Map.of(
                "negate", handle("nativeNegate",
                        MethodType.methodType(void.class, MemorySegment.class)),
                "scale", handle("nativeScale",
                        MethodType.methodType(void.class, MemorySegment.class)),
                "increment", handle("nativeIncrement",
                        MethodType.methodType(void.class, MemorySegment.class))
        );

        ArrayTransformer t = factory.create(ArrayTransformer.class, libraryWith(handles));

        int[] ints = {1, 2, 3};
        t.negate(ints);
        assertArrayEquals(new int[]{-1, -2, -3}, ints, "int[] must be negated");

        float[] floats = {1f, 2f, 4f};
        t.scale(floats);
        assertArrayEquals(new float[]{2f, 4f, 8f}, floats, 1e-6f, "float[] must be doubled");

        double[] doubles = {10.0, 20.0};
        t.increment(doubles);
        assertArrayEquals(new double[]{11.0, 21.0}, doubles, 1e-9, "double[] must be incremented");
    }


    @Test
    void voidMethodCallSucceedsAndReleasesLibrary() throws Exception {
        MethodHandle h = handle("nativeConsume",
                MethodType.methodType(void.class, int.class));
        ManagedLibrary lib = libraryWith(Map.of("consume", h));

        VoidConsumer consumer = factory.create(VoidConsumer.class, lib);
        assertDoesNotThrow(() -> consumer.consume(42));

        assertTrue(lib.acquire(), "library must be acquirable after void call");
        lib.release();
    }


    @Test
    void nativeExceptionDoesNotLeakLibraryAcquire() throws Exception {
        MethodHandle h = handle("nativeThrows",
                MethodType.methodType(int.class, int.class));
        ManagedLibrary lib = libraryWith(Map.of("alwaysFails", h));

        FailingClient client = factory.create(FailingClient.class, lib);
        assertThrows(IllegalStateException.class, () -> client.alwaysFails(1));

        assertTrue(lib.acquire(), "library must be released even after native failure");
        lib.release();
    }


    @Test
    void supplierBasedProxyUsesCurrentLibraryOnEachCall() throws Exception {
        MethodHandle h = handle("nativeCompute",
                MethodType.methodType(int.class, int.class));
        ManagedLibrary lib1 = libraryWith(Map.of("compute", h));
        ManagedLibrary lib2 = libraryWith(Map.of("compute", h));

        AtomicInteger invocation = new AtomicInteger(0);
        CALL_COUNTER.set(0);

        MultiCallClient client = factory.create(MultiCallClient.class, () ->
                invocation.getAndIncrement() < 2 ? lib1 : lib2);

        assertEquals(6, client.compute(3));
        assertEquals(8, client.compute(4));
        assertEquals(10, client.compute(5));

        assertEquals(3, CALL_COUNTER.get(), "native function must be called 3 times");
    }


    @Test
    void proxyImplementsTargetInterface() throws Exception {
        MethodHandle h = handle("nativeAdd",
                MethodType.methodType(int.class, int.class, int.class));
        Calculator calc = factory.create(Calculator.class, libraryWith(Map.of("add", h)));

        assertInstanceOf(Calculator.class, calc);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(calc.getClass()));
    }


    @Test
    void missingHandleThrowsWithMethodName() throws Exception {
        MethodHandle h = handle("nativeAdd",
                MethodType.methodType(int.class, int.class, int.class));
        Calculator calc = factory.create(Calculator.class, libraryWith(Map.of("add", h)));

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class, () -> calc.multiply(2, 3));
        assertTrue(ex.getMessage().contains("multiply"));
    }


    @Test
    void retiredLibraryThrowsWithReloadMessage() throws Exception {
        MethodHandle h = handle("nativeAdd",
                MethodType.methodType(int.class, int.class, int.class));
        ManagedLibrary lib = libraryWith(Map.of("add", h));
        lib.retire();

        Calculator calc = factory.create(Calculator.class, lib);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> calc.add(1, 2));
        assertTrue(ex.getMessage().toLowerCase().contains("reload"),
                "message must mention 'reload'");
    }


    @MiteStruct
    public static class Point2D {
        public float x;
        public float y;
    }

    public interface GeometryClient {
        Point2D reflect(Point2D p);
    }

    static MemorySegment nativeReflect(MemorySegment seg) {
        return seg;
    }

    @Test
    void miteStructArgumentIsReturnedAsSameJavaObjectViaAliasCache() throws Exception {
        MethodHandle h = handle("nativeReflect",
                MethodType.methodType(MemorySegment.class, MemorySegment.class));
        GeometryClient client = factory.create(GeometryClient.class,
                libraryWith(Map.of("reflect", h)));

        Point2D p = new Point2D();
        p.x = 3f;
        p.y = 4f;

        Point2D result = client.reflect(p);
        assertSame(p, result,
                "nativeToJava alias cache must return the original Java object for the same pointer");
    }


    @Test
    void repeatedCallsOnSameProxyDoNotLeakOrFail() throws Exception {
        MethodHandle h = handle("nativeAdd",
                MethodType.methodType(int.class, int.class, int.class));
        Calculator calc = factory.create(Calculator.class,
                libraryWith(Map.of("add", h, "multiply", handle("nativeMultiply",
                        MethodType.methodType(int.class, int.class, int.class)))));

        for (int i = 0; i < 100; i++) {
            assertEquals(i + 1, calc.add(i, 1));
            assertEquals(i * i, calc.multiply(i, i));
        }
    }


    @Test
    void miteArrayIsPassedAsSegmentDirectly() throws Exception {

        MethodHandle h = handle("nativeNegate",
                MethodType.methodType(void.class, MemorySegment.class));
        ArrayTransformer t = factory.create(ArrayTransformer.class, libraryWith(Map.of("negate", h)));

        int[] arr = {5, 10, 15};
        t.negate(arr);
        assertArrayEquals(new int[]{-5, -10, -15}, arr);
    }
}
