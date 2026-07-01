package org.example.memory;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class StructLayoutCache {

    private final Map<Class<?>, StructLayout> cache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Field>> fieldsCache = new ConcurrentHashMap<>();
    private final int alignmentBytes;

    public StructLayoutCache(int alignmentBytes) {
        this.alignmentBytes = alignmentBytes;
    }

    public StructLayout layoutFor(Class<?> type) {
        return cache.computeIfAbsent(type, this::buildLayout);
    }

    public List<Field> fieldsFor(Class<?> type) {
        return fieldsCache.computeIfAbsent(type, this::collectFields);
    }

    private StructLayout buildLayout(Class<?> type) {
        List<Field> fields = fieldsFor(type);
        List<MemoryLayout> members = new ArrayList<>();
        for (Field field : fields) {
            MemoryLayout layout = layoutForField(field).withName(field.getName());
            members.add(layout);
        }
        StructLayout layout = MemoryLayout.structLayout(members.toArray(new MemoryLayout[0]));
        return alignmentBytes > 0 ? (StructLayout) layout.withByteAlignment(alignmentBytes) : layout;
    }

    private MemoryLayout layoutForField(Field field) {
        Class<?> fieldType = field.getType();
        if (fieldType == int.class) return ValueLayout.JAVA_INT;
        if (fieldType == float.class) return ValueLayout.JAVA_FLOAT;
        if (fieldType == double.class) return ValueLayout.JAVA_DOUBLE;
        if (fieldType == long.class) return ValueLayout.JAVA_LONG;
        if (fieldType == short.class) return ValueLayout.JAVA_SHORT;
        if (fieldType == byte.class) return ValueLayout.JAVA_BYTE;
        if (fieldType == boolean.class) return ValueLayout.JAVA_BOOLEAN;

        AddressLayout address = ValueLayout.ADDRESS;
        return address;
    }

    private List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
    }

    public boolean isReferenceField(Field field) {
        Class<?> fieldType = field.getType();
        return !fieldType.isPrimitive();
    }

    public boolean isCollection(Class<?> type) {
        return Collection.class.isAssignableFrom(type);
    }

}