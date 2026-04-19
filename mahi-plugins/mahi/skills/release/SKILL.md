---
name: release
description: "Use this skill when the user invokes '/release' to publish a new version of the Mahi plugin. Orchestrates the full release pipeline: version bump, Gradle build, git commit + tag, push remote, and SNAPSHOT bump. Reserved for the maintainer on the main branch."
argument-hint: "[major | minor | patch | X.Y.Z]"
context: fork
allowed-tools: ["Read", "Write", "Edit", "Bash", "Glob"]
---

# Release Skill

Orchestrates the complete Mahi release process. See `.claude/commands/release.md` for the full procedure.

**Reserved for the maintainer. Must be run from the `main` branch.**

## Quick Summary

1. **Pre-conditions** — verify branch is `main`, no active spec/ADR, repo is clean
2. **Version selection** — propose patch/minor/major variants, confirm with user
3. **File update** — bump `build.gradle.kts`, `plugin.json`, `marketplace.json`
4. **Build** — run Gradle `bootJar` via PowerShell (Windows) or `./gradlew` (Unix)
5. **Commit + tag** — `chore(release): release vX.Y.Z` + annotated tag `vX.Y.Z`
6. **Push** — `git push origin main --tags`
7. **SNAPSHOT bump** — bump `build.gradle.kts` to `X.Y.(Z+1)-SNAPSHOT` and commit

Follow `.claude/commands/release.md` for the detailed step-by-step procedure.
