package org.example.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public class DeepMarshaller {

    private final StructLayoutCache layoutCache;

    public DeepMarshaller(StructLayoutCache layoutCache) {
        this.layoutCache = layoutCache;
    }

    public MemorySegment marshal(Object root, Arena arena) {
        return marshal(root, arena, new IdentityHashMap<>());
    }

    private MemorySegment marshal(Object value, Arena arena, Map<Object, MemorySegment> visited) {
        if (value == null) {
            return MemorySegment.NULL;
        }
        MemorySegment existing = visited.get(value);
        if (existing != null) {
            return existing;
        }

        Class<?> type = value.getClass();

        if (type.isArray() && !type.getComponentType().isPrimitive()) {
            return marshalObjectArray((Object[]) value, arena, visited);
        }
        if (value instanceof Collection<?> collection) {
            return marshalCollection(collection, arena, visited);
        }
        return marshalStruct(value, type, arena, visited);
    }

    private MemorySegment marshalStruct(Object value, Class<?> type, Arena arena, Map<Object, MemorySegment> visited) {
        StructLayout layout = layoutCache.layoutFor(type);
        MemorySegment segment = arena.allocate(layout);

        visited.put(value, segment);

        for (Field field : layoutCache.fieldsFor(type)) {
            try {
                writeField(value, field, segment, layout, arena, visited);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read field: " + field, e);
            }
        }
        return segment;
    }

    private void writeField(Object owner, Field field, MemorySegment segment, StructLayout layout,
                            Arena arena, Map<Object, MemorySegment> visited) throws IllegalAccessException {
        String name = field.getName();
        Class<?> fieldType = field.getType();
        long offset = layout.byteOffset(MemoryLayout.PathElement.groupElement(name));

        if (fieldType == int.class) {
            segment.set(ValueLayout.JAVA_INT, offset, field.getInt(owner));
        } else if (fieldType == float.class) {
            segment.set(ValueLayout.JAVA_FLOAT, offset, field.getFloat(owner));
        } else if (fieldType == double.class) {
            segment.set(ValueLayout.JAVA_DOUBLE, offset, field.getDouble(owner));
        } else if (fieldType == long.class) {
            segment.set(ValueLayout.JAVA_LONG, offset, field.getLong(owner));
        } else if (fieldType == short.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, field.getShort(owner));
        } else if (fieldType == byte.class) {
            segment.set(ValueLayout.JAVA_BYTE, offset, field.getByte(owner));
        } else if (fieldType == boolean.class) {
            segment.set(ValueLayout.JAVA_BOOLEAN, offset, field.getBoolean(owner));
        } else {
            Object refValue = field.get(owner);
            MemorySegment refSegment = marshal(refValue, arena, visited);
            segment.set(ValueLayout.ADDRESS, offset, refSegment);
        }
    }

    private MemorySegment marshalObjectArray(Object[] array, Arena arena, Map<Object, MemorySegment> visited) {
        MemorySegment pointerArray = arena.allocate(ValueLayout.ADDRESS, array.length);
        visited.put(array, pointerArray);
        for (int i = 0; i < array.length; i++) {
            MemorySegment elementSegment = marshal(array[i], arena, visited);
            pointerArray.setAtIndex(ValueLayout.ADDRESS, i, elementSegment);
        }
        return pointerArray;
    }

    private MemorySegment marshalCollection(Collection<?> collection, Arena arena, Map<Object, MemorySegment> visited) {
        Object[] array = collection.toArray();
        MemorySegment pointerArray = arena.allocate(ValueLayout.ADDRESS, array.length);
        visited.put(collection, pointerArray);
        for (int i = 0; i < array.length; i++) {
            MemorySegment elementSegment = marshal(array[i], arena, visited);
            pointerArray.setAtIndex(ValueLayout.ADDRESS, i, elementSegment);
        }
        return pointerArray;
    }

}