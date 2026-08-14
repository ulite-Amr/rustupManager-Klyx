# Troubleshooting

The plugin was built to fail loudly and helpfully. Install/update failures are shown in the dashboard's log panel and end with a short `hint:` line that tells you the likely cause.

## "Klyx Linux environment is not installed"

The dashboard shows this when Klyx's PRoot rootfs isn't bootstrapped yet (checked via the environment's `.bootstrap-version` file).

**Fix:** tap **Open terminal** — Klyx opens the terminal screen so you can complete the environment setup. When it's done, tap **Check again** on the dashboard.

## Install fails with a network hint

The log ends with something like:

- `hint: network problem while downloading — check your connection`

**Fix:** you need a working internet connection for `sh.rustup.rs`, the toolchain downloads, and `apt`. Check your connection (or VPN/proxy), then retry.

## Exit code 126 or 127

- `hint: command not found inside the Klyx Linux environment`

A binary the command needs isn't installed in the Linux environment (e.g. `curl`, `apt`, or a component that's missing). Install the missing package, then retry.

## "no such file or directory"

- `hint: a required file is missing inside the Linux environment`

A file the operation depends on is gone. If this follows a failed install, use **Reset & retry** to wipe `~/.rustup` and `~/.cargo` and start clean.

## Reset & retry

On the "rustup is not installed" screen, **Reset & retry** removes `~/.rustup` and `~/.cargo` before reinstalling. Use it when:

- an install left a half-written state,
- a stale settings file causes install failures,
- you want a completely clean toolchain setup.

## rust-analyzer won't start

- Open the **Language Server** screen and read the logs — a failed spawn is logged with an `error:` line.
- Make sure `rust-analyzer` is installed via **rustup component** or **apt** (the dashboard shows which, if any, is present).
- If the environment is missing, set it up first — see above.

## The dashboard is stuck or outdated

Every screen refresh re-reads state from the environment. If the dashboard looks stale:

- Tap the refresh button in the dashboard header, or
- Navigate away and back to the dashboard.
