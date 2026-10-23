# semi-mite

**semi-mite** is a Java framework for calling C++ code from inside a running JVM - no hand-written JNI, no boilerplate glue code. You write `.cpp` functions and a matching Java interface; the library compiles, links, and calls them for you, with support for hot reloading while the program is running.

Built on top of Project Panama (the Foreign Function & Memory API).

## Why the name

Semi-Mite runs compiled C++ code directly inside the Java process, creating a close connection between two different ecosystems. The name comes from the image of a mite living within a host - a small entity operating inside a larger environment.

However, C++ is not just a passive component. It provides the low-level performance and native power that drive the system. This is why it is not simply a "mite": Semi-Mite represents a partnership between Java and C++, combining the strengths of both worlds.

## Why bother mixing C++ into Java at all

Java is productive, safe, and has a huge ecosystem - but it's not always the fastest tool for CPU-heavy work: tight numeric loops, SIMD-friendly code, existing native libraries you don't want to rewrite, or code that simply already exists in C++ and works. Dropping down to native code lets you keep the JVM for orchestration, business logic, and tooling, while offloading the hot path to C++.

The usual way to do this is JNI - and JNI is notoriously painful: manual header generation, hand-written glue code on both sides, separate build steps, and a lot of ceremony for even a simple function call.

## Why not just use raw Panama

The Foreign Function & Memory API (Panama) is the modern, JNI-free way to call native code from Java - and semi-mite is built entirely on top of it, not instead of it. But using Panama directly still means you write it all by hand every time:

- Manually build a `MemoryLayout` and `FunctionDescriptor` for every native function
- Manually look up symbols with `SymbolLookup` and build `MethodHandle`s
- Manually marshal every argument and return value between Java and native memory
- Compile your native code yourself and manage the resulting library file
- Rebuild and rewire everything by hand every time the native code changes

That's a lot of correct, repetitive low-level code to write and maintain for what is conceptually a single function call. semi-mite automates all of it:

- Write a normal `.cpp` file, mark the functions you want to expose with `// @mite`
- Write a normal Java interface, mark it with `@MiteClient`
- semi-mite finds the functions, compiles them, builds the layouts and handles, and wires up a working proxy - automatically, with correct types and safe memory handling
- Edit the `.cpp` file while the program is running and the change is picked up and hot-reloaded, with no restart and no crashes on in-flight calls

In short: Panama is the engine, semi-mite is the car. You get the performance and control of native code without hand-rolling the plumbing on every project.

## How it works

Write a C++ function and mark it:

```cpp
extern "C" {

// @mite
int slow_call(int x) {
    return x * 2;
}

}
```

Write a matching Java interface:

```java
@MiteClient
public interface MathClient {
    int slow_call(int x);
}
```

Scan and use it:

```java
try (MiteContext ctx = Mite.scan("org.example") // TryWithResources or memory leak!
        .withHotReload()
        .start()) {

MathClient client = ctx.get(MathClient.class);
    System.out.println(client.slow_call(41)); // 82
        }
```

Under the hood, semi-mite:

- scans the given base package for interfaces annotated `@MiteClient`
- finds `.cpp` files and functions marked `// @mite`
- compiles them into a `.so` / `.dll` / `.dylib` via `g++` or `clang++`
- skips recompilation entirely if the source contents, compiler flags, and resolved compiler haven't changed since the last build (see [Compilation and caching](#compilation-and-caching))
- links the functions to your Java interface methods using the FFM API (`Linker`, `MethodHandle`, `FunctionDescriptor`)
- builds a proxy object that actually runs the compiled C++ code when you call the method
- when hot reload is enabled, watches the `.cpp` files and recompiles/relinks them on the fly while the program keeps running, without breaking calls already in flight

When a pre-built library is supplied with `Mite.use(...)`, the compile step is skipped and the signatures are derived from the `@MiteClient` interfaces instead - see [Using a pre-built library](#using-a-pre-built-library).

## Passing data across the boundary

At the FFM level, every native call only ever deals with primitives and pointers (`ADDRESS`) - semi-mite's marshalling layer sits on top of that and handles the following on your behalf:

- **Primitives** - `char`, `short`, `int`, `long`, `long long`, `float`, `double`, `bool`, and their `unsigned` variants, mapped to the correct Java type and byte layout automatically. `long` is mapped according to the actual platform ABI (4 bytes on Windows/MSVC, 8 bytes on Linux/macOS with GCC/Clang), so the same Java code behaves correctly on every OS. Unsigned values are delivered in the signed carrier of the same width (`int` for `uint32_t`, `byte` for `uint8_t`); read them with `Integer.toUnsignedLong` / `Byte.toUnsignedInt`, because widening the carrier would change the ABI.
- **Strings** - plain `char*`.
- **Off-heap arrays** - via `MiteArray<T>` (`memory` package), a small `AutoCloseable` wrapper around a native memory segment. Supports `int`, `float`, `double`, `long`, `byte`, and immutable arrays of `String`. Pass `myArray.segment()` into a native call, or read/write elements with `get`/`set`. Always close it (or use try-with-resources) to free the native memory.
- **Structs and object graphs** - via `@MiteStruct` on a Java class. This is more than flat structs: the marshaller supports nested structures, collections of structs, and **cyclic references** (e.g. graphs and trees with back-pointers) - it tracks already-marshalled objects by identity so a cycle doesn't recurse forever or get duplicated, and correctly reconstructs the same shared/cyclic shape on the way back from native code to Java.

```cpp
struct Node {
    int value;
    Node* next; // may point back into the same graph
};
```

```java
@MiteStruct
public class Node {
    public int value;
    public Node next;
}
```

### Nested structs: by pointer or by value

By default a nested `@MiteStruct` field is marshalled as a **pointer**, so it matches a `T*` member in C++:

```cpp
struct Particle {
    int id;
    float mass;
    Vec3* pos;   // Java: public Vec3 pos;
};
```

If the C++ struct stores that member **by value**, annotate the Java field with `@MiteInline`. The nested struct is then laid out inline, and its size and alignment become part of the parent:

```cpp
struct Particle {
    int id;
    float mass;
    Vec3 pos;    // sizeof(Particle) == 20
};
```

```java
@MiteStruct
public class Particle {
    public int id;
    public float mass;
    @MiteInline
    public Vec3 pos;
}
```

The same annotation makes **array storage** part of the structure, which is how a fixed-size C++ array or `std::array` is represented. The element count is required, because it is part of the structure's size and cannot be derived from the Java type:

```cpp
struct Samples {
    int id;
    double values[4];   // sizeof = 40
};

struct Mesh {
    Vec3 points[3];     // sizeof = 36
};
```

```java
@MiteStruct
public class Samples {
    public int id;
    @MiteInline(length = 4)
    public double[] values;
}

@MiteStruct
public class Mesh {
    @MiteInline(length = 3)
    public Vec3[] points;
}
```

Struct elements are copied contiguously with a stride of `sizeof(element)`, exactly as C++ lays out an array - so `std::array<Vec3, 3>` and `Vec3 points[3]` are both expressible. The count is fixed by the annotation: passing an array of a different length is rejected instead of silently copying a partial buffer. Without `@MiteInline` an array field is a `T*`, as before.

Field types that have no well-defined native representation are rejected when the layout is computed, with the offending class and field named in the message. This covers Java `char` and `char[]` (2-byte chars, no C++ equivalent), `enum` fields, and arbitrary object types.

One rule that is easy to miss: a Java `long` field always occupies 8 bytes, so it corresponds to a C++ `long long` (or `int64_t`), **not** to `long` - a C++ `long` is 4 bytes on Windows. The field-size table is fixed and does not follow the platform ABI. Likewise the layout never requires an alignment greater than 8 bytes, so a struct containing `__int128`, `long double` or a SIMD type (alignment 16+) is not representable.

## Compilation and caching

By default semi-mite compiles your `.cpp` sources into a `.dll`/`.so`/`.dylib` via `g++`/`clang++`; loading an already-built library instead is described in [Using a pre-built library](#using-a-pre-built-library). To avoid recompiling on every JVM startup, it computes a hash over:

- the contents of every `.cpp` file being compiled,
- the resolved compiler path, and
- the active compiler flags,

and skips the actual compiler invocation if nothing in that set has changed since the last successful build, reusing the previously compiled library from the cache directory instead.

Note: this cache only tracks the `.cpp` files passed in directly. If a `.cpp` file `#include`s a header that changes but the `.cpp` file itself doesn't, the cache will not detect that and will keep serving the stale library - touch the `.cpp` file (or clear the cache directory) after changing a shared header.

### Linking against external native libraries (e.g. OpenCV)

Because `// @mite` functions are just C++ code that semi-mite compiles for you, you can link against any external native library the same way you would with a normal `g++`/`clang++` invocation - by adding the right flags:

```java
Mite.scan("org.example")
    .withFlag("-I/path/to/opencv/include")
    .withFlag("-L/path/to/opencv/lib")
    .withFlag("-lopencv_core")
    .withFlag("-lopencv_imgproc")
    .start();
```

Your `.cpp` file can then `#include` the library's headers and call into it directly inside a `// @mite`-marked function, as long as the function's own signature only uses the types listed above. The external shared library itself (`opencv_world.dll`, `libopencv_core.so`, etc.) still needs to be resolvable at runtime by the OS loader (`PATH` on Windows, `LD_LIBRARY_PATH` on Linux/macOS) - semi-mite doesn't manage that for you.

### Using a pre-built library

If the native library is already built - by your own build system, by a vendor, or on another machine - point semi-mite at it with `Mite.use(...)` instead of `Mite.scan(...)`:

```java
try (MiteContext context = Mite.use("org.example", Path.of("lib/native.dll")).start()) {
    MathClient client = context.get(MathClient.class);
    System.out.println(client.slow_call(41));
}
```

No `.cpp` file, no compiler and no cache directory are involved. The native signatures are derived from the `@MiteClient` interface itself: primitives use the usual mapping, reference types (strings, arrays, `@MiteStruct`) are passed as pointers, and default/static interface methods are skipped because they have no native symbol.

The trade-off is that nothing checks the interface against the library at build time - a mismatch shows up as wrong values or a crash on the call, not as a compile error. Use this mode when the binary is out of your control; use `Mite.scan` when you have the sources.

`Mite.use(...)` returns the same `MiteBuilder`: `withPointerSize(int)` still applies, compiler-related options are ignored, and hot reload is unavailable because there is nothing to rebuild.

## Configuration

`Mite.scan(basePackage)` returns a `MiteBuilder` that allows you to customise how the native runtime is started before calling `start()`.

The following options are available (defaults shown):

| Method                       | Default                     | Description                                                                                                                                                                  |
| ---------------------------- | --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `withConfig(CompilerConfig)` | `CompilerConfig.defaults()` | Replaces the entire compiler configuration with a custom one.                                                                                                                |
| `withScriptsDir(Path)`       | `cppScripts`                | Directory containing the native `.cpp` source files.                                                                                                                         |
| `withCacheDir(Path)`         | `.mite-cache`               | Directory used to store compiled native libraries and the compilation cache.                                                                                                 |
| `withCompiler(String)`       | auto-detected               | Specifies an explicit compiler executable (for example `g++`, `clang++`, or an absolute path). If omitted, Semi-Mite searches for a supported compiler on the system `PATH`. |
| `withFlags(List<String>)`    | `["-O2"]`                   | Replaces the entire list of compiler flags passed to the native compiler.                                                                                                    |
| `withFlag(String)`           | —                           | Appends a single compiler flag while preserving the existing ones.                                                                                                           |
| `withPointerSize(int)`       | `8`                         | Sets the native pointer size in bytes (`4` or `8`). It must match the JVM's address size, so a 64-bit JVM only accepts `8`.                                                  |
| `withPrebuiltLibrary(Path)`  | -                           | Loads an already-built `.dll`/`.so`/`.dylib` instead of compiling sources. Shorthand: `Mite.use(pkg, path)`. See [Using a pre-built library](#using-a-pre-built-library).    |
| `withHotReload()`            | disabled                    | Watches the native source directory for changes and automatically recompiles and reloads the native library at runtime.                                                      |
| `start()`                    | —                           | Compiles (if necessary), loads the native library, scans the specified package, and returns a ready-to-use `MiteContext`.                                                    |

### Example

```java
MiteContext context = Mite.scan("com.example")
        .withScriptsDir(Path.of("native"))
        .withCacheDir(Path.of(".cache"))
        .withFlag("-O3")
        .withFlag("-march=native")
        .withHotReload()
        .start();
```

`MiteBuilder` is immutable from the user's perspective: every configuration method updates the internal compiler configuration and returns the same builder instance, allowing fluent method chaining.

## Easy to implement

[Maven Central ](https://central.sonatype.com/artifact/io.github.krosta12/semi-mite)

## Examples

A separate repository with runnable examples (structs, arrays, hot reload, error handling, etc.) is available at:

[semi-mite-examples](https://github.com/krosta12/semi-mite-examples)

## Features

- Call C++ functions from Java without writing any JNI glue code
- Automatic compilation of `.cpp` sources into a native library, with hash-based caching to skip unnecessary rebuilds
- Load an already-built `.dll`/`.so`/`.dylib` with `Mite.use(...)` - no compiler and no `.cpp` sources needed
- Hot reload: edit the C++ code, changes are picked up without restarting the JVM, without breaking in-flight calls
- Automatic type marshaling between Java and C++: primitives, strings, off-heap arrays (`MiteArray<T>`), and structs/object graphs via `@MiteStruct` (including nested structures and cyclic references)
- Thread-safe library swapping during hot reload
- Link against external native libraries (e.g. OpenCV) by passing standard compiler/linker flags

## Testing

The test suite includes real integration tests that compile an actual `.cpp` file, link it, and invoke the resulting native function through the full `MiteBootstrap` pipeline (these are skipped automatically in environments without a working `g++`/`clang++`). CI runs the full test suite on both Linux and Windows.

## Project structure

| Package      | Role                                                                |
| ------------ | ------------------------------------------------------------------- |
| `annotation` | `@MiteStruct` and `@MiteInline` - the native structure annotations   |
| `client`     | `@MiteClient` and the scanner that discovers the interfaces          |
| `parser`     | Parses `.cpp` files, finds functions marked `// @mite`               |
| `compiler`   | Locates and invokes `g++`/`clang++`, with hash-based build caching   |
| `linker`     | Links compiled or pre-built libraries, maps types, marshals, proxies |
| `core`       | Orchestrates the whole lifecycle (startup, hot reload, shutdown)     |
| `memory`     | `MiteArray<T>`, a convenience wrapper for off-heap memory            |

## Requirements

- JDK 22+ (Foreign Function & Memory API)
- `g++` or `clang++` on `PATH` - only for `Mite.scan(...)`; `Mite.use(...)` needs no compiler

## Writing your own `.cpp` function

- Put `// @mite` on its own line, directly above the function (`/// @mite` and `//! @mite` work too, and a trailing `// comment` is allowed; anything else after the marker is reported and the function is not exported)
- Keep the signature simple: `type name(params) {`, no `template`, `constexpr`, or `[[nodiscard]]`
- Supported types: `void`, `char`, `short`, `int`, `long`, `long long`, `float`, `double`, `bool` (and `unsigned` variants), pointers, and Java classes annotated with `@MiteStruct` (including nested structures and cyclic references, e.g. graphs and trees)
- Wrap exposed functions in `extern "C" { ... }` so the compiler doesn't mangle their names
- Function signatures are parsed with a lightweight, string-based parser rather than a full C++ parser - keep the exposed signature itself simple even if the function body does complex work internally

## Known limitations

- With `Mite.use(...)` the native signatures are inferred from the Java interfaces, so a mismatch is not caught until the call, and hot reload is unavailable
- The build cache hashes `.cpp` source files directly, but not headers they transitively `#include` - see [Compilation and caching](#compilation-and-caching)
- The public API (`MiteBuilder`, `CompilerConfig`, etc.) is still in devel and may change between releases
- Java `char` and `char[]` have no C++ equivalent (2-byte chars) and are rejected; use `byte[]` for `char*`, `String` for `const char*`, or `short[]` for `wchar_t*`/`uint16_t*`
- `String` fields are read back after the call: an in-place overwrite is read from the exact buffer the framework allocated (no length cap, so long strings survive), and a pointer replaced by native code is followed and read with a 64 KiB cap. A field that was `null` on the way in is read back too, so a structure handed over empty and filled in C++ works as an out-parameter. `String` fields of a structure returned by native code are filled the same way
- Pointer fields written by native code are followed: if C++ stores a newly allocated child object into a structure field, that object is unmarshalled into a Java object during copy-back. The framework cannot validate a foreign address, so if native code stores a dangling or uninitialised pointer the JVM will crash
- Following those pointers requires a public no-argument constructor on every `@MiteStruct` class. If it is missing, copy-back fails with `Unmarshal failed` rather than silently leaving the field untouched
- Unmarshalling walks pointer chains with an explicit work queue, so the size of a structure graph is limited by memory rather than by the JVM stack. Marshalling a deep graph from Java to native code still recurses, so an extremely deep Java-side structure can overflow the stack
- The framework never frees memory it did not allocate. Objects created in C++ stay owned by C++ - reading them into Java does not transfer ownership, so the native side must free them
- Native strings are scanned up to 64 MiB for a terminator; a string with no terminator within that window is rejected with an explicit error instead of being silently truncated. A pointer without any terminator at all can still run past the allocation and crash the JVM - the same risk as calling `strlen` on a broken `char*`
- Unmarshalling materialises at most 1 000 000 structures per run; a corrupt or larger graph fails with an explicit error instead of an `OutOfMemoryError`. Override with the `mite.maxUnmarshalledNodes` system property

## License

MIT
