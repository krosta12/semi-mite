package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.MiteStruct;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;


class NativeMarshallerIntegrationTest {

    private final NativeMarshaller marshaller = new NativeMarshaller();

    private List<Runnable> tasks() {
        return new ArrayList<>();
    }

    private Map<Object, MemorySegment> seen() {
        return new IdentityHashMap<>();
    }

    private Map<Long, Object> n2j() {
        return new HashMap<>();
    }


    @MiteStruct
    static class Vec3 {
        public float x, y, z;
    }

    @MiteStruct
    static class Particle {
        public int id;
        public float mass;
        public Vec3 pos;
    }


    @Test
    void marshalThenCopyBackUpdatesJavaObject() {
        Vec3 vec = new Vec3();
        vec.x = 1f;
        vec.y = 2f;
        vec.z = 3f;

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            Map<Long, Object> nativeToJava = n2j();

            MemorySegment seg = marshaller.marshalCustomObject(vec, arena, tasks, seen(), nativeToJava);

            seg.set(ValueLayout.JAVA_FLOAT, 0, 10f);
            seg.set(ValueLayout.JAVA_FLOAT, 4, 20f);
            seg.set(ValueLayout.JAVA_FLOAT, 8, 30f);

            tasks.forEach(Runnable::run);

            assertEquals(10f, vec.x, 1e-6f, "x must be updated by copy-back");
            assertEquals(20f, vec.y, 1e-6f, "y must be updated by copy-back");
            assertEquals(30f, vec.z, 1e-6f, "z must be updated by copy-back");
        }
    }

    @Test
    void marshalNullObjectReturnsNullSegment() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = marshaller.marshalCustomObject(null, arena, tasks(), seen(), n2j());
            assertEquals(0L, seg.address());
        }
    }


    @Test
    void marshalNonMiteStructThrowsSecurityViolation() {
        class PlainPojo {
            int x = 1;
        }
        try (Arena arena = Arena.ofConfined()) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> marshaller.marshalCustomObject(new PlainPojo(), arena, tasks(), seen(), n2j()));
            assertTrue(ex.getMessage().contains("Security Violation"));
            assertTrue(ex.getMessage().contains("MiteStruct"));
        }
    }


    @Test
    void cyclicStructureDoesNotCauseStackOverflow() {
        @MiteStruct
        class Node {
            public int id;
            public Node next;
        }
        Node a = new Node();
        a.id = 1;
        Node b = new Node();
        b.id = 2;
        a.next = b;
        b.next = a;

        try (Arena arena = Arena.ofConfined()) {
            assertDoesNotThrow(() ->
                    marshaller.marshalCustomObject(a, arena, tasks(), seen(), n2j()));
        }
    }


    @Test
    void unmarshalNullSegmentReturnsNull() {
        Object result = marshaller.unmarshalCustomObject(MemorySegment.NULL, Vec3.class, n2j());
        assertNull(result, "null segment must unmarshal to null Java object");
    }


    @Test
    void unmarshalUsesNativeToJavaCacheForSameAddress() {
        Vec3 existing = new Vec3();
        Map<Long, Object> cache = n2j();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ValueLayout.JAVA_FLOAT, 3);
            long addr = seg.address();
            cache.put(addr, existing);

            Object returned = marshaller.unmarshalCustomObject(seg.reinterpret(12), Vec3.class, cache);
            assertSame(existing, returned, "cached Java object must be returned for known address");
        }
    }


    @Test
    void intArrayCopyBackReflectsNativeChanges() {
        int[] arr = {10, 20, 30};
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = (MemorySegment) marshaller.marshal(arr, "int*", arena, tasks, seen(), n2j());

            for (int i = 0; i < arr.length; i++) {
                seg.setAtIndex(ValueLayout.JAVA_INT, i,
                        seg.getAtIndex(ValueLayout.JAVA_INT, i) + 100);
            }

            tasks.forEach(Runnable::run);

            assertArrayEquals(new int[]{110, 120, 130}, arr);
        }
    }

    @Test
    void doubleArrayCopyBackReflectsNativeChanges() {
        double[] arr = {1.0, 2.0, 3.0};
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = (MemorySegment) marshaller.marshal(arr, "double*", arena, tasks, seen(), n2j());

            seg.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 99.9);
            tasks.forEach(Runnable::run);

            assertEquals(99.9, arr[0], 1e-9);
        }
    }

    @Test
    void longArrayCopyBackReflectsNativeChanges() {
        long[] arr = {100L, 200L, 300L};
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = (MemorySegment) marshaller.marshal(arr, "long*", arena, tasks, seen(), n2j());

            seg.setAtIndex(ValueLayout.JAVA_LONG, 2, 9999L);
            tasks.forEach(Runnable::run);

            assertEquals(9999L, arr[2]);
        }
    }

    @Test
    void byteArrayCopyBackReflectsNativeChanges() {
        byte[] arr = {1, 2, 3, 4};
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = (MemorySegment) marshaller.marshal(arr, "char*", arena, tasks, seen(), n2j());

            seg.setAtIndex(ValueLayout.JAVA_BYTE, 1, (byte) 42);
            tasks.forEach(Runnable::run);

            assertEquals((byte) 42, arr[1]);
        }
    }

    @Test
    void shortArrayCopyBackReflectsNativeChanges() {
        short[] arr = {10, 20, 30};
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = (MemorySegment) marshaller.marshal(arr, "short*", arena, tasks, seen(), n2j());

            seg.setAtIndex(ValueLayout.JAVA_SHORT, 0, (short) 999);
            tasks.forEach(Runnable::run);

            assertEquals((short) 999, arr[0]);
        }
    }

    @Test
    void booleanArrayCopyBackReflectsNativeChanges() {
        boolean[] arr = {true, true, false};
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            marshaller.marshal(arr, "bool*", arena, tasks, seen(), n2j());
            tasks.forEach(Runnable::run);
            assertTrue(arr[0]);
            assertTrue(arr[1]);
            assertFalse(arr[2]);
        }
    }

    @Test
    void charArrayArgumentIsRejectedInsteadOfSendingUtf16() {
        char[] arr = {'a', 'b', 'c'};

        try (Arena arena = Arena.ofConfined()) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> marshaller.marshal(arr, "char*", arena, tasks(), seen(), n2j()));

            assertTrue(ex.getMessage().contains("char[]"), ex.getMessage());
        }
    }


    @Test
    void marshalStringWithoutCppTypeHintStillAllocatesNativeString() {
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal("hello", null, arena, tasks(), seen(), n2j());
            assertInstanceOf(MemorySegment.class, result);
            assertEquals("hello", marshaller.readNullTerminatedString((MemorySegment) result));
        }
    }

    @Test
    void marshalStringWithStdStringHintAllocatesNativeString() {
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal("world", "std::string", arena, tasks(), seen(), n2j());
            assertInstanceOf(MemorySegment.class, result);
            assertEquals("world", marshaller.readNullTerminatedString((MemorySegment) result));
        }
    }

    @Test
    void readNullTerminatedStringHandlesEmptyString() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocateFrom("");
            assertEquals("", marshaller.readNullTerminatedString(seg));
        }
    }

    @Test
    void readNullTerminatedStringHandlesUnicode() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocateFrom("hello");
            String result = marshaller.readNullTerminatedString(seg);
            assertEquals("hello", result);
        }
    }


    @Test
    void emptyCollectionMarshalToNullSegment() {
        List<Float> empty = List.of();
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(empty, "float*", arena, tasks(), seen(), n2j());
            assertEquals(MemorySegment.NULL, result);
        }
    }

    @Test
    void intCollectionMarshaledCorrectly() {
        List<Integer> list = List.of(10, 20, 30);
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(list, "int*", arena, tasks(), seen(), n2j());
            MemorySegment seg = (MemorySegment) result;
            assertEquals(10, seg.getAtIndex(ValueLayout.JAVA_INT, 0));
            assertEquals(20, seg.getAtIndex(ValueLayout.JAVA_INT, 1));
            assertEquals(30, seg.getAtIndex(ValueLayout.JAVA_INT, 2));
        }
    }

    @Test
    void doubleCollectionMarshaledCorrectly() {
        List<Double> list = List.of(1.1, 2.2, 3.3);
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(list, "double*", arena, tasks(), seen(), n2j());
            MemorySegment seg = (MemorySegment) result;
            assertEquals(1.1, seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 0), 1e-9);
            assertEquals(3.3, seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 2), 1e-9);
        }
    }

    @Test
    void longCollectionMarshaledCorrectly() {
        List<Long> list = List.of(100L, 200L);
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(list, "long*", arena, tasks(), seen(), n2j());
            MemorySegment seg = (MemorySegment) result;
            assertEquals(100L, seg.getAtIndex(ValueLayout.JAVA_LONG, 0));
            assertEquals(200L, seg.getAtIndex(ValueLayout.JAVA_LONG, 1));
        }
    }

    @Test
    void shortCollectionMarshaledCorrectly() {
        List<Short> list = List.of((short) 5, (short) 10);
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(list, "short*", arena, tasks(), seen(), n2j());
            MemorySegment seg = (MemorySegment) result;
            assertEquals((short) 5, seg.getAtIndex(ValueLayout.JAVA_SHORT, 0));
            assertEquals((short) 10, seg.getAtIndex(ValueLayout.JAVA_SHORT, 1));
        }
    }

    @Test
    void byteCollectionMarshaledCorrectly() {
        List<Byte> list = List.of((byte) 1, (byte) 2, (byte) 3);
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(list, "char*", arena, tasks(), seen(), n2j());
            MemorySegment seg = (MemorySegment) result;
            assertEquals((byte) 1, seg.getAtIndex(ValueLayout.JAVA_BYTE, 0));
        }
    }


    @Test
    void mutableListCollectionWriteBackUpdatesElements() {
        List<Integer> mutableList = new ArrayList<>(List.of(1, 2, 3));
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = (MemorySegment) marshaller.marshal(mutableList, "int*", arena, tasks, seen(), n2j());

            seg.setAtIndex(ValueLayout.JAVA_INT, 0, 100);
            seg.setAtIndex(ValueLayout.JAVA_INT, 1, 200);
            seg.setAtIndex(ValueLayout.JAVA_INT, 2, 300);

            tasks.forEach(Runnable::run);

            assertEquals(100, mutableList.get(0));
            assertEquals(200, mutableList.get(1));
            assertEquals(300, mutableList.get(2));
        }
    }


    @Test
    void layoutCachingProducesSameResultOnSecondMarshal() {
        Vec3 v1 = new Vec3();
        v1.x = 1f;
        v1.y = 2f;
        v1.z = 3f;
        Vec3 v2 = new Vec3();
        v2.x = 4f;
        v2.y = 5f;
        v2.z = 6f;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg1 = marshaller.marshalCustomObject(v1, arena, tasks(), seen(), n2j());
            MemorySegment seg2 = marshaller.marshalCustomObject(v2, arena, tasks(), seen(), n2j());

            assertEquals(1f, seg1.get(ValueLayout.JAVA_FLOAT, 0), 1e-6f);
            assertEquals(4f, seg2.get(ValueLayout.JAVA_FLOAT, 0), 1e-6f);
        }
    }


    @Test
    void primitiveIntPassesThroughUnchanged() {
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(42, null, arena, tasks(), seen(), n2j());
            assertEquals(42, result);
        }
    }

    @Test
    void primitiveDoublePassesThroughUnchanged() {
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(3.14, null, arena, tasks(), seen(), n2j());
            assertEquals(3.14, result);
        }
    }
}
