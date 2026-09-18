# Contributing to Vipo

Thank you for contributing to Vipo! We are committed to building the finest offline, privacy-first AI application on Android.

## Development Workflow

1. Fork the repository and clone your fork.
2. Create a feature branch (`git checkout -b feature/model-quant-enhancement`).
3. Commit your changes with clear, descriptive commit messages.
4. Verify your build passes cleanly (`./gradlew testDebugUnitTest assembleDebug`).
5. Open a Pull Request explaining your changes and motivation.

## Guiding Principles

- **Zero Cloud Leakage:** Never introduce background telemetry, remote AI calls, or cloud-based fallbacks.
- **Performance First:** Memory efficiency and token throughput on edge mobile devices take priority.
- **Clean Architecture:** Keep UI, ViewModel, Repository, Engine, and Room layers modular.
