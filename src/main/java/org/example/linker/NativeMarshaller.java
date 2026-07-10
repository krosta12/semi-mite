package org.example.linker;

import org.example.annotation.MiteStruct;
import org.example.memory.MiteArray;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NativeMarshaller {

    private final long pointerSize;

    private final Map<Class<?>, Field[]> fieldsCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, FieldLayout> layoutCache = new ConcurrentHashMap<>();

    public NativeMarshaller() {
        this(8);
    }

    public NativeMarshaller(int pointerSize) {
        if (pointerSize != 4 && pointerSize != 8) {
            throw new IllegalArgumentException("pointer.size must be 4 or 8, got: " + pointerSize);
        }
        long actualAddressSize = ValueLayout.ADDRESS.byteSize();
        if (pointerSize != actualAddressSize) {
            throw new IllegalArgumentException(
                    "pointer.size=" + pointerSize + " does not match this JVM's native address size");
        }
        this.pointerSize = pointerSize;
    }

    public Object marshal(Object arg, String cppType, Arena arena,
                          List<Runnable> copyBackTasks,
                          Map<Object, MemorySegment> seen,
                          Map<Long, Object> nativeToJava) {
        if (arg instanceof MiteArray<?> ma) {
            return ma.segment();
        }
        if ("const char*".equals(cppType) || "std::string".equals(cppType)) {
            return arg == null ? MemorySegment.NULL : arena.allocateFrom((String) arg);
        }
        if (arg instanceof String) {
            return arena.allocateFrom((String) arg);
        }
        if (cppType != null && cppType.endsWith("*")) {
            return marshalPointerArg(arg, arena, copyBackTasks, seen, nativeToJava);
        }
        return arg;
    }


    private Object marshalPointerArg(Object arg, Arena arena,
                                     List<Runnable> copyBackTasks,
                                     Map<Object, MemorySegment> seen,
                                     Map<Long, Object> nativeToJava) {
        if (arg == null) return MemorySegment.NULL;

        return switch (arg) {
            case Collection<?> col -> allocateNativeCollection(col, arena, copyBackTasks, seen, nativeToJava);
            case float[] arr -> {
                MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, arr);
                copyBackTasks.add(() -> MemorySegment.copy(seg, ValueLayout.JAVA_FLOAT, 0, arr, 0, arr.length));
                yield seg;
            }
            case int[] arr -> {
                MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_INT, arr);
                copyBackTasks.add(() -> MemorySegment.copy(seg, ValueLayout.JAVA_INT, 0, arr, 0, arr.length));
                yield seg;
            }
            case long[] arr -> {
                MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_LONG, arr);
                copyBackTasks.add(() -> MemorySegment.copy(seg, ValueLayout.JAVA_LONG, 0, arr, 0, arr.length));
                yield seg;
            }
            case double[] arr -> {
                MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, arr);
                copyBackTasks.add(() -> MemorySegment.copy(seg, ValueLayout.JAVA_DOUBLE, 0, arr, 0, arr.length));
                yield seg;
            }
            case byte[] arr -> {
                MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_BYTE, arr);
                copyBackTasks.add(() -> MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, 0, arr, 0, arr.length));
                yield seg;
            }
            case short[] arr -> {
                MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_SHORT, arr);
                copyBackTasks.add(() -> MemorySegment.copy(seg, ValueLayout.JAVA_SHORT, 0, arr, 0, arr.length));
                yield seg;
            }
            case char[] arr -> {
                MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_CHAR, arr);
                copyBackTasks.add(() -> MemorySegment.copy(seg, ValueLayout.JAVA_CHAR, 0, arr, 0, arr.length));
                yield seg;
            }
            case boolean[] arr -> {
                MemorySegment seg = arena.allocate(ValueLayout.JAVA_BYTE, arr.length);
                for (int j = 0; j < arr.length; j++) {
                    seg.setAtIndex(ValueLayout.JAVA_BYTE, j, (byte) (arr[j] ? 1 : 0));
                }
                copyBackTasks.add(() -> {
                    for (int j = 0; j < arr.length; j++) {
                        arr[j] = seg.getAtIndex(ValueLayout.JAVA_BYTE, j) != 0;
                    }
                });
                yield seg;
            }
            default -> marshalCustomObject(arg, arena, copyBackTasks, seen, nativeToJava);
        };
    }

    private MemorySegment allocateNativeCollection(Collection<?> col, Arena arena,
                                                   List<Runnable> copyBackTasks,
                                                   Map<Object, MemorySegment> seen,
                                                   Map<Long, Object> nativeToJava) {
        if (col.isEmpty()) return MemorySegment.NULL;

        Object first = col.iterator().next();
        Object[] snapshot = col.toArray();

        if (first instanceof Float) {
            float[] arr = new float[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) arr[i] = ((Number) snapshot[i]).floatValue();
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, arr);
            copyBackTasks.add(() -> writeBackNumeric(col, snapshot.length,
                    k -> seg.getAtIndex(ValueLayout.JAVA_FLOAT, k)));
            return seg;
        }
        if (first instanceof Integer) {
            int[] arr = new int[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) arr[i] = ((Number) snapshot[i]).intValue();
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_INT, arr);
            copyBackTasks.add(() -> writeBackNumeric(col, snapshot.length,
                    k -> seg.getAtIndex(ValueLayout.JAVA_INT, k)));
            return seg;
        }
        if (first instanceof Long) {
            long[] arr = new long[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) arr[i] = ((Number) snapshot[i]).longValue();
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_LONG, arr);
            copyBackTasks.add(() -> writeBackNumeric(col, snapshot.length,
                    k -> seg.getAtIndex(ValueLayout.JAVA_LONG, k)));
            return seg;
        }
        if (first instanceof Double) {
            double[] arr = new double[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) arr[i] = ((Number) snapshot[i]).doubleValue();
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, arr);
            copyBackTasks.add(() -> writeBackNumeric(col, snapshot.length,
                    k -> seg.getAtIndex(ValueLayout.JAVA_DOUBLE, k)));
            return seg;
        }
        if (first instanceof Short) {
            short[] arr = new short[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) arr[i] = ((Number) snapshot[i]).shortValue();
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_SHORT, arr);
            copyBackTasks.add(() -> writeBackNumeric(col, snapshot.length,
                    k -> seg.getAtIndex(ValueLayout.JAVA_SHORT, k)));
            return seg;
        }
        if (first instanceof Byte) {
            byte[] arr = new byte[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) arr[i] = ((Number) snapshot[i]).byteValue();
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_BYTE, arr);
            copyBackTasks.add(() -> writeBackNumeric(col, snapshot.length,
                    k -> seg.getAtIndex(ValueLayout.JAVA_BYTE, k)));
            return seg;
        }

        MemorySegment ptrArray = arena.allocate(ValueLayout.ADDRESS, snapshot.length);
        for (int i = 0; i < snapshot.length; i++) {
            ptrArray.setAtIndex(ValueLayout.ADDRESS, i,
                    marshalCustomObject(snapshot[i], arena, copyBackTasks, seen, nativeToJava));
        }
        return ptrArray;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeBackNumeric(Collection col, int length,
                                  java.util.function.IntFunction<Object> reader) {
        if (col instanceof List<?> list) {
            try {
                for (int k = 0; k < length; k++) {
                    ((List<Object>) list).set(k, reader.apply(k));
                }
            } catch (UnsupportedOperationException ignored) {
            }
        } else {
            try {
                col.clear();
                for (int k = 0; k < length; k++) {
                    col.add(reader.apply(k));
                }
            } catch (UnsupportedOperationException ignored) {
            }
        }
    }

    MemorySegment marshalCustomObject(Object obj, Arena arena,
                                      List<Runnable> copyBackTasks,
                                      Map<Object, MemorySegment> seen,
                                      Map<Long, Object> nativeToJava) {
        if (obj == null) return MemorySegment.NULL;
        if (seen.containsKey(obj)) return seen.get(obj);

        if (!obj.getClass().isAnnotationPresent(MiteStruct.class)) {
            throw new IllegalArgumentException(
                    "Security Violation: " + obj.getClass().getName() +
                            " must be annotated with @MiteStruct to be passed to native code.");
        }

        try {
            Class<?> clazz = obj.getClass();
            Field[] fields = getDeclaredFields(clazz);
            FieldLayout layout = getLayout(clazz, fields);

            MemorySegment seg = arena.allocate(layout.totalSize(), layout.maxAlignment());
            seen.put(obj, seg);
            nativeToJava.put(seg.address(), obj);

            writeFields(obj, fields, layout.offsets(), seg, arena, copyBackTasks, seen, nativeToJava);

            copyBackTasks.add(() -> {
                try {
                    readScalarFields(obj, fields, layout.offsets(), seg, nativeToJava);
                } catch (Exception e) {
                    throw new RuntimeException("Copy-back failed for " + obj.getClass().getName(), e);
                }
            });

            return seg;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to marshal " + obj.getClass().getName(), e);
        }
    }

    private void writeFields(Object obj, Field[] fields, Map<Field, Long> offsets,
                             MemorySegment seg, Arena arena,
                             List<Runnable> copyBackTasks,
                             Map<Object, MemorySegment> seen,
                             Map<Long, Object> nativeToJava) throws Exception {
        for (Field field : fields) {
            Long offsetObj = offsets.get(field);
            if (offsetObj == null) continue;
            long offset = offsetObj;
            Object val = field.get(obj);
            Class<?> fType = field.getType();

            if (val == null) {
                if (!fType.isPrimitive()) seg.set(ValueLayout.ADDRESS, offset, MemorySegment.NULL);
                continue;
            }

            if (fType == int.class || fType == Integer.class) {
                seg.set(ValueLayout.JAVA_INT, offset, ((Number) val).intValue());
            } else if (fType == long.class || fType == Long.class) {
                seg.set(ValueLayout.JAVA_LONG, offset, ((Number) val).longValue());
            } else if (fType == float.class || fType == Float.class) {
                seg.set(ValueLayout.JAVA_FLOAT, offset, ((Number) val).floatValue());
            } else if (fType == double.class || fType == Double.class) {
                seg.set(ValueLayout.JAVA_DOUBLE, offset, ((Number) val).doubleValue());
            } else if (fType == short.class || fType == Short.class) {
                seg.set(ValueLayout.JAVA_SHORT, offset, ((Number) val).shortValue());
            } else if (fType == byte.class || fType == Byte.class) {
                seg.set(ValueLayout.JAVA_BYTE, offset, ((Number) val).byteValue());
            } else if (fType == boolean.class || fType == Boolean.class) {
                seg.set(ValueLayout.JAVA_BOOLEAN, offset, (Boolean) val);
            } else if (fType == String.class) {
                seg.set(ValueLayout.ADDRESS, offset, arena.allocateFrom((String) val));
            } else if (fType == float[].class) {
                float[] arr = (float[]) val;
                MemorySegment sub = arena.allocateFrom(ValueLayout.JAVA_FLOAT, arr);
                seg.set(ValueLayout.ADDRESS, offset, sub);
                copyBackTasks.add(() -> MemorySegment.copy(sub, ValueLayout.JAVA_FLOAT, 0, arr, 0, arr.length));
            } else if (fType == int[].class) {
                int[] arr = (int[]) val;
                MemorySegment sub = arena.allocateFrom(ValueLayout.JAVA_INT, arr);
                seg.set(ValueLayout.ADDRESS, offset, sub);
                copyBackTasks.add(() -> MemorySegment.copy(sub, ValueLayout.JAVA_INT, 0, arr, 0, arr.length));
            } else if (fType == long[].class) {
                long[] arr = (long[]) val;
                MemorySegment sub = arena.allocateFrom(ValueLayout.JAVA_LONG, arr);
                seg.set(ValueLayout.ADDRESS, offset, sub);
                copyBackTasks.add(() -> MemorySegment.copy(sub, ValueLayout.JAVA_LONG, 0, arr, 0, arr.length));
            } else if (fType == double[].class) {
                double[] arr = (double[]) val;
                MemorySegment sub = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, arr);
                seg.set(ValueLayout.ADDRESS, offset, sub);
                copyBackTasks.add(() -> MemorySegment.copy(sub, ValueLayout.JAVA_DOUBLE, 0, arr, 0, arr.length));
            } else if (fType == byte[].class) {
                byte[] arr = (byte[]) val;
                MemorySegment sub = arena.allocateFrom(ValueLayout.JAVA_BYTE, arr);
                seg.set(ValueLayout.ADDRESS, offset, sub);
                copyBackTasks.add(() -> MemorySegment.copy(sub, ValueLayout.JAVA_BYTE, 0, arr, 0, arr.length));
            } else if (fType == boolean[].class) {
                boolean[] arr = (boolean[]) val;
                MemorySegment sub = arena.allocate(ValueLayout.JAVA_BYTE, arr.length);
                for (int j = 0; j < arr.length; j++) sub.setAtIndex(ValueLayout.JAVA_BYTE, j, (byte) (arr[j] ? 1 : 0));
                seg.set(ValueLayout.ADDRESS, offset, sub);
                copyBackTasks.add(() -> {
                    for (int j = 0; j < arr.length; j++) arr[j] = sub.getAtIndex(ValueLayout.JAVA_BYTE, j) != 0;
                });
            } else if (fType == short[].class) {
                short[] arr = (short[]) val;
                MemorySegment sub = arena.allocateFrom(ValueLayout.JAVA_SHORT, arr);
                seg.set(ValueLayout.ADDRESS, offset, sub);
                copyBackTasks.add(() -> MemorySegment.copy(sub, ValueLayout.JAVA_SHORT, 0, arr, 0, arr.length));
            } else if (fType.isArray()) {
                Object[] arr = (Object[]) val;
                MemorySegment sub = arena.allocate(ValueLayout.ADDRESS, arr.length);
                for (int j = 0; j < arr.length; j++) {
                    sub.setAtIndex(ValueLayout.ADDRESS, j,
                            marshalCustomObject(arr[j], arena, copyBackTasks, seen, nativeToJava));
                }
                seg.set(ValueLayout.ADDRESS, offset, sub);
            } else if (val instanceof Collection<?> col) {
                seg.set(ValueLayout.ADDRESS, offset,
                        allocateNativeCollection(col, arena, copyBackTasks, seen, nativeToJava));
            } else {
                seg.set(ValueLayout.ADDRESS, offset,
                        marshalCustomObject(val, arena, copyBackTasks, seen, nativeToJava));
            }
        }
    }

    private void readScalarFields(Object obj, Field[] fields, Map<Field, Long> offsets,
                                  MemorySegment seg, Map<Long, Object> nativeToJava) throws Exception {
        for (Field field : fields) {
            Long offsetObj = offsets.get(field);
            if (offsetObj == null) continue;
            long offset = offsetObj;
            Class<?> fType = field.getType();

            if (fType == int.class || fType == Integer.class)
                field.set(obj, seg.get(ValueLayout.JAVA_INT, offset));
            else if (fType == long.class || fType == Long.class)
                field.set(obj, seg.get(ValueLayout.JAVA_LONG, offset));
            else if (fType == float.class || fType == Float.class)
                field.set(obj, seg.get(ValueLayout.JAVA_FLOAT, offset));
            else if (fType == double.class || fType == Double.class)
                field.set(obj, seg.get(ValueLayout.JAVA_DOUBLE, offset));
            else if (fType == short.class || fType == Short.class)
                field.set(obj, seg.get(ValueLayout.JAVA_SHORT, offset));
            else if (fType == byte.class || fType == Byte.class)
                field.set(obj, seg.get(ValueLayout.JAVA_BYTE, offset));
            else if (fType == boolean.class || fType == Boolean.class)
                field.set(obj, seg.get(ValueLayout.JAVA_BOOLEAN, offset));
            else if (!fType.isPrimitive() && fType != String.class
                    && !fType.isArray() && !Collection.class.isAssignableFrom(fType)) {
                MemorySegment childSeg = seg.get(ValueLayout.ADDRESS, offset);
                if (childSeg.address() == 0) {
                    field.set(obj, null);
                } else {
                    Object linked = nativeToJava.get(childSeg.address());
                    if (linked != null) field.set(obj, linked);
                }
            }
        }
    }

    public Object unmarshalCustomObject(MemorySegment seg, Class<?> clazz,
                                        Map<Long, Object> nativeToJava) {
        if (seg.address() == 0) return null;
        if (nativeToJava.containsKey(seg.address())) return nativeToJava.get(seg.address());

        try {
            Object obj = clazz.getDeclaredConstructor().newInstance();
            nativeToJava.put(seg.address(), obj);

            Field[] fields = getDeclaredFields(clazz);
            FieldLayout layout = getLayout(clazz, fields);
            long structSize = layout.totalSize();
            MemorySegment sized = seg.reinterpret(structSize);

            readScalarFields(obj, fields, layout.offsets(), sized, nativeToJava);
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("Unmarshal failed for " + clazz.getName(), e);
        }
    }

    String readNullTerminatedString(MemorySegment seg) {
        long maxLen = seg.byteSize() == 0 ? MAX_STRING_BYTES : Math.min(seg.byteSize(), MAX_STRING_BYTES);
        MemorySegment safe = seg.byteSize() > 0 ? seg : seg.reinterpret(MAX_STRING_BYTES);
        long len = 0;
        while (len < maxLen && safe.get(ValueLayout.JAVA_BYTE, len) != 0) len++;
        return new String(safe.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static final long MAX_STRING_BYTES = 65536;

    record FieldLayout(Map<Field, Long> offsets, long totalSize, long maxAlignment) {
    }


    private FieldLayout getLayout(Class<?> clazz, Field[] fields) {
        FieldLayout cached = layoutCache.get(clazz);
        if (cached != null) return cached;
        FieldLayout computed = computeLayout(fields);
        layoutCache.putIfAbsent(clazz, computed);
        return layoutCache.get(clazz);
    }

    private FieldLayout computeLayout(Field[] fields) {
        Map<Field, Long> offsets = new HashMap<>();
        long offset = 0;
        long maxAlign = 1;

        for (Field field : fields) {
            Class<?> fType = field.getType();
            long size = fieldSize(fType);
            long align = fieldAlignment(fType);
            if (align > maxAlign) maxAlign = align;
            offset = (offset + align - 1) & -align;
            offsets.put(field, offset);
            offset += size;
        }

        long total = (offset + maxAlign - 1) & -maxAlign;
        if (total == 0) total = 1;
        return new FieldLayout(offsets, total, maxAlign);
    }

    private long fieldSize(Class<?> fType) {
        if (fType == int.class || fType == Integer.class
                || fType == float.class || fType == Float.class) return 4;
        if (fType == long.class || fType == Long.class
                || fType == double.class || fType == Double.class) return 8;
        if (fType == short.class || fType == Short.class) return 2;
        if (fType == boolean.class || fType == Boolean.class
                || fType == byte.class || fType == Byte.class) return 1;
        return pointerSize;
    }


    private long fieldAlignment(Class<?> fType) {
        if (!fType.isPrimitive()) return pointerSize;
        return fieldSize(fType);
    }

    private Field[] getDeclaredFields(Class<?> clazz) {
        Field[] cached = fieldsCache.get(clazz);
        if (cached != null) return cached;

        Field[] fields = clazz.getDeclaredFields();
        List<Field> result = new ArrayList<>();
        for (Field f : fields) {
            if (!f.isSynthetic()) {
                f.setAccessible(true);
                result.add(f);
            }
        }
        Field[] computed = result.toArray(new Field[0]);
        fieldsCache.putIfAbsent(clazz, computed);
        return fieldsCache.get(clazz);
    }

}