# Rustup Manager

[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

A full Rust toolchain manager for [Klyx](https://klyx.dev), built directly on top of `rustup` and the Linux environment that ships inside Klyx.

Install toolchains, switch the default, manage components and cross-compilation targets, and get `rust-analyzer` wired up as the language server for `.rs` files — all from one dashboard inside Klyx.

## Features

- **One-tap setup** — installs `rustup` (with a stable toolchain) inside Klyx's built-in Linux environment if it isn't already there.
- **Toolchains** — install, switch default, update, and remove `stable`, `beta`, `nightly`, or pinned versions; detects available updates via `rustup check`.
- **Components** — add/remove `clippy`, `rustfmt`, and `rust-src`.
- **Targets** — add and remove cross-compilation targets.
- **rust-analyzer** — installs the language server either via `rustup component add` or `apt`, and registers it for `.rs` files automatically. It's spawned by its bare command name so Klyx resolves it against the Linux environment's `PATH`, meaning it always matches whatever toolchain is active — no hard-coded paths to go stale.
- **Background updates** — optional `rustup update` on a configurable interval (daily / weekly / monthly), toggled from the settings screen.
- **Graceful error handling** — detects a missing Linux environment before anything runs, and turns install failures into short, actionable hints (network problems, missing files, missing commands) instead of silent crashes.
- **Material 3 Expressive UI** — animated transitions, rounded cards, and icon-first controls.

## Requirements

- Klyx **4.2.0** or newer.
- Klyx's built-in Linux environment (the PRoot rootfs) must be bootstrapped. If it isn't, the dashboard tells you and opens the terminal so you can set it up.

## Installation

1. Grab the latest `RustupManager.klyx` bundle from the [releases](https://github.com/ulite-Amr/rustupManager-Klyx/releases) page.
2. Install it in Klyx: **Settings → Plugins → Install from file** (or via the bundled plugin installer).
3. Open **Rustup Manager** from the toolbar. First run walks you through installing `rustup`.

## Documentation

- [Usage](docs/usage.md) — requirements, first run, dashboard walkthrough, settings.
- [Language server](docs/language-server.md) — how `rust-analyzer` is managed, install options, and the LSP dashboard.
- [Troubleshooting](docs/troubleshooting.md) — common problems and the hints the plugin shows for them.

## Building

Requires JDK 21 and the Android SDK (the CI workflow uses `compileSdk 37`).

```
./gradlew :app:klyxBundleRelease
```

The bundled `.klyx` file is written to `output/`.

## License

[GPL-3.0](LICENSE) © uliteamr
