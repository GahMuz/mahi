# Mahi — Claude Code Instructions

## Build

**Requirements:** Java 21, Gradle (wrapper included)

**Windows (required — bash fails due to JAVA_HOME with spaces):**
```
powershell.exe -Command "Set-Location mahi-mcp; .\gradlew.bat bootJar"
```

**Unix/macOS:**
```bash
cd mahi-mcp && ./gradlew bootJar
```

The build copies the JAR to `mahi-plugins/mahi/mahi-mcp-server.jar` automatically.

## Tests

```
powershell.exe -Command "Set-Location mahi-mcp; .\gradlew.bat test"
```

Or Unix: `cd mahi-mcp && ./gradlew test`

Test reports: `mahi-mcp/build/reports/tests/test/index.html`
Coverage: `mahi-mcp/build/reports/jacoco/test/html/index.html`

## Architecture

Two-layer architecture:

```
mahi-mcp/          Java MCP server (Spring Boot 3.4.4, Spring AI 1.1.4)
  └── ia.mahi/
      ├── mcp/               MCP tool endpoints (@McpTool)
      ├── workflow/
      │   ├── core/          FSM primitives (WorkflowContext, Artifact, Guard…)
      │   ├── definitions/   Per-workflow-type state machine definitions
      │   └── engine/        WorkflowEngine, WorkflowService
      ├── store/             WorkflowStore (atomic JSON persistence)
      └── service/           ActiveStateService, StateFileService, GitWorktreeService

mahi-plugins/mahi/         Claude Code plugin (workflow + graph consumption)
  ├── agents/        Specialized agents (spec-orchestrator, spec-planner…)
  └── skills/        User-invocable skills (/spec, /adr, /graph-query, /graph-status…)

mahi-plugins/mahi-codebase/ Claude Code plugin (codebase generation — CI/CD)
  ├── agents/        Generation agents (doc-generator, graph-builder-java, analyse-*)
  └── skills/        Generation skills (/doc, /analyse, /graph-build)
```

## Key Design Rules

- **MCP tool names**: `mcp__plugin_mahi_mahi__<tool_name>` — never use short `mahi_*` form in plugin docs
- **FSM events**: `APPROVE_REQUIREMENTS`, `APPROVE_DESIGN`, `APPROVE_WORKTREE`, `APPROVE_PLANNING`, `APPROVE_IMPLEMENTATION`, `APPROVE_FINISHING`, `APPROVE_RETROSPECTIVE` — these are the only valid events
- **No `fire_event("discard")`**: discard is handled by `remove_worktree` + `update_registry` + `deactivate` — no FSM event
- **Atomic writes**: all file writes use temp-file + `Files.move(REPLACE_EXISTING)`
- **Optimistic concurrency**: `WorkflowStore` checks `version` field before each save

## Release

Run `/release` and follow the prompts. See `.claude/commands/release.md` for details.
