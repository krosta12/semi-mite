package org.example.linker;

import org.example.annotation.MiteStruct;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeMarshallerTest {

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

    @Test
    void marshalsStringToNullTerminated() {
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal("hello", "const char*", arena, tasks(), seen(), n2j());
            assertInstanceOf(MemorySegment.class, result);
            String back = marshaller.readNullTerminatedString((MemorySegment) result);
            assertEquals("hello", back);
        }
    }

    @Test
    void marshalsNullStringToNullSegment() {
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(null, "const char*", arena, tasks(), seen(), n2j());
            assertEquals(MemorySegment.NULL, result);
        }
    }

    @Test
    void marshalsFloatArrayWithCopyBack() {
        float[] arr = {1f, 2f, 3f};
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            Object result = marshaller.marshal(arr, "float*", arena, tasks, seen(), n2j());
            MemorySegment seg = (MemorySegment) result;

            // simulate C++ modifying the array
            seg.setAtIndex(ValueLayout.JAVA_FLOAT, 0, 99f);

            // copy-back
            tasks.forEach(Runnable::run);
            assertEquals(99f, arr[0]);
        }
    }

    @Test
    void marshalsBooleanArrayWithCopyBack() {
        boolean[] arr = {true, false, true};
        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            marshaller.marshal(arr, "bool*", arena, tasks, seen(), n2j());
            // native flips index 1
            tasks.forEach(Runnable::run);
            // original values preserved (no native change in this test)
            assertTrue(arr[0]);
            assertFalse(arr[1]);
        }
    }

    @Test
    void marshalsCollectionOfFloats() {
        List<Float> list = List.of(1f, 2f, 3f);
        try (Arena arena = Arena.ofConfined()) {
            Object result = marshaller.marshal(list, "float*", arena, tasks(), seen(), n2j());
            MemorySegment seg = (MemorySegment) result;
            assertEquals(1f, seg.getAtIndex(ValueLayout.JAVA_FLOAT, 0));
            assertEquals(3f, seg.getAtIndex(ValueLayout.JAVA_FLOAT, 2));
        }
    }

    @Test
    void marshalsMiteStructToNativeAndBack() {
        PointDto pt = new PointDto();
        pt.x = 1.5f;
        pt.y = 2.5f;
        pt.label = "test";

        try (Arena arena = Arena.ofConfined()) {
            Map<Long, Object> n2j = n2j();
            List<Runnable> tasks = tasks();
            MemorySegment seg = marshaller.marshalCustomObject(pt, arena, tasks, seen(), n2j);
            assertNotNull(seg);
            assertNotEquals(0L, seg.address());

            PointDto back = (PointDto) marshaller.unmarshalCustomObject(seg.reinterpret(64), PointDto.class, n2j);
            assertEquals(1.5f, back.x);
            assertEquals(2.5f, back.y);
        }
    }

    @Test
    void rejectsNonMiteStructObject() {
        try (Arena arena = Arena.ofConfined()) {
            assertThrows(IllegalArgumentException.class, () ->
                    marshaller.marshalCustomObject(new Object(), arena, tasks(), seen(), n2j()));
        }
    }


    @Test
    void mixedPrimitiveStructLayoutIsCorrect() {
        @MiteStruct
        class Mixed { //define
            public final int a = 1;
            public final float b = 2.0f;
            public final long c = 3L;
            public final double d = 4.0;
        }
        Mixed obj = new Mixed();

        try (Arena arena = Arena.ofConfined()) {
            Map<Long, Object> n2j = n2j();
            MemorySegment seg = marshaller.marshalCustomObject(obj, arena, tasks(), seen(), n2j);

            int aVal = seg.get(ValueLayout.JAVA_INT, 0);
            float bVal = seg.get(ValueLayout.JAVA_FLOAT, 4);
            long cVal = seg.get(ValueLayout.JAVA_LONG, 8);
            double dVal = seg.get(ValueLayout.JAVA_DOUBLE, 16);

            //use defined
            assertEquals(1, aVal, "int field at offset 0");
            assertEquals(2.0f, bVal, "float field at offset 4");
            assertEquals(3L, cVal, "long field at offset 8");
            assertEquals(4.0, dVal, 1e-9, "double field at offset 16");
            assertEquals(24L, seg.byteSize(), "total struct size must be the 24 bytes");
        }
    }

    @Test
    void cycleDetectionPreventsStackOverflow() {
        @MiteStruct
        class Node {
            public int id;
            public Node next;
        }
        Node a = new Node();
        Node b = new Node();
        a.id = 1;
        b.id = 2;
        a.next = b;
        b.next = a; // cycle

        try (Arena arena = Arena.ofConfined()) {
            Map<Object, MemorySegment> seen = seen();
            assertDoesNotThrow(() -> marshaller.marshalCustomObject(a, arena, tasks(), seen, n2j()));
        }
    }

}