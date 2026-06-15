# CyberTrail Build Verification Plan
Version: 1.0
Status: Frozen

---

## Objective
Before proceeding to any UI work or Android FFI bridging, the Rust core backend must be systematically verified. We have generated thousands of lines of architectural theory, domain models, and infrastructure implementations. We must prove it compiles and adheres to `strict` Rust standards.

## 1. Rust Toolchain Checks

The CI/CD pipeline and the developer workstation must run the following exact commands from the `/cybertrail` workspace root.

```bash
# 1. Syntax, Types, and Borrow-Checker Validation
cargo check --workspace --all-targets

# 2. Strict Linter Rules
cargo clippy --workspace --all-targets -- -D warnings

# 3. Unit and Integration Test Suite
cargo test --workspace

# 4. Workspace Formatting (Sanity Check)
cargo fmt --all -- --check
```

## 2. Success Criteria Matrix

If the system is correctly implemented, the following modules must report `PASS` with zero compiler errors and zero unaddressed Clippy warnings.

| Module / Crate | Status Check | Target Coverage | Notes |
| :--- | :--- | :--- | :--- |
| `domain` | Cargo Check | Entities, VO, Traits | Foundation layer. Zero dependencies. |
| `application` | Cargo Check | Use Cases, DTOs | Must only depend on Domain. |
| `infrastructure` | Cargo Check | SQLite, WebDAV | Must connect real DB implementations to domain traits without circular references. |
| `crypto` | Cargo Check | AES-GCM, Argon2, Types | Cryptography suite testing. Key derivations must pass logic checks. |
| `tracking/filters`| Cargo Check | Kalman, Limits | The filtering algorithms. |

## 3. Immediate Priorities

1. **Resolve Cargo Workspace Integrity:** Ensure `Cargo.toml` correctly maps `[workspace]` members.
2. **Resolve Missing Dependencies:** Ensure SQLite `rusqlite`, Async `tokio`, error handling `thiserror`, and Crypto `aes-gcm`, `zstd`, `r2d2` are fully resolvable by `cargo`.
3. **Circular Dependencies:** Validate that `infrastructure` depends on `domain`, and `domain` **does not** depend on `infrastructure`.

Any build failure here instantly halts Phase 9 architecture expansions. The code must be corrected and pushed back to green before the map/UI FFI bounds are built.
