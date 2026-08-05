# Rustup Manager

A full Rust toolchain manager for Klyx, built directly on top of `rustup`.

## What it does

- Installs `rustup` inside Klyx's built-in Linux environment if it isn't already there.
- Lists, installs, switches, and removes toolchains (stable / beta / nightly / pinned versions).
- Manages components: `rust-analyzer`, `clippy`, `rustfmt`, `rust-src`.
- Manages cross-compilation targets.
- Registers `rust-analyzer` as the language server for `.rs` files automatically, resolved live via `rustup which rust-analyzer` so it always matches whatever toolchain is active.
- Optional background `rustup update` on a configurable interval (daily / weekly / monthly), toggled from the plugin's settings screen.

## Why rustup instead of downloading binaries directly

`rustup` already solves toolchain/target/component compatibility, glibc versioning, and updates. Wrapping it means the plugin doesn't need its own update channel logic, its own download/checksum code, or any assumptions about which libc a downloaded binary needs — `rustup` and Klyx's Linux environment handle that.

## Building

```
./gradlew :app:assembleRelease
```

The bundled `.klyx` file is written to `output/`.
