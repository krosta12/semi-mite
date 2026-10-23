package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.MiteInline;
import io.github.krosta12.semimite.annotation.MiteStruct;
import io.github.krosta12.semimite.memory.MiteArray;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

    /** Guards against infinite recursion while laying out {@code @MiteInline} fields. */
    private final ThreadLocal<Set<Class<?>>> layoutsInProgress =
            ThreadLocal.withInitial(LinkedHashSet::new);

    /** Upper bound on structures read by one unmarshal call; see mite.maxUnmarshalledNodes. */
    private final int maxUnmarshalledNodes =
            Integer.getInteger("mite.maxUnmarshalledNodes", 1_000_000);

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
            case char[] ignored -> {
                throw new IllegalArgumentException(
                        "char[] is not supported (Java chars are 2 bytes, C++ chars are 1): "
                                + "use byte[] for char*, String for const char*, "
                                + "or short[] for wchar_t*/uint16_t*");
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
            FieldLayout layout = layoutOf(clazz);

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

            if (isInline(field)) {
                if (fType.isArray()) {
                    writeInlineArrayField(field, val, seg, offset, copyBackTasks, arena, seen, nativeToJava);
                } else {
                    FieldLayout nested = layoutOf(fType);
                    MemorySegment slot = seg.asSlice(offset, nested.totalSize());
                    if (val == null) {
                        slot.fill((byte) 0);
                    } else {
                        writeFields(val, getDeclaredFields(fType), nested.offsets(), slot,
                                arena, copyBackTasks, seen, nativeToJava);
                    }
                }
                continue;
            }

            if (val == null) {
                if (!fType.isPrimitive()) seg.set(ValueLayout.ADDRESS, offset, MemorySegment.NULL);
                if (fType == String.class) {
                    copyBackTasks.add(() -> {
                        try {
                            MemorySegment current = seg.get(ValueLayout.ADDRESS, offset);
                            if (current.address() != 0) {
                                field.set(obj, readNullTerminatedString(
                                        current.reinterpret(MAX_STRING_BYTES)));
                            }
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(
                                    "String copy-back failed for " + field.getName(), e);
                        }
                    });
                }
                continue;
            }

            if (fType == int.class) {
                seg.set(ValueLayout.JAVA_INT, offset, (Integer) val);
            } else if (fType == long.class) {
                seg.set(ValueLayout.JAVA_LONG, offset, (Long) val);
            } else if (fType == float.class) {
                seg.set(ValueLayout.JAVA_FLOAT, offset, (Float) val);
            } else if (fType == double.class) {
                seg.set(ValueLayout.JAVA_DOUBLE, offset, (Double) val);
            } else if (fType == short.class) {
                seg.set(ValueLayout.JAVA_SHORT, offset, (Short) val);
            } else if (fType == byte.class) {
                seg.set(ValueLayout.JAVA_BYTE, offset, (Byte) val);
            } else if (fType == boolean.class) {
                seg.set(ValueLayout.JAVA_BOOLEAN, offset, (Boolean) val);
            } else if (fType == String.class) {
                String text = (String) val;
                MemorySegment buffer = arena.allocateFrom(text);
                seg.set(ValueLayout.ADDRESS, offset, buffer);
                copyBackTasks.add(() -> {
                    try {
                        MemorySegment current = seg.get(ValueLayout.ADDRESS, offset);
                        String updated;
                        if (current.address() == 0) {
                            updated = null;
                        } else if (current.address() == buffer.address()) {
                            updated = readStringInAllocatedBuffer(buffer);
                        } else {
                            updated = readNullTerminatedString(current.reinterpret(MAX_STRING_BYTES));
                        }
                        if (!Objects.equals(updated, text)) {
                            field.set(obj, updated);
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("String copy-back failed for " + field.getName(), e);
                    }
                });
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
     * Reads fields back into a Java object after a call. {@code String} fields
     * are skipped here and re-read from their own buffer.
     *
     * @param obj          the Java object to update
     * @param fields       the object's declared fields
     * @param offsets      field-to-byte-offset mapping
     * @param seg          the native structure
     * @param nativeToJava native address to Java object map
     * @throws Exception if a field cannot be set
     */
    private void readScalarFields(Object obj, Field[] fields, Map<Field, Long> offsets,
                                  MemorySegment seg, Map<Long, Object> nativeToJava) throws Exception {
        readFields(obj, fields, offsets, seg, nativeToJava, false, null);
    }

    /**
     * Reads fields back from native memory.
     *
     * @param obj          the Java object to update
     * @param fields       the object's declared fields
     * @param offsets      field-to-byte-offset mapping
     * @param seg          the native structure
     * @param nativeToJava native address to Java object map
     * @param readStrings  whether {@code String} fields are read from pointers
     * @param pending      unmarshal queue, or {@code null} during copy-back
     * @throws Exception if a field cannot be set
     */
    private void readFields(Object obj, Field[] fields, Map<Field, Long> offsets,
                            MemorySegment seg, Map<Long, Object> nativeToJava,
                            boolean readStrings, Deque<PendingStruct> pending) throws Exception {
        for (Field field : fields) {
            Long offsetObj = offsets.get(field);
            if (offsetObj == null) continue;
            long offset = offsetObj;
            Class<?> fType = field.getType();

            if (isInline(field)) {
                if (fType.isArray()) {
                    readInlineArrayField(field, obj, seg, offset, nativeToJava, readStrings, pending);
                } else {
                    FieldLayout nested = layoutOf(fType);
                    Object child = field.get(obj);
                    if (child == null) {
                        try {
                            child = fType.getDeclaredConstructor().newInstance();
                            field.set(obj, child);
                        } catch (ReflectiveOperationException e) {
                            continue;
                        }
                    }
                    readFields(child, getDeclaredFields(fType), nested.offsets(),
                            seg.asSlice(offset, nested.totalSize()), nativeToJava, readStrings, pending);
                }
                continue;
            }

            if (fType == int.class)
                field.set(obj, seg.get(ValueLayout.JAVA_INT, offset));
            else if (fType == long.class)
                field.set(obj, seg.get(ValueLayout.JAVA_LONG, offset));
            else if (fType == float.class)
                field.set(obj, seg.get(ValueLayout.JAVA_FLOAT, offset));
            else if (fType == double.class)
                field.set(obj, seg.get(ValueLayout.JAVA_DOUBLE, offset));
            else if (fType == short.class)
                field.set(obj, seg.get(ValueLayout.JAVA_SHORT, offset));
            else if (fType == byte.class)
                field.set(obj, seg.get(ValueLayout.JAVA_BYTE, offset));
            else if (fType == boolean.class)
                field.set(obj, seg.get(ValueLayout.JAVA_BOOLEAN, offset));
            else if (fType == String.class) {
                if (readStrings) {
                    MemorySegment text = seg.get(ValueLayout.ADDRESS, offset);
                    field.set(obj, text.address() == 0 ? null
                            : readNullTerminatedString(text.reinterpret(MAX_STRING_BYTES)));
                }
            } else if (!fType.isPrimitive() && !fType.isArray()
                    && !Collection.class.isAssignableFrom(fType)) {
                MemorySegment childSeg = seg.get(ValueLayout.ADDRESS, offset);
                if (childSeg.address() == 0) {
                    field.set(obj, null);
                } else {
                    Object linked = nativeToJava.get(childSeg.address());
                    if (linked == null && fType.isAnnotationPresent(MiteStruct.class)) {
                        linked = pending != null
                                ? enqueueUnmarshalled(childSeg, fType, nativeToJava, pending)
                                : unmarshalCustomObject(childSeg, fType, nativeToJava);
                    }
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
        Object existing = nativeToJava.get(seg.address());
        if (existing != null) return existing;

        Object root = newUnmarshalled(clazz);
        nativeToJava.put(seg.address(), root);

        Deque<PendingStruct> pending = new ArrayDeque<>();
        pending.addLast(new PendingStruct(root, clazz, seg));

        int materialised = 0;
        while (!pending.isEmpty()) {
            if (++materialised > maxUnmarshalledNodes) {
                throw new IllegalStateException(
                        "Unmarshalling stopped after " + maxUnmarshalledNodes + " structures: "
                                + "the graph is corrupt or larger than the configured limit "
                                + "(system property mite.maxUnmarshalledNodes)");
            }
            PendingStruct next = pending.removeFirst();
            FieldLayout layout = layoutOf(next.clazz());
            MemorySegment sized = next.segment().reinterpret(layout.totalSize());
            try {
                readFields(next.object(), getDeclaredFields(next.clazz()), layout.offsets(),
                        sized, nativeToJava, true, pending);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Unmarshal failed for " + next.clazz().getName(), e);
            }
        }
        return root;
    }

    /** A structure waiting to be read, held by the unmarshalling queue. */
    private record PendingStruct(Object object, Class<?> clazz, MemorySegment segment) {
    }

    /** Creates an empty instance of a {@code @MiteStruct} class. */
    private Object newUnmarshalled(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unmarshal failed for " + clazz.getName()
                    + ": a no-argument constructor is required", e);
        }
    }

    /** Registers a C++-created structure and queues it instead of recursing. */
    private Object enqueueUnmarshalled(MemorySegment seg, Class<?> clazz,
                                       Map<Long, Object> nativeToJava,
                                       Deque<PendingStruct> pending) {
        Object obj = newUnmarshalled(clazz);
        nativeToJava.put(seg.address(), obj);
        pending.addLast(new PendingStruct(obj, clazz, seg));
        return obj;
    }

    /**
     * Reads a null-terminated C string from native memory into a Java {@link String}.
     *
     * @param seg the native memory segment containing the string
     * @return the decoded UTF-8 string
     * @throws IllegalArgumentException if no terminator is found within {@link #MAX_STRING_BYTES}
     */
    String readNullTerminatedString(MemorySegment seg) {
        long maxLen = seg.byteSize() == 0 ? MAX_STRING_BYTES : Math.min(seg.byteSize(), MAX_STRING_BYTES);
        MemorySegment safe = seg.byteSize() > 0 ? seg : seg.reinterpret(MAX_STRING_BYTES);
        long len = 0;
        while (len < maxLen && safe.get(ValueLayout.JAVA_BYTE, len) != 0) {
            len++;
        }
        if (len == maxLen) {
            throw new IllegalArgumentException(
                    "Refusing to read a native string: no terminator within " + maxLen
                            + " bytes, the value would be silently truncated");
        }
        return new String(safe.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Scan limit for strings of unknown length: a runaway guard, not a truncation limit. */
    private static final long MAX_STRING_BYTES = 64L * 1024 * 1024;

    /** Reads a string from a buffer allocated here, bounded by its exact size. */
    private static String readStringInAllocatedBuffer(MemorySegment buffer) {
        long size = buffer.byteSize();
        long len = 0;
        while (len < size && buffer.get(ValueLayout.JAVA_BYTE, len) != 0) {
            len++;
        }
        return new String(buffer.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE),
                java.nio.charset.StandardCharsets.UTF_8);
    }

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
     * @return the computed (or cached) field layout
     */
    private FieldLayout layoutOf(Class<?> clazz) {
        FieldLayout cached = layoutCache.get(clazz);
        if (cached != null) return cached;

        Set<Class<?>> inProgress = layoutsInProgress.get();
        if (!inProgress.add(clazz)) {
            throw new IllegalArgumentException(
                    "Cyclic @MiteInline containment: " + clazz.getName()
                            + " contains itself by value, which cannot be represented as a C++ struct");
        }
        try {
            FieldLayout computed = computeLayout(getDeclaredFields(clazz));
            FieldLayout existing = layoutCache.putIfAbsent(clazz, computed);
            return existing != null ? existing : computed;
        } finally {
            inProgress.remove(clazz);
            if (inProgress.isEmpty()) {
                layoutsInProgress.remove();
            }
        }
    }

    /**
     * Computes offsets, total size and alignment for the given fields, following
     * C struct rules. {@code @MiteInline} fields contribute their own size and
     * alignment instead of a pointer.
     *
     * @param fields the fields to lay out, in declaration order
     * @return the computed layout
     * @throws IllegalArgumentException if a field cannot be represented natively
     */
    private FieldLayout computeLayout(Field[] fields) {
        Map<Field, Long> offsets = new HashMap<>();
        long offset = 0;
        long maxAlign = 1;

        for (Field field : fields) {
            validateField(field);

            long size;
            long align;
            if (isInline(field)) {
                Class<?> inlineType = field.getType();
                if (inlineType.isArray()) {
                    Class<?> element = inlineType.getComponentType();
                    if (element.isAnnotationPresent(MiteStruct.class)) {
                        FieldLayout nested = layoutOf(element);
                        size = nested.totalSize() * inlineLength(field);
                        align = nested.maxAlignment();
                    } else {
                        size = fieldSize(element) * inlineLength(field);
                        align = fieldAlignment(element);
                    }
                } else {
                    FieldLayout nested = layoutOf(inlineType);
                    size = nested.totalSize();
                    align = nested.maxAlignment();
                }
            } else {
                size = fieldSize(field.getType());
                align = fieldAlignment(field.getType());
            }

            if (align > maxAlign) maxAlign = align;
            offset = (offset + align - 1) & -align;
            offsets.put(field, offset);
            offset += size;
        }

        long total = (offset + maxAlign - 1) & -maxAlign;
        if (total == 0) total = 1;
        return new FieldLayout(offsets, total, maxAlign);
    }

    /** Whether the field is stored by value inside its parent structure. */
    private boolean isInline(Field field) {
        return field.isAnnotationPresent(MiteInline.class);
    }

    /** Element count declared on an inline array field. */
    private int inlineLength(Field field) {
        int length = field.getAnnotation(MiteInline.class).length();
        if (length <= 0) {
            throw new IllegalArgumentException(
                    "@MiteInline on an array field requires the element count: "
                            + field.getDeclaringClass().getSimpleName() + "." + field.getName()
                            + " - write @MiteInline(length = N) so it matches the C++ array size");
        }
        return length;
    }

    /** Layout used for bulk-copying an inline array of the given primitive type. */
    private static ValueLayout primitiveLayout(Class<?> type) {
        if (type == float.class) return ValueLayout.JAVA_FLOAT;
        if (type == int.class) return ValueLayout.JAVA_INT;
        if (type == long.class) return ValueLayout.JAVA_LONG;
        if (type == double.class) return ValueLayout.JAVA_DOUBLE;
        if (type == short.class) return ValueLayout.JAVA_SHORT;
        if (type == byte.class) return ValueLayout.JAVA_BYTE;
        throw new IllegalArgumentException("Unsupported inline array element type: " + type.getName());
    }

    /** Writes a primitive inline array. {@code boolean[]} is copied byte by byte. */
    private void writePrimitiveInlineArray(Object array, Class<?> element, MemorySegment seg,
                                           long offset, int length) {
        if (element == boolean.class) {
            boolean[] bits = (boolean[]) array;
            for (int i = 0; i < length; i++) {
                seg.set(ValueLayout.JAVA_BYTE, offset + i, (byte) (bits[i] ? 1 : 0));
            }
            return;
        }
        MemorySegment.copy(array, 0, seg, primitiveLayout(element), offset, length);
    }

    /** Reads a primitive inline array back. {@code boolean[]} is copied byte by byte. */
    private void readPrimitiveInlineArray(Object array, Class<?> element, MemorySegment seg,
                                          long offset, int length) {
        if (element == boolean.class) {
            boolean[] bits = (boolean[]) array;
            for (int i = 0; i < length; i++) {
                bits[i] = seg.get(ValueLayout.JAVA_BYTE, offset + i) != 0;
            }
            return;
        }
        MemorySegment.copy(seg, primitiveLayout(element), offset, array, 0, length);
    }

    /**
     * Writes an inline array field: primitives by bulk copy, structures element
     * by element. Registers a copy-back task for the field.
     *
     * @param field the inline array field
     * @param value the Java array, which must not be {@code null}
     * @throws Exception if a field cannot be read via reflection
     */
    private void writeInlineArrayField(Field field, Object value, MemorySegment seg, long offset,
                                       List<Runnable> copyBackTasks, Arena arena,
                                       Map<Object, MemorySegment> seen,
                                       Map<Long, Object> nativeToJava) throws Exception {
        Class<?> element = field.getType().getComponentType();
        int length = inlineLength(field);
        String where = field.getDeclaringClass().getSimpleName() + "." + field.getName();

        if (value == null) {
            throw new IllegalArgumentException(
                    "Inline array field cannot be null, its storage is part of the structure: " + where);
        }

        int actual = java.lang.reflect.Array.getLength(value);
        if (actual != length) {
            throw new IllegalArgumentException(
                    "Inline array length mismatch for " + where + ": declared " + length
                            + ", got " + actual + " (the count must match the C++ array size)");
        }

        if (element.isAnnotationPresent(MiteStruct.class)) {
            FieldLayout nested = layoutOf(element);
            long elementSize = nested.totalSize();

            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(value, i);
                MemorySegment slot = seg.asSlice(offset + (long) i * elementSize, elementSize);
                if (item == null) {
                    slot.fill((byte) 0);
                } else {
                    writeFields(item, getDeclaredFields(element), nested.offsets(), slot,
                            arena, copyBackTasks, seen, nativeToJava);
                }
            }

            copyBackTasks.add(() -> {
                for (int i = 0; i < length; i++) {
                    try {
                        Object item = java.lang.reflect.Array.get(value, i);
                        if (item == null) {
                            item = element.getDeclaredConstructor().newInstance();
                            java.lang.reflect.Array.set(value, i, item);
                        }
                        readScalarFields(item, getDeclaredFields(element), nested.offsets(),
                                seg.asSlice(offset + (long) i * elementSize, elementSize), nativeToJava);
                    } catch (Exception e) {
                        throw new RuntimeException("Copy-back failed for inline array "
                                + where + "[" + i + "]", e);
                    }
                }
            });
            return;
        }

        writePrimitiveInlineArray(value, element, seg, offset, length);
        copyBackTasks.add(() -> readPrimitiveInlineArray(value, element, seg, offset, length));
    }

    /** Reads an inline array field back, creating the array when it is missing. */
    private void readInlineArrayField(Field field, Object owner, MemorySegment seg, long offset,
                                      Map<Long, Object> nativeToJava, boolean readStrings,
                                      Deque<PendingStruct> pending) throws Exception {
        Class<?> element = field.getType().getComponentType();
        int length = inlineLength(field);

        Object array = field.get(owner);
        if (array == null || java.lang.reflect.Array.getLength(array) != length) {
            array = java.lang.reflect.Array.newInstance(element, length);
            field.set(owner, array);
        }

        if (element.isAnnotationPresent(MiteStruct.class)) {
            FieldLayout nested = layoutOf(element);
            long elementSize = nested.totalSize();
            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(array, i);
                if (item == null) {
                    item = element.getDeclaredConstructor().newInstance();
                    java.lang.reflect.Array.set(array, i, item);
                }
                readFields(item, getDeclaredFields(element), nested.offsets(),
                        seg.asSlice(offset + (long) i * elementSize, elementSize), nativeToJava,
                        readStrings, pending);
            }
            return;
        }

        readPrimitiveInlineArray(array, element, seg, offset, length);
    }

    /**
     * Rejects field types that have no native representation.
     *
     * @param field the field to validate
     * @throws IllegalArgumentException if the field type cannot be marshalled
     */
    private void validateField(Field field) {
        Class<?> type = field.getType();
        String where = field.getDeclaringClass().getSimpleName() + "." + field.getName();

        if (isInline(field)) {
            if (type.isArray()) {
                Class<?> element = type.getComponentType();
                if (element == char.class) {
                    throw new IllegalArgumentException(
                            "char[] is not supported as an inline array (Java chars are 2 bytes, C++ chars are 1): "
                                    + where + "; use byte[]");
                }
                if (!element.isPrimitive() && !element.isAnnotationPresent(MiteStruct.class)) {
                    throw new IllegalArgumentException(
                            "@MiteInline array elements must be primitive or @MiteStruct: "
                                    + where + " : " + element.getName());
                }
                inlineLength(field);
                return;
            }
            if (!type.isAnnotationPresent(MiteStruct.class)) {
                throw new IllegalArgumentException(
                        "@MiteInline requires the field type to be annotated with @MiteStruct: "
                                + where + " : " + type.getName());
            }
            return;
        }

        if (type.isPrimitive()) {
            if (type == char.class) {
                throw new IllegalArgumentException(
                        "Java 'char' is 2 bytes and has no C++ equivalent, so " + where
                                + " cannot be marshalled: use 'byte' for a C++ char or 'short' for a 16-bit value");
            }
            return;
        }

        if (type == String.class) return;

        if (type.isArray()) {
            if (type == char[].class) {
                throw new IllegalArgumentException(
                        "char[] is not supported (Java chars are 2 bytes, C++ chars are 1): " + where
                                + "; use byte[]");
            }
            if (type == float[].class || type == int[].class || type == long[].class
                    || type == double[].class || type == byte[].class || type == short[].class
                    || type == boolean[].class || type == Object[].class) {
                return;
            }
            if (type.getComponentType().isAnnotationPresent(MiteStruct.class)) {
                return;
            }
            throw new IllegalArgumentException(
                    "Unsupported array field type: " + where + " : " + type.getName()
                            + " (elements must be a primitive or @MiteStruct)");
        }

        if (Collection.class.isAssignableFrom(type)) return;
        if (type.isAnnotationPresent(MiteStruct.class)) return;

        throw new IllegalArgumentException(
                "Unsupported @MiteStruct field: " + where + " : " + type.getName()
                        + ". Supported: primitives, String, primitive arrays, Collection, and @MiteStruct "
                        + "(pointer by default, by value with @MiteInline)");
    }

    /**
     * Returns the native size in bytes for a field's type.
     *
     * @param fType the field's declared type
     * @return 4 for int/float, 8 for long/double, 2 for short, 1 for
     * byte/boolean, or {@link #pointerSize} for reference types
     */
    private long fieldSize(Class<?> fType) {
        if (fType == int.class
                || fType == float.class) return 4;
        if (fType == long.class
                || fType == double.class) return 8;
        if (fType == short.class) return 2;
        if (fType == boolean.class
                || fType == byte.class) return 1;
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
            if (!f.isSynthetic() && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                result.add(f);
            }
        }
        Field[] computed = result.toArray(new Field[0]);
        fieldsCache.putIfAbsent(clazz, computed);
        return fieldsCache.get(clazz);
    }

}