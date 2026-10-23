package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.MiteStruct;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** String copy-back and char[] rejection. */
class NativeMarshallerStringCopyBackTest {

    private final NativeMarshaller marshaller = new NativeMarshaller();

    @MiteStruct
    static class Person {
        public int id;
        public String name;
    }

    @Test
    void stringFieldChangesAreCopiedBack() {
        Person p = new Person();
        p.id = 1;
        p.name = "abc";

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = new ArrayList<>();
            MemorySegment seg = marshaller.marshalCustomObject(
                    p, arena, tasks, new IdentityHashMap<>(), new HashMap<>());

            assertEquals(16L, seg.byteSize(), "int + pointer");
            MemorySegment buffer = seg.get(ValueLayout.ADDRESS, 8).reinterpret(4);
            buffer.set(ValueLayout.JAVA_BYTE, 0, (byte) 'x');
            buffer.set(ValueLayout.JAVA_BYTE, 1, (byte) 'y');
            buffer.set(ValueLayout.JAVA_BYTE, 2, (byte) 'z');

            tasks.forEach(Runnable::run);

            assertEquals("xyz", p.name, "in-place native string changes must reach the Java field");
        }
    }

    @Test
    void unchangedStringKeepsSameInstance() {
        Person p = new Person();
        p.id = 2;
        p.name = "same";
        String original = p.name;

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = new ArrayList<>();
            marshaller.marshalCustomObject(p, arena, tasks, new IdentityHashMap<>(), new HashMap<>());
            tasks.forEach(Runnable::run);

            assertSame(original, p.name, "an untouched string must not be replaced");
        }
    }

    @Test
    void longStringIsNotTruncatedOnCopyBack() {
        Person p = new Person();
        p.id = 3;
        p.name = "x".repeat(70_000);

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = new ArrayList<>();
            marshaller.marshalCustomObject(p, arena, tasks, new IdentityHashMap<>(), new HashMap<>());
            tasks.forEach(Runnable::run);

            assertEquals(70_000, p.name.length(),
                    "a string longer than MAX_STRING_BYTES must survive copy-back unchanged");
        }
    }

    @Test
    void replacedStringPointerIsFollowed() {
        Person p = new Person();
        p.id = 4;
        p.name = "first";

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = new ArrayList<>();
            MemorySegment seg = marshaller.marshalCustomObject(
                    p, arena, tasks, new IdentityHashMap<>(), new HashMap<>());

            seg.set(ValueLayout.ADDRESS, 8, arena.allocateFrom("second"));
            tasks.forEach(Runnable::run);

            assertEquals("second", p.name, "a pointer replaced by native code must be followed");
        }
    }

    @Test
    void stringFieldIsFilledWhenUnmarshalling() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment struct = arena.allocate(16, 8);
            struct.set(ValueLayout.JAVA_INT, 0, 7);
            struct.set(ValueLayout.ADDRESS, 8, arena.allocateFrom("from native"));

            Person p = (Person) marshaller.unmarshalCustomObject(struct, Person.class, new HashMap<>());

            assertEquals(7, p.id);
            assertEquals("from native", p.name, "a returned struct must carry its string field");
        }
    }

    @Test
    void nullStringFieldIsFilledWhenNativeWritesIt() {
        Person p = new Person();
        p.id = 5;
        p.name = null;

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = new ArrayList<>();
            MemorySegment seg = marshaller.marshalCustomObject(
                    p, arena, tasks, new IdentityHashMap<>(), new HashMap<>());

            assertEquals(0L, seg.get(ValueLayout.ADDRESS, 8).address(),
                    "a null field must start as a null pointer");

            seg.set(ValueLayout.ADDRESS, 8, arena.allocateFrom("filled by native"));
            tasks.forEach(Runnable::run);

            assertEquals("filled by native", p.name,
                    "an out-parameter written by native code must reach the Java field");
        }
    }

    @MiteStruct
    static class Node {
        public int value;
        public Node next;
    }

    @Test
    void childObjectCreatedByNativeIsUnmarshalled() {
        Node head = new Node();
        head.value = 1;
        head.next = null;

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = new ArrayList<>();
            MemorySegment seg = marshaller.marshalCustomObject(
                    head, arena, tasks, new IdentityHashMap<>(), new HashMap<>());

            MemorySegment child = arena.allocate(16, 8);
            child.set(ValueLayout.JAVA_INT, 0, 42);
            child.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
            seg.set(ValueLayout.ADDRESS, 8, child);

            tasks.forEach(Runnable::run);

            assertNotNull(head.next, "a child allocated by native code must materialise in Java");
            assertEquals(42, head.next.value);
            assertNull(head.next.next);
        }
    }

    @Test
    void longNativeStringIsReadCompletely() {
        String big = "y".repeat(200_000);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocateFrom(big);

            assertEquals(big, marshaller.readNullTerminatedString(seg),
                    "a string well above the old 64 KiB cap must be read in full");
        }
    }

    @Test
    void unterminatedBoundedBufferIsRejected() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(8);
            buffer.fill((byte) 'a');

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> marshaller.readNullTerminatedString(buffer));

            assertTrue(ex.getMessage().contains("terminator"), ex.getMessage());
        }
    }

    @Test
    void charArrayArgumentIsRejected() {
        char[] chars = {'a', 'b', 'c'};

        try (Arena arena = Arena.ofConfined()) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> marshaller.marshal(chars, "char*", arena,
                            new ArrayList<>(), new IdentityHashMap<>(), new HashMap<>()));

            assertTrue(ex.getMessage().contains("char[]"), ex.getMessage());
            assertTrue(ex.getMessage().contains("byte[]"), ex.getMessage());
        }
    }

    @Test
    void shortArrayStillWorksForUtf16() {
        short[] codes = {'a', 'b'};

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = (MemorySegment) marshaller.marshal(codes, "short*", arena,
                    new ArrayList<>(), new IdentityHashMap<>(), new HashMap<>());

            assertEquals((short) 'a', seg.getAtIndex(ValueLayout.JAVA_SHORT, 0));
            assertEquals((short) 'b', seg.getAtIndex(ValueLayout.JAVA_SHORT, 1));
        }
    }
}
