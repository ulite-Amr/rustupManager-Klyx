# Language server (rust-analyzer)

Rustup Manager registers `rust-analyzer` as the language server for `.rs` files, so editing Rust code gives you diagnostics, completion, and inlay hints without configuring a server yourself.

## How it works

- Klyx owns the language server's lifecycle: it calls the plugin to spawn `rust-analyzer` whenever a `.rs` file needs one, and spawns a fresh one automatically the next time it's needed (for example, re-opening or editing a Rust file).
- The server is spawned **by its bare command name** and resolved against the Linux environment's `PATH`. That means it works whether `rust-analyzer` was installed through `rustup` or as a prebuilt GitHub release symlinked into `~/.local/bin`, and it always matches whatever toolchain is active — no hard-coded absolute paths that can drift out of date.

## Installing rust-analyzer

Three options, all managed from the dashboard's **Language server** card:

1. **rustup component** — `rustup component add rust-analyzer`, the toolchain-managed install.
2. **Latest** — downloads the newest build from rust-analyzer's GitHub releases. Pick **Stable** (the latest tagged release, e.g. `2026-08-10`) or **Nightly** (the rolling `nightly` build).
3. **Versions** — browse rust-analyzer's release list. Each entry shows whether that version is **Installed**, **In use**, or **Not installed**. Install any version, switch between installed ones with **Use**, or delete them with **Remove**. When more than one version is installed, the active one is marked **In use**.

GitHub-installed binaries live under `~/.local/share/rust-analyzer/<version>/`, and the active one is symlinked into `~/.local/bin/rust-analyzer` so Klyx's bare-name lookup picks it up.

You can mix a rustup component with GitHub installs, but when a GitHub version is active it takes precedence (its `~/.local/bin` entry is found before the rustup fallback).

## LSP dashboard

Open the Language Server screen from the plugin's toolbar button:

- **Status** — not started / running (with process id) / exited, plus live indexing progress.
- **Stop** — kills the current `rust-analyzer` process. Klyx will start a new one automatically next time it's needed.
- **Clear logs** — empties the log panel.
- **Logs** — filter by **All** or **Errors**, and copy the visible logs to the clipboard.
- **Completion order fix** — reverses completion results (off by default).
- **Indexing notifications** — toasts when indexing starts and finishes.
- **Toolbar auto-hide** — only shows the Rust Toolchain button while a `.rs` file is open.

## Not available yet (Klyx host limits)

These features are shown in the LSP screen but are not implemented by Klyx yet:

- Hover / symbol info on hover
- Code actions (quick fixes, refactors)
- Go to definition
- Find references
- Rename symbol
- Signature help

## If the server won't start

- Make sure `rust-analyzer` is installed (rustup component **or** a GitHub release) — the dashboard shows its state and lets you install it.
- Open the LSP screen and check the logs; a failed spawn is logged there with an error line.
- If the environment itself is missing, set it up first — see [Troubleshooting](troubleshooting.md).
