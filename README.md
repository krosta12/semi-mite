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

## Passing data across the boundary

At the FFM level, every native call only ever deals with primitives and pointers (`ADDRESS`) - semi-mite's marshalling layer sits on top of that and handles the following on your behalf:

- **Primitives** - `char`, `short`, `int`, `long`, `long long`, `float`, `double`, `bool`, and their `unsigned` variants, mapped to the correct Java type and byte layout automatically. `long` is mapped according to the actual platform ABI (4 bytes on Windows/MSVC, 8 bytes on Linux/macOS with GCC/Clang), so the same Java code behaves correctly on every OS.
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

## Compilation and caching

semi-mite does not bind to a pre-built `.dll`/`.so`/`.dylib` - it always compiles your `.cpp` sources itself via `g++`/`clang++`. To avoid recompiling on every JVM startup, it computes a hash over:

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

There is currently no way to skip compilation and point semi-mite directly at an already-built shared library with no accompanying `.cpp` source - a `.cpp` file (even a thin wrapper) is always required.

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
| `withAlignment(int)`         | `8`                         | Sets the native pointer/alignment size in bytes (typically `4` or `8`). This should match the target platform's ABI.                                                         |
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
- Hot reload: edit the C++ code, changes are picked up without restarting the JVM, without breaking in-flight calls
- Automatic type marshaling between Java and C++: primitives, strings, off-heap arrays (`MiteArray<T>`), and structs/object graphs via `@MiteStruct` (including nested structures and cyclic references)
- Thread-safe library swapping during hot reload
- Link against external native libraries (e.g. OpenCV) by passing standard compiler/linker flags

## Testing

The test suite includes real integration tests that compile an actual `.cpp` file, link it, and invoke the resulting native function through the full `MiteBootstrap` pipeline (these are skipped automatically in environments without a working `g++`/`clang++`). CI runs the full test suite on both Linux and Windows.

## Project structure

| Package      | Role                                                             |
| ------------ | ---------------------------------------------------------------- |
| `annotation` | The `@MiteStruct` and `@MiteClient` annotations                  |
| `client`     | Discovers interfaces marked with `@MiteClient`                   |
| `parser`     | Parses `.cpp` files, finds functions marked `// @mite`           |
| `compiler`   | Locates and invokes `g++`/`clang++`, with hash-based build caching |
| `linker`     | Links the compiled library, marshals types, builds proxy objects |
| `core`       | Orchestrates the whole lifecycle (startup, hot reload, shutdown) |
| `memory`     | `MiteArray<T>`, a convenience wrapper for off-heap memory        |

## Requirements

- JDK 22+ (Foreign Function & Memory API)
- `g++` or `clang++` available on `PATH`

## Writing your own `.cpp` function

- Put `// @mite` on its own line, directly above the function
- Keep the signature simple: `type name(params) {`, no `template`, `constexpr`, or `[[nodiscard]]`
- Supported types: `void`, `char`, `short`, `int`, `long`, `long long`, `float`, `double`, `bool` (and `unsigned` variants), pointers, and Java classes annotated with `@MiteStruct` (including nested structures and cyclic references, e.g. graphs and trees)
- Wrap exposed functions in `extern "C" { ... }` so the compiler doesn't mangle their names
- Function signatures are parsed with a lightweight, string-based parser rather than a full C++ parser - keep the exposed signature itself simple even if the function body does complex work internally

## Known limitations

- No support for pointing at a pre-built shared library without an accompanying `.cpp` source (see [Linking against external native libraries](#linking-against-external-native-libraries-eg-opencv))
- The build cache hashes `.cpp` source files directly, but not headers they transitively `#include` - see [Compilation and caching](#compilation-and-caching)
- The public API (`MiteBuilder`, `CompilerConfig`, etc.) is still in devel and may change between releases

## License

MIT