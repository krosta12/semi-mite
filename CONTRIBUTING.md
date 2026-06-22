# Contributing to semi-mite-spring-boot-starter

Thank you for taking the time to contribute to this project. This is an early-stage engineering effort developed by a single maintainer, and any help is highly appreciated.

## How Can You Help?

### Testing and Cross-Platform Validation
Currently, the framework has been primarily tested in Windows environments. You can contribute significantly by:
* Running the starter and examples on Linux or macOS.
* Reporting platform-specific bugs or performance discrepancies.
* Validating multi-threaded behavior and Project Panama memory allocation under high load.

### Reporting Bugs
If you find a bug, please use the Bug Report template provided in the Issues section. Make sure to include your environment details (OS, Java version, compiler version) and Spring Boot console logs.

### Pull Requests
If you want to contribute code:
1. Fork the repository and create your branch from `main`.
2. Ensure your code follows standard Java formatting principles.
3. If you change marshalling logic or low-level Panama API interactions, please provide corresponding tests or benchmark results.
4. Open a Pull Request with a clear description of your changes.
