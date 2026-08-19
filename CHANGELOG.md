# Changelog

All notable changes to Rustup Manager are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-08-19

### Added

- **Custom initialization options** — define your own keys for rust-analyzer's `initialize` request (boolean or free-text values), merged after the built-in toggles, from **Feature Parameters and Initialize**.
- **Raw options JSON** — paste a complete JSON object to send to rust-analyzer verbatim; it overrides every other option (with live validation of the object).
- **Real download progress** — every install operation (rustup, toolchains, components, targets, rust-analyzer builds) now reports percent and downloaded/total bytes instead of an indeterminate spinner.
- **Multiline text fields** — the styling for dashboard input fields now has a multiline variant used by the raw-JSON editor.

### Changed

- **Language server card** — one vertical card with `rustup | versions` sources; **Latest stable** and **nightly** are pinned at the top of the release list and the separate versions screen is gone.
- **Single active server source** — "Use" now switches exclusively: taking a GitHub version removes the rustup component, and taking the rustup component removes the managed binary, so both can never fight.
- **Feature Parameters card** moved to the top of the LSP screen, and the four toggle cards now carry distinct icons.
- **Dashboard text fields** — Material 3 tonal fill with a primary-color focus border.
- **Plugin icon** is only shown in the store; the toolbar action no longer duplicates it.

### Fixed

- **Scroll state** — scrolling away from the dashboard lists no longer resets typed input or re-fetches (and blanks) the rust-analyzer release list; fetching now lives in the screen body instead of inside the scrollable item.

## [1.1.0] - 2026-08-14

### Added

- **One-tap setup** — installs `rustup` with a stable default toolchain inside Klyx's built-in Linux environment using the official `sh.rustup.rs` installer.
- **Reset & retry** — wipes `~/.rustup` and `~/.cargo` for a clean reinstall when a stale install causes trouble.
- **Toolchain management** — install, switch the default, update, and remove `stable`, `beta`, `nightly`, or pinned versions (e.g. `1.80.0`).
- **Update detection** — flags toolchains that have an available update via `rustup check`, and an **Update all** action that runs `rustup update` across everything.
- **Component management** — add/remove `clippy`, `rustfmt`, and `rust-src`.
- **Target management** — list, add, and remove cross-compilation targets (e.g. `aarch64-unknown-linux-gnu`, `wasm32-unknown-unknown`).
- **rust-analyzer installation** — three install options from the dashboard's Language server card:
  - `rustup component add rust-analyzer` (toolchain-managed);
  - **Latest** GitHub release — **Stable** (latest tagged release) or **Nightly** (rolling build);
  - **Versions** — browse the full release list, with each version shown as **Installed / In use / Not installed**; install any version, switch between installed ones with **Use**, and remove them.
- **rust-analyzer wiring** — registers the server for `.rs` files automatically and spawns it by its bare command name, so Klyx resolves it against the Linux environment's `PATH` and it always matches the active toolchain (no hard-coded paths). GitHub-installed binaries are symlinked into `~/.local/bin/rust-analyzer`.
- **LSP features** — diagnostics, completion, and inlay hints for `.rs` files, plus live indexing progress.
- **Indexing progress** — `$/progress` notifications surfaced as toasts when indexing starts and finishes.
- **Background updates** — optional `rustup update` on a configurable interval (daily / weekly / monthly), off by default, toggled from settings; the loop wakes every 6 hours but only acts when the interval has elapsed.
- **Settings screen** — update-check interval, default toolchain channel, completion-order fix, indexing notifications, and toolbar auto-hide (only show the Rust Toolchain button while a `.rs` file is open).
- **LSP screen** — server status (not started / running with pid / exited), stop button, and log filtering.
- **Dashboard icon set** — local vector icons and plugin icon, dropping the material-icons dependency.
- **Graceful error handling** — detects a missing Linux environment before anything runs and turns install failures into short, actionable hints (network problems, missing files, missing commands) instead of silent crashes.
- **Material 3 Expressive UI** — animated transitions, rounded cards, and icon-first controls.
- **CI workflow** — GitHub Actions build (`compileSdk 37`, JDK 21) that produces the `.klyx` bundle.

### Changed

- **Logs routed to Klyx's SDK logs** — LSP traffic goes to the host's LSP log via `LanguageClient.logMessage`, and rustup operations go to the app logs via the SDK `Logger` (`LOG_TAG = "Rustup"`), replacing the plugin's custom in-dashboard log panels.
- **Dashboard redesign** — toolchain cards, available-update flags, and the Language server card; the "Logs" button became "Manage".
- **Progress indicators** — the dashboard's busy bars and inline spinners use the M3 Expressive API with rounded stroke caps.
- **README** — the LSP limitation note moved from the top into a "Limitations and known issues" section at the bottom (Klyx's markdown renderer doesn't render `[!NOTE]` cards).

### Fixed

- **Status bar overlap** — screen headers are padded below the status bar so top content is no longer hidden behind it.
- **Stderr draining** — rust-analyzer's stderr is drained line-by-line like the reference implementation so the server never deadlocks.
- **Build workarounds** — a `plugin.json`-missing workaround for a klyx-gradle-plugin configuration-time crash, and the `TerminalManager` package reference (`com.klyx.api.data.terminal`).
