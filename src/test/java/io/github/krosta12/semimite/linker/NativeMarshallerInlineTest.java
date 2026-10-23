package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.MiteInline;
import io.github.krosta12.semimite.annotation.MiteStruct;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Layout checks for {@code @MiteInline} fields; sizes come from g++ on x86-64. */
class NativeMarshallerInlineTest {

    private final NativeMarshaller marshaller = new NativeMarshaller();

    @MiteStruct
    static class Vec3 {
        public float x, y, z;
    }

    @MiteStruct
    static class Particle {
        public int id;
        public float mass;
        @MiteInline
        public Vec3 pos;
    }

    @MiteStruct
    static class PointerParticle {
        public int id;
        public float mass;
        public Vec3 pos;
    }

    private List<Runnable> tasks() {
        return new ArrayList<>();
    }

    private Map<Object, MemorySegment> seen() {
        return new IdentityHashMap<>();
    }

    private Map<Long, Object> n2j() {
        return new HashMap<>();
    }

    private Particle particle() {
        Particle p = new Particle();
        p.id = 7;
        p.mass = 1.5f;
        p.pos = new Vec3();
        p.pos.x = 1f;
        p.pos.y = 2f;
        p.pos.z = 3f;
        return p;
    }

    @Test
    void inlineFieldIsStoredByValueAtCppOffsets() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = marshaller.marshalCustomObject(particle(), arena, tasks(), seen(), n2j());

            assertEquals(20L, seg.byteSize(), "sizeof(Particle) in C++ is 20");
            assertEquals(7, seg.get(ValueLayout.JAVA_INT, 0), "id at offset 0");
            assertEquals(1.5f, seg.get(ValueLayout.JAVA_FLOAT, 4), "mass at offset 4");
            assertEquals(1f, seg.get(ValueLayout.JAVA_FLOAT, 8), "pos.x at offset 8");
            assertEquals(2f, seg.get(ValueLayout.JAVA_FLOAT, 12), "pos.y at offset 12");
            assertEquals(3f, seg.get(ValueLayout.JAVA_FLOAT, 16), "pos.z at offset 16");
        }
    }

    @Test
    void inlineCopyBackPropagatesNestedChanges() {
        Particle p = particle();

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = marshaller.marshalCustomObject(p, arena, tasks, seen(), n2j());

            seg.set(ValueLayout.JAVA_FLOAT, 12, 42f);
            tasks.forEach(Runnable::run);

            assertEquals(42f, p.pos.y, 1e-6f, "copy-back must reach the inline nested object");
        }
    }

    @Test
    void fieldWithoutInlineAnnotationStaysAPointer() {
        PointerParticle p = new PointerParticle();
        p.id = 1;
        p.mass = 2f;
        p.pos = new Vec3();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = marshaller.marshalCustomObject(p, arena, tasks(), seen(), n2j());

            assertEquals(16L, seg.byteSize(), "pointer field keeps the struct at 16 bytes");
            assertNotEquals(0L, seg.get(ValueLayout.ADDRESS, 8).address(),
                    "the nested object must be behind a pointer");
        }
    }

    @Test
    void unmarshalReconstructsInlineNestedObject() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(20, 4);
            seg.set(ValueLayout.JAVA_INT, 0, 9);
            seg.set(ValueLayout.JAVA_FLOAT, 4, 2.5f);
            seg.set(ValueLayout.JAVA_FLOAT, 8, 4f);
            seg.set(ValueLayout.JAVA_FLOAT, 12, 5f);
            seg.set(ValueLayout.JAVA_FLOAT, 16, 6f);

            Particle p = (Particle) marshaller.unmarshalCustomObject(seg, Particle.class, n2j());

            assertEquals(9, p.id);
            assertEquals(2.5f, p.mass, 1e-6f);
            assertNotNull(p.pos, "inline nested object must be created, not left null");
            assertEquals(4f, p.pos.x, 1e-6f);
            assertEquals(5f, p.pos.y, 1e-6f);
            assertEquals(6f, p.pos.z, 1e-6f);
        }
    }

    @MiteStruct
    static class Line {
        @MiteInline
        public Vec3 a;
        @MiteInline
        public Vec3 b;
    }

    @Test
    void nestedInlineIsResolvedRecursively() {
        Line line = new Line();
        line.a = new Vec3();
        line.b = new Vec3();
        line.b.z = 5f;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = marshaller.marshalCustomObject(line, arena, tasks(), seen(), n2j());

            assertEquals(24L, seg.byteSize(), "struct Line { Vec3 a; Vec3 b; } is 24 bytes");
            assertEquals(5f, seg.get(ValueLayout.JAVA_FLOAT, 20), "b.z at offset 20");
        }
    }

    @Test
    void nullInlineFieldIsZeroedInsteadOfDereferenced() {
        Particle p = new Particle();
        p.id = 3;
        p.mass = 4f;
        p.pos = null;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = marshaller.marshalCustomObject(p, arena, tasks(), seen(), n2j());

            assertEquals(20L, seg.byteSize());
            assertEquals(0f, seg.get(ValueLayout.JAVA_FLOAT, 8), "null inline field must be zeroed");
            assertEquals(0f, seg.get(ValueLayout.JAVA_FLOAT, 16));
        }
    }

    // ------------------------------------------------------------------
    // inline arrays
    // ------------------------------------------------------------------

    @MiteStruct
    static class Samples {
        public int id;
        @MiteInline(length = 4)
        public double[] values;
    }

    @MiteStruct
    static class Mesh {
        @MiteInline(length = 3)
        public Vec3[] points;
    }

    @Test
    void inlinePrimitiveArrayIsPartOfTheStruct() {
        Samples s = new Samples();
        s.id = 3;
        s.values = new double[]{1, 2, 3, 4};

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = marshaller.marshalCustomObject(s, arena, tasks(), seen(), n2j());

            assertEquals(40L, seg.byteSize(), "struct Samples { int id; double values[4]; } is 40 bytes");
            assertEquals(3, seg.get(ValueLayout.JAVA_INT, 0), "id at offset 0");
            assertEquals(1.0, seg.get(ValueLayout.JAVA_DOUBLE, 8), 1e-9, "values[0] at offset 8");
            assertEquals(4.0, seg.get(ValueLayout.JAVA_DOUBLE, 32), 1e-9, "values[3] at offset 32");
        }
    }

    @Test
    void inlinePrimitiveArrayCopyBackWorks() {
        Samples s = new Samples();
        s.id = 1;
        s.values = new double[]{1, 2, 3, 4};

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = marshaller.marshalCustomObject(s, arena, tasks, seen(), n2j());

            seg.set(ValueLayout.JAVA_DOUBLE, 16, 99.5);   // values[1], as C++ would
            tasks.forEach(Runnable::run);

            assertEquals(99.5, s.values[1], 1e-9);
        }
    }

    @Test
    void inlineStructArrayIsContiguousWithStructStride() {
        Mesh m = new Mesh();
        m.points = new Vec3[3];
        m.points[0] = new Vec3();
        m.points[2] = new Vec3();
        m.points[2].z = 9f;

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = marshaller.marshalCustomObject(m, arena, tasks, seen(), n2j());

            assertEquals(36L, seg.byteSize(), "struct Mesh { Vec3 points[3]; } is 36 bytes");
            assertEquals(9f, seg.get(ValueLayout.JAVA_FLOAT, 32), 1e-6f, "points[2].z at 2*12+8");

            seg.set(ValueLayout.JAVA_FLOAT, 16, 5f);       // points[1].y
            tasks.forEach(Runnable::run);

            assertNotNull(m.points[1], "copy-back must create a missing element object");
            assertEquals(5f, m.points[1].y, 1e-6f);
        }
    }

    @Test
    void unmarshalCreatesInlineArrays() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(40, 8);
            seg.set(ValueLayout.JAVA_INT, 0, 4);
            for (int i = 0; i < 4; i++) {
                seg.set(ValueLayout.JAVA_DOUBLE, 8 + i * 8L, i + 0.5);
            }

            Samples s = (Samples) marshaller.unmarshalCustomObject(seg, Samples.class, n2j());

            assertNotNull(s.values, "inline array must be created on unmarshal");
            assertEquals(4, s.values.length);
            assertEquals(0.5, s.values[0], 1e-9);
            assertEquals(3.5, s.values[3], 1e-9);
        }
    }

    @Test
    void inlineArrayLengthMismatchIsRejected() {
        Samples s = new Samples();
        s.values = new double[]{1, 2, 3};

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                marshaller.marshalCustomObject(s, Arena.ofConfined(), tasks(), seen(), n2j()));

        assertTrue(ex.getMessage().contains("length mismatch"), ex.getMessage());
        assertTrue(ex.getMessage().contains("declared 4"), ex.getMessage());
    }

    @MiteStruct
    static class MissingLength {
        @MiteInline
        public double[] values;
    }

    @Test
    void inlineArrayWithoutDeclaredLengthIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                marshaller.marshalCustomObject(new MissingLength(), Arena.ofConfined(), tasks(), seen(), n2j()));

        assertTrue(ex.getMessage().contains("length"), ex.getMessage());
    }

    @MiteStruct
    static class WithChar {
        public char c;
    }

    @Test
    void charFieldIsRejectedWithActionableMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                marshaller.marshalCustomObject(new WithChar(), Arena.ofConfined(), tasks(), seen(), n2j()));

        assertTrue(ex.getMessage().contains("char"), "message must name the offending type: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("WithChar.c"), "message must name the offending field: " + ex.getMessage());
    }

    @MiteStruct
    static class BadInline {
        @MiteInline
        public String name;
    }

    @Test
    void inlineOnNonMiteStructFieldIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                marshaller.marshalCustomObject(new BadInline(), Arena.ofConfined(), tasks(), seen(), n2j()));

        assertTrue(ex.getMessage().contains("@MiteInline"), ex.getMessage());
        assertTrue(ex.getMessage().contains("MiteStruct"), ex.getMessage());
    }

    @MiteStruct
    static class SelfEmbedded {
        @MiteInline
        public SelfEmbedded self;
    }

    @Test
    void cyclicInlineContainmentIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                marshaller.marshalCustomObject(new SelfEmbedded(), Arena.ofConfined(), tasks(), seen(), n2j()));

        assertTrue(ex.getMessage().contains("Cyclic"), ex.getMessage());
    }

    @MiteStruct
    static class WithUnsupportedField {
        public Object payload;
    }

    @Test
    void unknownFieldTypeIsRejectedAtLayoutTime() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                marshaller.marshalCustomObject(new WithUnsupportedField(), Arena.ofConfined(), tasks(), seen(), n2j()));

        assertTrue(ex.getMessage().contains("Unsupported"), ex.getMessage());
        assertTrue(ex.getMessage().contains("WithUnsupportedField.payload"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // regression checks raised in review
    // ------------------------------------------------------------------

    @MiteStruct
    static class Node {
        public int value;
        public Node[] children;
    }

    @Test
    void structArrayFieldStaysAPointerArray() {
        Node root = new Node();
        root.value = 1;
        root.children = new Node[]{new Node(), new Node()};
        root.children[0].value = 2;
        root.children[1].value = 3;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = marshaller.marshalCustomObject(root, arena, tasks(), seen(), n2j());

            assertEquals(16L, seg.byteSize(), "int + pointer");
            MemorySegment ptrs = seg.get(ValueLayout.ADDRESS, 8).reinterpret(16);
            MemorySegment first = ptrs.get(ValueLayout.ADDRESS, 0).reinterpret(8);
            assertEquals(2, first.get(ValueLayout.JAVA_INT, 0), "children[0].value behind a pointer");
        }
    }

    @MiteStruct
    static class Flags {
        @MiteInline(length = 4)
        public boolean[] bits;
    }

    @Test
    void inlineBooleanArrayWorks() {
        Flags f = new Flags();
        f.bits = new boolean[]{true, false, true, true};

        try (Arena arena = Arena.ofConfined()) {
            List<Runnable> tasks = tasks();
            MemorySegment seg = marshaller.marshalCustomObject(f, arena, tasks, seen(), n2j());

            assertEquals(4L, seg.byteSize());
            assertEquals(1, seg.get(ValueLayout.JAVA_BYTE, 0));
            assertEquals(0, seg.get(ValueLayout.JAVA_BYTE, 1));
            assertEquals(1, seg.get(ValueLayout.JAVA_BYTE, 3));

            seg.set(ValueLayout.JAVA_BYTE, 1, (byte) 1);
            tasks.forEach(Runnable::run);
            assertTrue(f.bits[1], "copy-back must reach the inline boolean array");
        }
    }
}
