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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;


public class NativeMarshaller {


    public Object marshal(Object arg, String cppType, Arena arena,
                          List<Runnable> copyBackTasks,
                          Map<Object, MemorySegment> seen,
                          Map<Long, Object> nativeToJava) {
        if (arg instanceof MiteArray ma) {
            return ma.segment();
        }
        if ("const char*".equals(cppType) || "std::string".equals(cppType)) {
            return arg == null ? MemorySegment.NULL : arena.allocateFrom((String) arg);
        }
        if (arg instanceof String) {
            return arena.allocateFrom((String) arg);
        }
        if (cppType != null && cppType.endsWith("*")) {
            return marshalPointerArg(arg, cppType, arena, copyBackTasks, seen, nativeToJava);
        }

        return arg;
    }

    public Object unmarshal(Object result, String returnType,
                            Map<Long, Object> nativeToJava,
                            Map<String, Class<?>> structRegistry) {
        if (!(result instanceof MemorySegment seg)) {
            return result;
        }
        if (seg.address() == 0) {
            return null;
        }
        if ("const char*".equals(returnType) || "std::string".equals(returnType)) {
            return readNullTerminatedString(seg);
        }
        if (returnType != null && returnType.endsWith("*")) {
            String cleanType = returnType.replace("*", "").trim();
            Class<?> targetClass = structRegistry.get(cleanType);
            if (targetClass != null) {
                return unmarshalCustomObject(seg, targetClass, nativeToJava);
            }
        }
        return result;
    }


    @SuppressWarnings("unchecked")
    private Object marshalPointerArg(Object arg, String cppType, Arena arena,
                                     List<Runnable> copyBackTasks,
                                     Map<Object, MemorySegment> seen,
                                     Map<Long, Object> nativeToJava) {
        if (arg == null) return MemorySegment.NULL;

        return switch (arg) {
            case Collection<?> col -> allocateNativeCollection(col, cppType, arena, copyBackTasks, seen, nativeToJava);
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

    @SuppressWarnings("unchecked")
    private MemorySegment allocateNativeCollection(Collection<?> col, String cppType, Arena arena,
                                                   List<Runnable> copyBackTasks,
                                                   Map<Object, MemorySegment> seen,
                                                   Map<Long, Object> nativeToJava) {
        if (col.isEmpty()) return MemorySegment.NULL;

        Object first = col.iterator().next();

        if (first instanceof Float) {
            float[] arr = new float[col.size()];
            int i = 0;
            for (Object x : col) arr[i++] = ((Number) x).floatValue();
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, arr);
            if (col instanceof List<?> list) {
                copyBackTasks.add(() -> {
                    for (int k = 0; k < arr.length; k++) {
                        ((List<Float>) list).set(k, seg.getAtIndex(ValueLayout.JAVA_FLOAT, k));
                    }
                });
            }
            return seg;
        }
        if (first instanceof Integer) {
            int[] arr = col.stream().mapToInt(x -> ((Number) x).intValue()).toArray();
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_INT, arr);
            if (col instanceof List<?> list) {
                copyBackTasks.add(() -> {
                    for (int k = 0; k < arr.length; k++) {
                        ((List<Integer>) list).set(k, seg.getAtIndex(ValueLayout.JAVA_INT, k));
                    }
                });
            }
            return seg;
        }
        if (first instanceof Long) {
            long[] arr = col.stream().mapToLong(x -> ((Number) x).longValue()).toArray();
            return arena.allocateFrom(ValueLayout.JAVA_LONG, arr);
        }
        if (first instanceof Double) {
            double[] arr = col.stream().mapToDouble(x -> ((Number) x).doubleValue()).toArray();
            return arena.allocateFrom(ValueLayout.JAVA_DOUBLE, arr);
        }

        MemorySegment ptrArray = arena.allocate(ValueLayout.ADDRESS, col.size());
        int idx = 0;
        for (Object item : col) {
            ptrArray.setAtIndex(ValueLayout.ADDRESS, idx++,
                    marshalCustomObject(item, arena, copyBackTasks, seen, nativeToJava));
        }
        return ptrArray;
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
                            " must be anotated with @MiteStruct to be passed to native code.");
        }

        try {
            Field[] fields = getDeclaredFields(obj.getClass());
            FieldLayout layout = computeLayout(fields);

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

    @SuppressWarnings("unchecked")
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
                        allocateNativeCollection(col, null, arena, copyBackTasks, seen, nativeToJava));
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
            FieldLayout layout = computeLayout(fields);
            long structSize = layout.totalSize();
            MemorySegment sized = seg.reinterpret(structSize);

            readScalarFields(obj, fields, layout.offsets(), sized, nativeToJava);
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("Unmarshal failed for " + clazz.getName(), e);
        }
    }

    String readNullTerminatedString(MemorySegment seg) {
        MemorySegment safe = seg.reinterpret(4096);
        long len = 0;
        while (len < 4096 && safe.get(ValueLayout.JAVA_BYTE, len) != 0) len++;
        return new String(safe.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE));
    }


    record FieldLayout(Map<Field, Long> offsets, long totalSize, long maxAlignment) {
    }

    private FieldLayout computeLayout(Field[] fields) {
        Map<Field, Long> offsets = new HashMap<>();
        long offset = 0;
        long maxAlign = 1;

        for (Field field : fields) {
            Class<?> fType = field.getType();
            long size = fieldSize(fType);
            long align = size;
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
        if (fType == int.class || fType == Integer.class || fType == float.class || fType == Float.class) return 4;
        if (fType == long.class || fType == Long.class || fType == double.class || fType == Double.class) return 8;
        if (fType == short.class || fType == Short.class) return 2;
        if (fType == boolean.class || fType == Boolean.class || fType == byte.class || fType == Byte.class) return 1;
        return 8;
    }

    private Field[] getDeclaredFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<Field> result = new ArrayList<>();
        for (Field f : fields) {
            if (!f.isSynthetic()) {
                f.setAccessible(true);
                result.add(f);
            }
        }
        return result.toArray(new Field[0]);
    }

}