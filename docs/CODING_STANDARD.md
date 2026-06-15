# CyberTrail Coding Standard
Version: 1.0

---

## 1. Rust Code Style
- **Format**: All code must be formatted using `rustfmt`.
- **Lints**: `clippy` must pass without any warnings. Use `#[deny(clippy::all)]` per crate.
- **Naming**: 
  - `snake_case` for variables, functions, and modules.
  - `PascalCase` for structs, enums, and traits.
  - `SCREAMING_SNAKE_CASE` for constants.
- **Mutability**: Keep variables immutable by default. Only use `mut` when strictly necessary.
- **Visibility**: Keep items private by default. Only use `pub` when exposing necessary APIs.

---

## 2. Error Handling
- **Result Types**: Always use `Result<T, E>`.
- **Error Crates**: Use `thiserror` for library error definitions.
- **Forbidden**: `unwrap()`, `expect()`, `panic!()` in production code. Use `?` operator to propagate errors.
- **Granularity**: Errors should be strongly typed and domain-specific (e.g., `GpsError::LocationServiceDisabled`).

---

## 3. Testing Standard
- **Unit Tests**: Place within the same module using `#[cfg(test)]`. 
- **Coverage**: Target 80%+ test coverage for domain and application logic.
- **Structure**: Use Arrange-Act-Assert methodology.
- **Mocks**: Mock repository interfaces when testing `application` layer Use Cases.

---

## 4. FFI (Foreign Function Interface)
- **Boundary**: All cross-language calls MUST pass through the `ffi` crate.
- **Type Safety**: Use `#[repr(C)]` for structs shared across FFI.
- **Memory Safety**: Handle raw pointers explicitly. Document ownership and drop semantics for memory passed to Android. Always catch Rust panics at the FFI boundary (`catch_unwind`) to prevent JVM crashes.

---

## 5. Android / Kotlin Specs
- **UI Framework**: Jetpack Compose exclusively.
- **Architecture**: MVVM with distinct StateFlows. 
- **JNI Binding**: Expose methods using `external fun` inside Kotlin `object` wrappers.
- **Coroutines**: Execute FFI calls on `Dispatchers.IO` when blocking, though prefer Rust executing and returning via callbacks for continuous data (e.g. tracking).
