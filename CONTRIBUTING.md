# Contributing to CivDiscord

Thanks for your interest. This document covers what you need to know to land a change.

## Development environment

CivDiscord pins its JDK and build tools via [mise](https://mise.jdx.dev/). After cloning:

```bash
mise install                 # installs the pinned JDK (Java 21)
mise exec -- ./gradlew tasks # verify Gradle works
```

If you prefer not to use mise, install Temurin (or any OpenJDK) 21 yourself and drop the `mise exec --` prefix from the commands below.

## Running tests

```bash
mise exec -- ./gradlew check
```

This runs ktlint, compiles all three modules, and runs the full JUnit + Kotest + MockBukkit test suite. Expect ~200+ tests; a full run takes ~30–60 s on a warm Gradle daemon.

For faster iteration during work on a single module:

```bash
mise exec -- ./gradlew :core:check
mise exec -- ./gradlew :velocity:check
mise exec -- ./gradlew :paper:check
```

## Coding conventions

- **Kotlin idiom.** Prefer expression bodies, `when`, data classes, sealed hierarchies. Avoid Java-style null sentinels.
- **Self-documenting code.** Names carry meaning; reserve comments for non-obvious WHY — hidden constraints, surprising invariants, workarounds for upstream bugs. Do not narrate WHAT the code is doing.
- **ktlint.** Enforced in CI. `mise exec -- ./gradlew ktlintFormat` fixes most issues. Semicolon-chained statements (`foo(); bar()`) will fail the build.
- **`when` over `if/else if` chains** for sealed types — and make them exhaustive so the compiler catches new variants.
- **MarkdownSafe.** Any user-controlled string interpolated into Discord output **must** go through the `MarkdownSafe` helper in `core/text/`. Player names, chat messages, snitch names — all untrusted. This is the difference between a spoofed `@everyone` and a contained message.
- **Suspending vs. blocking.** Velocity scheduler tasks and JDA event handlers run on bounded pools. Do not block them on I/O; route to coroutines or the appropriate executor.
- **DB access via Exposed** in `transaction { … }` blocks. The DAO layer lives in `core/db/`; new tables get a numbered migration in `core/src/main/resources/db/migrations/` plus an entry in `MigrationRunner.Migrations.ALL`.

## Test-driven development

We expect tests first. The workflow:

1. Write a failing test that describes the behavior you want.
2. Implement the minimum to make it pass.
3. Refactor; tests stay green.

Tests live next to the module they cover (`core/src/test/`, `velocity/src/test/`, `paper/src/test/`). For Bukkit-side behavior we use MockBukkit; for JDA we mock at the boundary (`JDA`, `Guild`, `Member`) with MockK.

A PR that changes behavior with no test changes will be sent back unless the change is purely cosmetic.

## Commit messages

Imperative present tense, short subject line, optional scope colon. Examples:

```
Velocity: filter PluginMessageEvent by source backend
core: tighten BindingDao UNIQUE race window
docs: document Patreon refresh-token rotation
```

Subject ≤ 72 chars. Body wraps at 72. Body is optional for trivial changes but expected when the *why* isn't obvious from the diff.

## Pull request checklist

Before opening a PR, confirm:

- [ ] `mise exec -- ./gradlew check` passes locally.
- [ ] New behavior has a test; no new uncovered branches.
- [ ] `README.md` updated if your change is user-facing (config keys, commands, permissions, install steps).
- [ ] `docs/ARCHITECTURE.md` updated if you added a module, payload variant, table, or threading boundary.
- [ ] `CHANGELOG.md` has an entry under `## [Unreleased]` for notable changes.
- [ ] If you touched `libs/`, `libs/CHECKSUMS.sha256` is updated and the CI checksum job will pass.
- [ ] No secrets committed; `config.yml` examples use placeholders.

## Reporting bugs

Use GitHub Issues. Include:

- CivDiscord version (`mise exec -- ./gradlew :velocity:properties | grep version` or the jar filename).
- Velocity + Paper versions.
- Relevant log lines (with debug logging enabled for `io.github.grepsedawk.civdiscord` if reproducible).
- Steps to reproduce.

For security issues, see [`SECURITY.md`](SECURITY.md) — do not open a public issue.
