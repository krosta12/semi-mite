package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.MiteStruct;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Deep graph traversal: long chains, cycles and the node limit. */
class NativeMarshallerDeepGraphTest {

    private final NativeMarshaller marshaller = new NativeMarshaller();

    @MiteStruct
    static class Node {
        public int value;
        public Node next;
    }

    private MemorySegment chain(Arena arena, int length) {
        MemorySegment next = MemorySegment.NULL;
        for (int i = length - 1; i >= 0; i--) {
            MemorySegment node = arena.allocate(16, 8);
            node.set(ValueLayout.JAVA_INT, 0, i);
            node.set(ValueLayout.ADDRESS, 8, next);
            next = node;
        }
        return next;
    }

    @Test
    void twentyThousandNodeChainIsUnmarshalled() {
        int length = 20_000;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment head = chain(arena, length);

            Node node = (Node) marshaller.unmarshalCustomObject(head, Node.class, new HashMap<>());

            int count = 0;
            for (Node n = node; n != null; n = n.next) {
                count++;
            }
            assertEquals(length, count, "the whole chain must be materialised, not truncated by depth");
        }
    }

    @Test
    void cycleIsPreservedAsIdentity() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment a = arena.allocate(16, 8);
            MemorySegment b = arena.allocate(16, 8);
            a.set(ValueLayout.JAVA_INT, 0, 1);
            a.set(ValueLayout.ADDRESS, 8, b);
            b.set(ValueLayout.JAVA_INT, 0, 2);
            b.set(ValueLayout.ADDRESS, 8, a);

            Node node = (Node) marshaller.unmarshalCustomObject(a, Node.class, new HashMap<>());

            assertEquals(1, node.value);
            assertEquals(2, node.next.value);
            assertSame(node, node.next.next, "a cycle must come back as the same Java instance");
        }
    }

    @Test
    void sharedChildIsUnmarshalledOnce() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment child = arena.allocate(16, 8);
            child.set(ValueLayout.JAVA_INT, 0, 7);
            child.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);

            MemorySegment left = arena.allocate(16, 8);
            left.set(ValueLayout.JAVA_INT, 0, 1);
            left.set(ValueLayout.ADDRESS, 8, child);

            MemorySegment right = arena.allocate(16, 8);
            right.set(ValueLayout.JAVA_INT, 0, 2);
            right.set(ValueLayout.ADDRESS, 8, child);

            MemorySegment root = arena.allocate(16, 8);
            root.set(ValueLayout.JAVA_INT, 0, 0);
            root.set(ValueLayout.ADDRESS, 8, left);

            Map<Long, Object> cache = new HashMap<>();
            Node rootNode = (Node) marshaller.unmarshalCustomObject(root, Node.class, cache);
            Node second = (Node) marshaller.unmarshalCustomObject(right, Node.class, cache);

            assertEquals(7, rootNode.next.next.value);
            assertEquals(7, second.next.value);
            assertSame(rootNode.next.next, second.next,
                    "the same native address must map to one Java object");
        }
    }

    @Test
    void nodeLimitIsEnforcedWithClearError() {
        String previous = System.getProperty("mite.maxUnmarshalledNodes");
        System.setProperty("mite.maxUnmarshalledNodes", "5");
        try {
            NativeMarshaller limited = new NativeMarshaller();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment head = chain(arena, 20);

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                        () -> limited.unmarshalCustomObject(head, Node.class, new HashMap<>()));

                assertTrue(ex.getMessage().contains("mite.maxUnmarshalledNodes"), ex.getMessage());
            }
        } finally {
            if (previous == null) {
                System.clearProperty("mite.maxUnmarshalledNodes");
            } else {
                System.setProperty("mite.maxUnmarshalledNodes", previous);
            }
        }
    }
}
