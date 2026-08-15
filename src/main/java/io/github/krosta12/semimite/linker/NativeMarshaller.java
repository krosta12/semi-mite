package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.MiteStruct;
import io.github.krosta12.semimite.memory.MiteArray;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles marshalling and unmarshalling of data between Java objects and
 * off-heap native memory: {@code @MiteStruct} structures, primitive arrays,
 * collections, strings, and scalar values. Field layouts for structures are
 * cached per class for performance.
 */
public class NativeMarshaller {

    private final long pointerSize;

    private final Map<Class<?>, Field[]> fieldsCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, FieldLayout> layoutCache = new ConcurrentHashMap<>();

    /**
     * Creates a marshaller using the default pointer size (8 bytes).
     */
    public NativeMarshaller() {
        this(8);
    }

    /**
     * Creates a marshaller for the given native pointer size.
     *
     * @param pointerSize the native pointer size in bytes; must be 4 or 8, and
     *                    must match the current JVM's native address size
     * @throws IllegalArgumentException if {@code pointerSize} is not 4 or 8, or
     *                                  does not match the JVM's actual address size
     */
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

    /**
     * Marshals a single call argument into a value suitable for a native
     * downcall: a {@link MiteArray} becomes its segment, strings become a
     * null-terminated native string, pointer-typed arguments are delegated to
     * {@link #marshalPointerArg}, and anything else is passed through unchanged.
     *
     * @param arg           the Java argument value
     * @param cppType       the C++ type hint for this argument, or {@code null}
     * @param arena         the arena to allocate native memory from
     * @param copyBackTasks list to append copy-back tasks to (run after the native call returns)
     * @param seen          identity map of already-marshalled objects (cycle protection)
     * @param nativeToJava  map of native addresses to their originating Java objects
     * @return the value to pass to the native call for this argument
     */
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


    /**
     * Marshals a pointer-typed argument: collections, primitive arrays
     * ({@code float[]}, {@code int[]}, {@code long[]}, {@code double[]},
     * {@code byte[]}, {@code short[]}, {@code char[]}, {@code boolean[]}), or
     * an arbitrary {@code @MiteStruct} object. For primitive arrays, a
     * copy-back task is registered to write changes back into the Java array
     * after the call returns.
     *
     * @param arg           the argument value
     * @param arena         the arena to allocate native memory from
     * @param copyBackTasks list to append copy-back tasks to
     * @param seen          identity map of already-marshalled objects (cycle protection)
     * @param nativeToJava  map of native addresses to their originating Java objects
     * @return the native memory segment representing the argument
     */
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

    /**
     * Allocates native memory for a {@link Collection}, determining the
     * element type from the first element (numeric types get a packed
     * primitive array; other types get an array of struct pointers). A
     * copy-back task is registered to propagate changes back to the collection.
     *
     * @param col           the collection to marshal
     * @param arena         the arena to allocate native memory from
     * @param copyBackTasks list to append copy-back tasks to
     * @param seen          identity map of already-marshalled objects (cycle protection)
     * @param nativeToJava  map of native addresses to their originating Java objects
     * @return the native memory segment, or {@link MemorySegment#NULL} if the collection is empty
     */
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

    /**
     * Writes values read back from native memory into a Java collection after
     * a native call returns. If {@code col} is a {@link List}, elements are
     * updated in place by index; otherwise the collection is cleared and refilled.
     * Read-only collections silently ignore the write-back.
     *
     * @param col    the collection to update
     * @param length the number of elements to write back
     * @param reader supplies the value at a given index from native memory
     */
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

    /**
     * Marshals an object annotated with {@code @MiteStruct} into an off-heap
     * native structure, writing all its fields. Already-seen objects (tracked
     * via {@code seen}) are reused rather than re-marshalled, protecting
     * against reference cycles. A copy-back task is registered to read the
     * fields back into the Java object once the native call returns.
     *
     * @param obj           the {@code @MiteStruct}-annotated object to marshal
     * @param arena         the arena to allocate native memory from
     * @param copyBackTasks list to append the copy-back task to
     * @param seen          identity map of already-marshalled objects (cycle protection)
     * @param nativeToJava  map of native addresses to their originating Java objects
     * @return the native memory segment representing the object,
     * or {@link MemorySegment#NULL} if {@code obj} is {@code null}
     * @throws IllegalArgumentException if {@code obj}'s class is not annotated with {@code @MiteStruct}
     */
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

    /**
     * Writes a Java object's field values into the corresponding offsets of
     * its native structure segment (scalars, strings, arrays, nested
     * {@code @MiteStruct} objects, and collections).
     *
     * @param obj           the source Java object
     * @param fields        the object's declared fields
     * @param offsets       field-to-byte-offset mapping within the structure
     * @param seg           the destination native memory segment
     * @param arena         the arena to allocate any nested native memory from
     * @param copyBackTasks list to append copy-back tasks for nested allocations
     * @param seen          identity map of already-marshalled objects (cycle protection)
     * @param nativeToJava  map of native addresses to their originating Java objects
     * @throws Exception if a field cannot be read via reflection
     */
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

    /**
     * Reads scalar and reference fields back from native memory into a Java
     * object. Used both for copy-back after a call and for unmarshalling a
     * returned native structure. Nested {@code @MiteStruct} references are
     * resolved via {@code nativeToJava} rather than being re-unmarshalled.
     *
     * @param obj          the Java object to update
     * @param fields       the object's declared fields
     * @param offsets      field-to-byte-offset mapping within the structure
     * @param seg          the source native memory segment
     * @param nativeToJava map of native addresses to their originating Java objects
     * @throws Exception if a field cannot be set via reflection
     */
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

    /**
     * Creates (or reuses) a Java object from a native structure segment,
     * populating its fields. Already-unmarshalled objects are looked up by
     * native address in {@code nativeToJava} to support cyclic structures.
     *
     * @param seg          the native structure segment
     * @param clazz        the {@code @MiteStruct}-annotated target class
     * @param nativeToJava cache of native addresses to already-unmarshalled Java objects
     * @return the unmarshalled Java object, or {@code null} if {@code seg} is a null pointer
     * @throws RuntimeException if the object cannot be instantiated or its fields cannot be read
     */
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

    /**
     * Reads a null-terminated C string from native memory into a Java {@link String}.
     *
     * @param seg the native memory segment containing the string
     * @return the decoded UTF-8 string (truncated at {@link #MAX_STRING_BYTES} if unbounded)
     */
    String readNullTerminatedString(MemorySegment seg) {
        long maxLen = seg.byteSize() == 0 ? MAX_STRING_BYTES : Math.min(seg.byteSize(), MAX_STRING_BYTES);
        MemorySegment safe = seg.byteSize() > 0 ? seg : seg.reinterpret(MAX_STRING_BYTES);
        long len = 0;
        while (len < maxLen && safe.get(ValueLayout.JAVA_BYTE, len) != 0) len++;
        return new String(safe.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static final long MAX_STRING_BYTES = 65536;

    /**
     * Computed native layout of a Java object's fields, mirroring a C/C++
     * struct layout (offsets, total size, and required alignment).
     *
     * @param offsets      byte offset of each field within the structure
     * @param totalSize    total structure size in bytes, including trailing padding
     * @param maxAlignment the largest alignment requirement among all fields
     */
    record FieldLayout(Map<Field, Long> offsets, long totalSize, long maxAlignment) {
    }


    /**
     * Returns the cached field layout for a class, computing and caching it
     * on first access.
     *
     * @param clazz  the {@code @MiteStruct}-annotated class
     * @param fields the class's declared fields
     * @return the computed (or cached) field layout
     */
    private FieldLayout getLayout(Class<?> clazz, Field[] fields) {
        FieldLayout cached = layoutCache.get(clazz);
        if (cached != null) return cached;
        FieldLayout computed = computeLayout(fields);
        layoutCache.putIfAbsent(clazz, computed);
        return layoutCache.get(clazz);
    }

    /**
     * Computes byte offsets, total size, and required alignment for a set of
     * fields, following standard C-style struct layout rules (each field
     * aligned to its own size, struct padded to its largest field's alignment).
     *
     * @param fields the fields to lay out, in declaration order
     * @return the computed layout
     */
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

    /**
     * Returns the native size in bytes for a field's type.
     *
     * @param fType the field's declared type
     * @return 4 for int/float, 8 for long/double, 2 for short, 1 for
     * byte/boolean, or {@link #pointerSize} for reference types
     */
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


    /**
     * Returns the required alignment in bytes for a field's type.
     *
     * @param fType the field's declared type
     * @return {@link #pointerSize} for non-primitive types, otherwise the same as {@link #fieldSize}
     */
    private long fieldAlignment(Class<?> fType) {
        if (!fType.isPrimitive()) return pointerSize;
        return fieldSize(fType);
    }

    /**
     * Returns the cached, non-synthetic, accessible declared fields of a
     * class, computing and caching them on first access.
     *
     * @param clazz the class to inspect
     * @return the class's non-synthetic declared fields, each made accessible
     */
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