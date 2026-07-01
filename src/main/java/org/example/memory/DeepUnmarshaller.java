package org.example.memory;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;


public class DeepUnmarshaller {

    private final StructLayoutCache layoutCache;

    public DeepUnmarshaller(StructLayoutCache layoutCache) {
        this.layoutCache = layoutCache;
    }

    public <T> T unmarshal(MemorySegment segment, Class<T> type) {
        return unmarshal(segment, type, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private <T> T unmarshal(MemorySegment segment, Class<T> type, Map<Long, Object> visited) {
        if (segment == null || segment.equals(MemorySegment.NULL)) {
            return null;
        }
        long address = segment.address();
        Object existing = visited.get(address);
        if (existing != null) {
            return (T) existing;
        }

        StructLayout layout = layoutCache.layoutFor(type);
        MemorySegment sized = segment.reinterpret(layout.byteSize());

        T instance = instantiate(type);
        visited.put(address, instance);

        for (Field field : layoutCache.fieldsFor(type)) {
            try {
                readField(instance, field, sized, layout, visited);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot write field: " + field, e);
            }
        }
        return instance;
    }

    private void readField(Object instance, Field field, MemorySegment segment, StructLayout layout,
                           Map<Long, Object> visited) throws IllegalAccessException {
        String name = field.getName();
        Class<?> fieldType = field.getType();
        long offset = layout.byteOffset(MemoryLayout.PathElement.groupElement(name));

        if (fieldType == int.class) {
            field.setInt(instance, segment.get(ValueLayout.JAVA_INT, offset));
        } else if (fieldType == float.class) {
            field.setFloat(instance, segment.get(ValueLayout.JAVA_FLOAT, offset));
        } else if (fieldType == double.class) {
            field.setDouble(instance, segment.get(ValueLayout.JAVA_DOUBLE, offset));
        } else if (fieldType == long.class) {
            field.setLong(instance, segment.get(ValueLayout.JAVA_LONG, offset));
        } else if (fieldType == short.class) {
            field.setShort(instance, segment.get(ValueLayout.JAVA_SHORT, offset));
        } else if (fieldType == byte.class) {
            field.setByte(instance, segment.get(ValueLayout.JAVA_BYTE, offset));
        } else if (fieldType == boolean.class) {
            field.setBoolean(instance, segment.get(ValueLayout.JAVA_BOOLEAN, offset));
        } else if (!fieldType.isArray() && !java.util.Collection.class.isAssignableFrom(fieldType)
                && !fieldType.isInterface() && fieldType != String.class) {
            MemorySegment refSegment = segment.get(ValueLayout.ADDRESS, offset);
            Object refValue = unmarshal(refSegment, fieldType, visited);
            field.set(instance, refValue);
        }

    }

    @SuppressWarnings("unchecked")
    private <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot instantiate " + type.getName() + ": requires a no-arg constructor", e);
        }
    }

}