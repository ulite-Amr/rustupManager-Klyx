# Usage

## Requirements

- Klyx **4.2.0** or newer.
- Klyx's built-in Linux environment must be bootstrapped. The plugin detects this and, if it's missing, shows **"Klyx Linux environment is not installed"** with a button that opens the terminal so you can finish the setup first.

## First run

1. Open **Rustup Manager** from the toolbar.
2. If `rustup` isn't installed yet, the dashboard shows an **Install rustup** button. It runs the official `sh.rustup.rs` installer with a stable default toolchain.
3. When the install finishes, the dashboard refreshes and shows your toolchains.

If a stale install ever causes trouble, use **Reset & retry** — it wipes `~/.rustup` and `~/.cargo` so the next install starts from a clean slate.

## Toolchains

The dashboard lists every installed toolchain and marks the active default.

- **Install** — type a toolchain name and add it: `stable`, `beta`, `nightly`, or a pinned release like `1.80.0`.
- **Set default** — switch the default toolchain for the whole environment.
- **Update** — update a single toolchain. Toolchains with an available update are flagged (from `rustup check`).
- **Remove** — uninstall a toolchain.
- **Update all** — runs `rustup update` across everything at once.

## Components

Add or remove the standard components:

- `clippy` — linting.
- `rustfmt` — formatting.
- `rust-src` — the standard library sources (needed for symbol navigation in some editors).

## Targets

List the installed compilation targets and add or remove more, e.g. `aarch64-unknown-linux-gnu` or `wasm32-unknown-unknown`, for cross-compilation.

## Settings

From the plugin's settings screen:

| Setting | What it does |
| --- | --- |
| Update check interval | Runs `rustup update` in the background every **1, 7, or 30 days** (daily / weekly / monthly). Off by default. |
| Toolchain channel | Default channel for newly installed toolchains. |
| Completion order fix | Reverses `rust-analyzer`'s completion results. Off by default — rust-analyzer already returns the most relevant entry first. |
| Indexing notifications | Shows a toast when `rust-analyzer` starts and finishes indexing. |
| Toolbar auto-hide | Only shows the Rust Toolchain button while a `.rs` file is open. |

The background update loop wakes every 6 hours but only acts when the configured interval has elapsed.

See the [language server](language-server.md) doc for the LSP-specific options.
