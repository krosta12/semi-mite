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

- finds `.cpp` files and functions marked `// @mite`
- compiles them into a `.so` / `.dll` / `.dylib` via `g++` or `clang++`
- links the functions to your Java interface methods using the FFM API (`Linker`, `MethodHandle`)
- builds a proxy object that actually runs the compiled C++ code when you call the method
- watches the `.cpp` files and recompiles/relinks them on the fly while the program keeps running

## Examples

A separate repository with runnable examples (structs, arrays, hot reload, error handling, etc.) is available at:

[semi-mite-examples](https://github.com/krosta12/semi-mite-examples)

## Features

- Call C++ functions from Java without writing any JNI glue code
- Automatic compilation of `.cpp` sources into a native library
- Hot reload: edit the C++ code, changes are picked up without restarting the JVM
- Automatic type marshaling between Java and C++ (primitives, strings, arrays, structs via `@MiteStruct`)
- Thread-safe library swapping during hot reload, without breaking calls in flight

## Project structure

| Package      | Role                                                             |
| ------------ | ---------------------------------------------------------------- |
| `annotation` | The `@MiteStruct` and `@MiteClient` annotations                  |
| `client`     | Discovers interfaces marked with `@MiteClient`                   |
| `parser`     | Parses `.cpp` files, finds functions marked `// @mite`           |
| `compiler`   | Locates and invokes `g++`/`clang++`                              |
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

## License

MIT
