# semi-mite-spring-boot-starter

[![Java Version](https://img.shields.io/badge/Java-22--26-orange.svg)](https://jdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x%20/%203.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Technology](https://img.shields.io/badge/Engine-Project%20Panama-blue.svg)](https://openjdk.org/projects/panama/)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)

**semi-mite-spring-boot-starter** is a high-level declarative FFI (Foreign Function Interface) framework for Spring Boot applications built on top of modern **Project Panama** (`java.lang.foreign`).

The framework provides a direct bridge between JVM applications and native C++ code without JNI, C wrappers, generated bindings, or manual serialization layers. It allows developers to invoke native functions using ordinary Java interfaces while leveraging zero-copy memory exchange, deep object marshalling, cyclic graph support, and runtime hot-reloading of native sources.

---

# ⚡ Core Features

> **⚠️ Active Development Notice**
>
> semi-mite-spring-boot-starter is currently under active development.
>
> The core architecture is functional and continuously evolving, but APIs, annotations, internal marshalling mechanisms, and runtime behavior may change between releases as new features and optimizations are introduced.
>
> This project is developed by a single maintainer and is an early-stage engineering effort. While it is fully functional and actively tested, some design decisions may evolve as the system matures.
>
> ⚙️ **Testing scope:** At the current stage, testing has been primarily performed on Windows environments. Cross-platform support (Linux/macOS) is planned and may require additional validation and adjustments.
>
> Feedback, bug reports, feature requests, and contributions are welcome.

## 🚀 Zero-Copy Memory Bridge (`MiteArray`)

Transfer large datasets between Java and C++ using direct off-heap memory access.

Native code operates on memory regions allocated outside of the JVM heap, eliminating traditional heap-to-native copying overhead and significantly reducing GC pressure during computation-heavy workloads.

### Benefits

* Direct off-heap memory access
* Reduced allocation overhead
* No intermediate serialization layers
* Optimized for large numerical workloads
* Designed for high-frequency native execution

---

## 🛠 Declarative Native Clients (`@MiteClient`)

Expose native C++ functions as regular Spring-managed Java interfaces.

The framework automatically:

* Compiles native code
* Loads generated shared libraries
* Resolves exported symbols by function name
* Creates dynamic proxy implementations
* Handles Panama linker configuration

Example:

```java
@MiteClient(script = "cppScripts/math.cpp") //path is decorative
public interface MathClient {

    float calculate(float[] values, int size);

}
```

> **Note:** Function resolution is based on method name matching against all functions registered from the `cppScripts` directory. The optional `script` attribute is informational and does not currently restrict resolution to a specific file. Function names across all `.cpp` files in `cppScripts` must therefore be unique.

No JNI code.

No generated bindings.

No manual symbol registration.

---

## 🌳 Recursive Deep Marshalling

Automatically converts complex Java object graphs into continuous native memory structures.

Supported scenarios include:

* Nested DTOs
* Trees
* Linked structures
* Arrays of custom objects
* Collections containing custom objects
* Multi-level object hierarchies

Example:

```java
class Node {

    private String name;

    private List<Node> children;

}
```

The framework recursively traverses the graph and constructs corresponding native structures.

---

## 🔄 Cyclic Graph Support

Traditional recursive traversal fails when object graphs contain cycles.

Example:

```java
class User {

    User manager;

}

class Manager {

    User employee;

}
```

Most serializers eventually trigger:

```text
StackOverflowError
```

semi-mite tracks object identity during marshalling and automatically resolves cyclic references while preserving native address uniqueness.

---

## 🔥 Runtime Hot Reloading

Native source files can be modified while the Spring Boot application remains running.

A background `WatchService` monitors configured C++ sources.

When changes are detected:

1. Source files are re-scanned.
2. Native libraries are rebuilt.
3. Panama bindings are refreshed.
4. Existing Spring beans continue working.

No application restart required.

---

## 🔍 Headerless Function Parsing

No need to manually create Panama `FunctionDescriptor` definitions.

The framework parses C++ source files directly and automatically discovers native functions marked with:

```cpp
// @mite
```

Example:

```cpp
// @mite
float calculate_metrics(const float* coordinates, int totalElements) {
    ...
}
```

Detected signatures are converted into the required Panama descriptors automatically.

---

# 📊 Performance Statistics & Benchmarks

The following benchmarks were executed on a dataset of **20,000,000 float elements**.

All C++ measurements reflect **pure compute time only** — data is pre-loaded into off-heap `MiteArray` before the benchmark loop begins and remains there across iterations. This reflects the intended use case: long-lived off-heap state modified repeatedly by native code, with no per-call copy overhead.

Java measurements reflect the equivalent logic running on JVM heap arrays, timed identically (copy-to-fresh-array is outside the timer).

> **Key insight:** semi-mite does not automatically make everything faster. The JVM's HotSpot JIT is highly competitive on simple loops. The benefit of native C++ grows with computational complexity and with workloads that keep data off-heap across multiple calls.

---

## Benchmark Results

### Scenario 1 — Simple Sum

*Trivial accumulation loop. JIT auto-vectorises this effectively — C++ has no inherent advantage here.*

| Compiler Flags                                                                | Java (ms) | C++ (ms) |   Speedup |
| ----------------------------------------------------------------------------- | --------: | -------: | --------: |
| `-O0`                                                                         |     13.81 |    55.62 |     0.25× |
| `-O2`                                                                         |     13.87 |    14.05 |     0.99× |
| `-O2 -DNDEBUG -flto=auto -pipe`                                               |     13.81 |    14.28 |     0.97× |
| `-O3 -DNDEBUG -flto=auto -march=native -fomit-frame-pointer`                  |     13.76 |    13.95 |     0.99× |
| `-O3 -march=native -flto=auto -DNDEBUG -Ofast -fno-plt -fomit-frame-pointer`  |     13.83 |     3.49 | **3.96×** |
| `-Ofast -march=native -flto=auto -fno-plt -fomit-frame-pointer -DNDEBUG`      |     13.82 |     3.41 | **4.05×** |

**Best Result:** 🚀 **4.05× faster than Java** (requires `-Ofast` with full CPU-specific flags)

---

### Scenario 2 — Explosion Simulation

*Per-element `sqrt` + conditional branch. JIT is competitive; C++ gains an edge with aggressive vectorisation flags.*

| Compiler Flags                                                                | Java (ms) | C++ (ms) |   Speedup |
| ----------------------------------------------------------------------------- | --------: | -------: | --------: |
| `-O0`                                                                         |     62.70 |   172.58 |     0.36× |
| `-O2`                                                                         |     62.70 |    66.37 |     0.94× |
| `-O2 -DNDEBUG -flto=auto -pipe`                                               |     62.54 |    66.24 |     0.94× |
| `-O3 -DNDEBUG -flto=auto -march=native -fomit-frame-pointer`                  |     62.14 |    66.37 |     0.94× |
| `-O3 -march=native -flto=auto -DNDEBUG -Ofast -fno-plt -fomit-frame-pointer`  |     63.04 |    29.95 | **2.10×** |
| `-Ofast -march=native -flto=auto -fno-plt -fomit-frame-pointer -DNDEBUG`      |     61.86 |    29.10 | **2.13×** |

**Best Result:** 🚀 **2.13× faster than Java**

---

### Scenario 3 — Heavy Math

*Per-element `sin`, `cos`, `exp`. JIT cannot optimise `Math.sin/exp` as aggressively as C++ with `-ffast-math` + SVML/libmvec vectorisation. C++ advantage is consistent across all optimisation levels.*

| Compiler Flags                                                                | Java (ms) | C++ (ms) |   Speedup |
| ----------------------------------------------------------------------------- | --------: | -------: | --------: |
| `-O0`                                                                         |    386.46 |   390.74 |     0.99× |
| `-O2`                                                                         |    392.68 |   231.03 |     1.70× |
| `-O2 -DNDEBUG -flto=auto -pipe`                                               |    383.70 |   220.37 |     1.74× |
| `-O3 -DNDEBUG -flto=auto -march=native -fomit-frame-pointer`                  |    387.37 |   225.63 |     1.72× |
| `-O3 -march=native -flto=auto -DNDEBUG -Ofast -fno-plt -fomit-frame-pointer`  |    404.47 |   225.17 | **1.80×** |
| `-Ofast -march=native -flto=auto -fno-plt -fomit-frame-pointer -DNDEBUG`      |    388.71 |   217.04 | **1.79×** |

**Best Result:** 🚀 **1.80× faster than Java** — consistent across all flag configurations

---

### Summary

| Scenario          | Java competitive? | C++ advantage                              |
| ----------------- | :---------------: | ------------------------------------------ |
| Simple sum        | ✅ Yes (at `-O2`)  | Only with `-Ofast` + full CPU flags (4×)   |
| Explosion (sqrt)  | ✅ Yes (at `-O2`)  | With `-Ofast -march=native` (2.13×)        |
| Heavy math        | ❌ No              | Even at `-O2`, C++ wins consistently (1.7×)|

**When to use semi-mite:**
- Computation involves transcendental functions (`sin`, `cos`, `exp`, `log`)
- Data lives off-heap across multiple calls (long-lived `MiteArray`)
- You are integrating existing C++ libraries or algorithms into a Spring application

**When to stay with Java:**
- Simple loops over primitive arrays — JIT handles these very well
- You need the full Java ecosystem (reflection, generics, streams)

---

## Repeated Call Overhead: Heap Array vs MiteArray

Benchmark: `calculate_cosine_similarity` called **20 times** on **5,000,000 floats**.

With a plain Java `float[]`, the framework marshals the entire array from JVM heap to off-heap memory on every single call. With `MiteArray`, memory lives off-heap permanently — C++ accesses it directly with no copy per call.

| Method                              | Total (20 calls) | Avg per call |
| ----------------------------------- | ---------------: | -----------: |
| Heap `float[]` (marshal every call) |         470.2 ms |     23.51 ms |
| `MiteArray` (zero-copy off-heap)    |          72.4 ms |      3.62 ms |

**Result:** 🚀 **6.49× faster per call — 397.8 ms saved over 20 calls**

The advantage compounds with call frequency. In simulation loops, signal processing pipelines, or any workload that calls native code repeatedly on the same dataset, keeping data in a `MiteArray` eliminates the dominant cost entirely.

---

# 🛠 Requirements

## Java

Supported versions:

* Java 22
* Java 23
* Java 24
* Java 25
* Java 26

---

## Native Compiler

One of the following toolchains must be available:

* GCC (`g++`)
* Clang (`clang++`)

The compiler must be accessible through the system `PATH`.

---

## JVM Startup Flags

Project Panama requires native access permissions. Launch your application with:

```bash
--enable-native-access=ALL-UNNAMED
```

> `--enable-preview` is **not required**. The `java.lang.foreign` API has been stable (non-preview) since Java 22.

---

## Configuration

All settings are configured via `application.properties` under the `mite.*` prefix. Everything has a sensible default — no configuration is required to get started.

| Property               | Default         | Description                                                                  |
| ---------------------- | --------------- | ---------------------------------------------------------------------------- |
| `mite.scripts-dir`     | `cppScripts`    | Directory scanned for `.cpp` source files (relative to working directory)    |
| `mite.cache-dir`       | `.mite-cache`   | Directory where compiled `.dll`/`.so` libraries are cached and reused        |
| `mite.compiler-path`   | *(auto-detect)* | Explicit compiler binary path. Leave unset to resolve from system `PATH`     |
| `mite.compiler-flags`  | `-O2`           | Flags passed to the compiler, comma-separated                                |
| `mite.alignment-bytes` | `4`             | Struct field alignment in bytes used during native memory layout             |

---

### Minimal setup (defaults)

Nothing to configure. Drop `.cpp` files into `cppScripts/` and start the application.

```properties
# these are the defaults — only set them if you need different paths
mite.scripts-dir=cppScripts
mite.cache-dir=.mite-cache
```

---

### Production setup with aggressive optimisation

```properties
mite.compiler-flags=-Ofast,-march=native,-flto=auto,-fno-plt,-fomit-frame-pointer,-DNDEBUG
mite.alignment-bytes=32
```

**`mite.compiler-flags`** — flags are passed directly to `g++`/`clang++`. The benchmark section shows the measured impact of different flag combinations across three workload types. For maximum performance, `-Ofast -march=native` is recommended; for safer IEEE-754 compliance, use `-O3 -march=native` without `-ffast-math`.

**`mite.alignment-bytes`** — controls how struct fields are laid out in native memory. The default `4` is correct for most workloads. Set to `32` when your C++ code uses AVX2/AVX-512 intrinsics that require 256-bit aligned memory. Mismatched alignment between Java marshalling and C++ struct expectations will cause incorrect reads without any runtime error — if in doubt, leave it at `4`.

---

### Windows / custom compiler path

On Windows with MinGW, or when multiple compiler versions are installed, specify the compiler explicitly:

```properties
mite.compiler-path=C:/mingw64/bin/g++.exe
```

On Linux/macOS the compiler is resolved automatically from `PATH` (`g++` preferred, then `clang++`).

---

# 📖 Usage Guide

## 1. Enable Mite Infrastructure

```java
package com.example.semi_mite;

import org.example.client.EnableMiteClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableMiteClients
public class MiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiteApplication.class, args);
    }

}
```

---

## 2. Create a Native Client

```java
package com.example.semi_mite.client;

import org.example.client.MiteClient;
import org.example.memory.MiteArray;
import com.example.semi_mite.dto.CustomDataStructure;

@MiteClient
public interface AnalyticsClient {

    float calculate_metrics(float[] coordinates, int totalElements);

    void process_structures(CustomDataStructure structure, int depth);

    void process_massive_matrix(MiteArray offHeapBuffer, int length);

}
```

---

## 3. Implement Native Functions

```cpp
#include <cmath>

struct CustomDataStructure {

    int id;

    float value;

};

extern "C" {

    // @mite
    float calculate_metrics(const float* coordinates, int totalElements) {

        float sum = 0.0f;

        for (int i = 0; i < totalElements; i++) {
            sum += std::sin(coordinates[i]);
        }

        return sum;

    }

    // @mite
    void process_structures(CustomDataStructure* structure, int depth) {

        if (structure == nullptr) {
            return;
        }

        structure->value *= 2.5f;

    }

}
```

---

# 📝 Logging & Diagnostics

Enable detailed diagnostics using standard Spring Boot logging configuration.

```properties
logging.level.org.example.scanner=DEBUG

logging.level.org.example.parser=TRACE

logging.level.org.example.compiler=DEBUG
```

### Scanner Logs

Provides:

* Compilation commands
* File watcher activity
* Native rebuild events
* Dynamic library refresh events

### Parser Logs

Provides:

* Signature detection
* Type resolution
* Marshalling diagnostics
* Function registration details

---

# 🏗 Internal Architecture

```text
[ Spring Application Context ]
                │
                ▼
[ @MiteClient Proxy Interface ]
                │
                ▼
[ Deep Marshalling Layer ]
                │
                ▼
[ Panama Downcall Linker ]
                │
                ▼
[ Native Shared Library ]
```

---

## A. Signature Parsing Engine

The parser scans configured directories for C++ source files.

Functions marked with:

```cpp
// @mite
```

are automatically registered.

The engine:

* Normalizes pointer declarations
* Resolves primitive types
* Detects custom structures
* Builds Panama FunctionDescriptors
* Registers callable native methods

---

## B. Intelligent Debounced File Watching

Modern IDEs frequently save files in multiple stages.

To avoid reading incomplete source files, semi-mite introduces a stabilization delay before recompilation.

Workflow:

```text
File Change
      ↓
Debounce Delay
      ↓
Source Scan
      ↓
Compilation
      ↓
Library Reload
      ↓
Binding Refresh
```

This prevents race conditions and incomplete parsing.

---

## C. Advanced Runtime Type Matching

The framework supports runtime matching between Java abstractions and native structures.

Example:

```java
String baseCppType = cppType.replace("*", "").trim();

if (javaClassName.equals(baseCppType)) {
        return true;
        }

        if (argClass.isArray()
        && argClass.getComponentType().getSimpleName().equals(baseCppType)) {
        return true;
        }

        if (arg instanceof java.util.Collection) {
        return true;
        }
```

Supported mappings:

| Java       | Native    |
| ---------- | --------- |
| User       | User*     |
| User[]     | User**    |
| List<User> | User**    |
| TreeNode   | TreeNode* |

---

## D. Compiler Configuration

The framework compiles C++ sources using flags configured via `application.properties`.

Default flag: `-O2`

Recommended configuration for production workloads:

```properties
mite.compiler-flags=-O3,-march=native,-flto=auto,-DNDEBUG,-Ofast,-fno-plt,-fomit-frame-pointer
```

These flags enable:

* Link-Time Optimization (LTO)
* CPU-specific instruction generation
* SIMD and AVX utilisation (when available)
* Removal of debug overhead

> Performance gains from aggressive flags are workload-dependent. See the benchmark section for measured impact across different computation profiles.

---

# 📂 Example Repository

Looking for practical examples?

The companion repository **[https://github.com/krosta12/semi-mite-examples](https://github.com/krosta12/semi-mite-examples)** contains:

* Cyclic graph processing
* Deep object trees
* Off-heap array operations
* Physics simulations
* Matrix processing
* Native analytics workloads

---

## 📄 License

This project is licensed under the Apache License 2.0.

See the [LICENSE](./LICENSE) file for details.
