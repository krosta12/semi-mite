package org.example.memory;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class DeepMarshallerRoundTripTest {

    private final StructLayoutCache layoutCache = new StructLayoutCache(4);
    private final DeepMarshaller marshaller = new DeepMarshaller(layoutCache);
    private final DeepUnmarshaller unmarshaller = new DeepUnmarshaller(layoutCache);

    @Test
    void roundTripsSimpleDto() {
        SimpleDto original = new SimpleDto();
        original.id = 42;
        original.value = 3.5f;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = marshaller.marshal(original, arena);
            SimpleDto result = unmarshaller.unmarshal(segment, SimpleDto.class);

            assertEquals(42, result.id);
            assertEquals(3.5f, result.value);
        }
    }

    @Test
    void roundTripsNestedDto() {
        ParentDto parent = new ParentDto();
        parent.id = 1;
        parent.child = new SimpleDto();
        parent.child.id = 2;
        parent.child.value = 9.9f;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = marshaller.marshal(parent, arena);
            ParentDto result = unmarshaller.unmarshal(segment, ParentDto.class);

            assertEquals(1, result.id);
            assertNotNull(result.child);
            assertEquals(2, result.child.id);
            assertEquals(9.9f, result.child.value);
        }
    }

    @Test
    void handlesCyclicGraphWithoutStackOverflow() {
        UserDto user = new UserDto();
        user.id = 1;
        ManagerDto manager = new ManagerDto();
        manager.id = 2;
        user.manager = manager;
        manager.employee = user;

        try (Arena arena = Arena.ofConfined()) {
            assertDoesNotThrow(() -> {
                MemorySegment segment = marshaller.marshal(user, arena);
                UserDto result = unmarshaller.unmarshal(segment, UserDto.class);

                assertEquals(1, result.id);
                assertEquals(2, result.manager.id);
                // cycle resolved: manager.employee points back to the same reconstructed user
                assertSame(result, result.manager.employee);
            });
        }
    }

}