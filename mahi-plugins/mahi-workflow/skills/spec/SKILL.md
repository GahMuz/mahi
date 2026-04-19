---
name: spec
description: "This skill should be used when the user invokes '/spec' to manage spec-driven development workflow. Handles 'new spec', 'open spec' (loads context + resumes workflow), 'recap' (briefing complet avec contexte), 'approve phase', 'clarify spec documents (requirements, design, or plan)', 'discard spec', 'split spec', 'close spec', 'switch spec'. Orchestrates the full lifecycle from requirements through tested, reviewed code."
argument-hint: "new <titre> | open [titre] | recap | clarify | approve | discard | split [<new-titre>] | close | switch <titre>"
context: fork
allowed-tools: ["Read", "Write", "Edit", "Bash", "Glob", "Grep", "Agent", "EnterWorktree", "ExitWorktree", "mcp__plugin_mahi-workflow_mahi__*"]
---

# Spec Workflow Orchestrator

All communication with the user MUST be in French.

## Local Active Item

The currently active item (spec or ADR) is tracked in `.sdd/local/active.json` — gitignored, machine-local, never committed. **Always read it via `mahi_get_active()` — never with the `Read` tool directly** (the file lives in the repo root, not in the current working directory which may be a worktree).

```json
{ "type": "spec", "id": "mon-spec", "path": ".sdd/specs/2026/04/mon-spec", "activatedAt": "ISO-8601", "workflowId": "<uuid>" }
```

**Rules:**
- Only one item (spec or ADR) can be active at a time on this machine. This single file enforces the constraint.
- `new`, `open`, `switch` are the only commands that write this file (via `mahi_activate`).
- All other commands fail immediately if `mahi_get_active()` returns null or has `type != "spec"`: "Aucun spec actif. Lancez `/spec open <titre>` pour en ouvrir un."
- `new` and `open` call `mahi_get_active()`: if present with any type, execute the appropriate CLOSE (spec or ADR) before continuing.

## Parse Arguments

Extract subcommand from user input:
- `new <titre>` → START_NEW
- `open [titre]` → OPEN
- `recap` → RECAP
- `clarify` → CLARIFY
- `approve` → APPROVE
- `discard` → DISCARD
- `split [<new-titre>]` → SPLIT
- `close` → CLOSE
- `switch <titre>` → SWITCH
- no args → CHECK_STATE

## CHECK_STATE

1. Check `.sdd/config.json` exists. If not: "Lancez `/sdd-init` d'abord pour configurer le projet."
2. Call `mahi_get_active()`. If present: call `mahi_get_workflow(workflowId)` to retrieve current phase — if the call fails, display: "Le serveur Mahi n'est pas démarré. Vérifiez votre configuration `.mcp.json` et que le serveur Mahi est actif (java -jar mahi-mcp-server.jar)." and stop. Show that spec prominently with its current phase. If null: "Aucun spec actif — lancez `/spec new <titre>` ou `/spec open <titre>`."

## START_NEW

0. Call `mahi_get_active()`. If present: execute CLOSE (full context save), then continue.
1. Verify `.sdd/config.json` exists.
2. Convert title to kebab-case for directory name. Note current `YYYY/MM` from today's date.
3. Create `.sdd/specs/YYYY/MM/<kebab-titre>/` and `reviews/` subdirectory.
4. Create empty `rule-candidates.md` in the spec directory (header only: `# Règles candidates`).
5. Call `mahi_create_workflow(flowId=<spec-id>, workflowType="spec")` — store the returned `workflowId`.
   Then call `EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")` to create the branch and enter the worktree.
6. Write initial log.md with creation entry: date, title, "Spec créé".
7. Call `mahi_update_registry(specId, "requirements", title, period)` to add the spec row in registry.
8. Call `mahi_activate(specId, "spec", path, workflowId)` to write `.sdd/local/active.json` on the main branch.
9. Enter requirements phase — read and follow `references/phase-requirements.md`.

## OPEN

0. Prévenir : "Pour un contexte propre, cette commande fonctionne mieux après un `/clear`. Si la session contient du contexte accumulé d'un travail précédent, les réponses futures pourraient être influencées par cet historique."
1. Read `.sdd/specs/registry.md`. Title given → find matching row. No title → list non-completed rows, ask user (in French).
2. Call `mahi_get_active()`. If present with `type="adr"`: execute ADR CLOSE. If `type="spec"` with different id: execute spec CLOSE. If same id: skip to step 4.
3. Call `mahi_activate(specId, "spec", path, workflowId)` to write `.sdd/local/active.json` on the main branch. Then call `EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")` to enter the worktree.
4. Load context following priority order from `references/protocol-context.md` section **Chargement du contexte** — present the briefing before resuming.
5. Call `mahi_get_workflow(workflowId)` → currentPhase. If the call fails: "Le serveur Mahi n'est pas démarré. Vérifiez votre configuration `.mcp.json` et que le serveur Mahi est actif (java -jar mahi-mcp-server.jar)." and stop. If in implementation → follow `references/protocol-resume.md`.
6. Report state (in French) and resume.

## RECAP

0. Call `mahi_get_active()`. If null or `type != "spec"`: fail.
Read and follow `references/phase-recap.md`.

## APPROVE

0. Call `mahi_get_active()`. If null or `type != "spec"`: fail.
1. Call `mahi_get_workflow(workflowId)` → currentPhase.
2. Validate current phase output:
   - requirements: requirement.md has >= 1 REQ
   - design: design.md has >= 1 DES
   - planning: plan.md has >= 1 TASK with subtasks
   - finishing: all tests pass, all subtasks [x], no uncommitted changes in worktree
3. Advance per state machine (`references/state-machine.md`) — fire the event matching the current phase:
   - requirements → fire `APPROVE_REQUIREMENTS` → design: follow `references/phase-design.md`
   - design → fire `APPROVE_DESIGN` → worktree: follow `references/phase-worktree.md` then `references/phase-planning.md`
   - planning → fire `APPROVE_PLANNING` → implementation: follow `references/phase-execution.md`
   - implementation → fire `APPROVE_IMPLEMENTATION` → finishing: follow `references/phase-finish.md`
   - finishing → fire `APPROVE_FINISHING` → retrospective: follow `references/phase-retro.md`
   - retrospective → fire `APPROVE_RETROSPECTIVE` → completed: follow `references/phase-retro.md`
   If the server returns an error: display the error message in French — do not attempt a local transition.
4. Call `mahi_update_registry(specId, <newPhase>)` to update the status column in registry.
   Call `mahi_update_state(specPath, <newPhase>, changelogEntry)` to update state.json for the spec.

## CLARIFY

0. Call `mahi_get_active()`. If null or `type != "spec"`: fail.
Read and follow `references/protocol-clarify.md`.

## DISCARD

0. Call `mahi_get_active()`. If null or `type != "spec"`: fail.
1. **Ask explicit confirmation** (destructive).
2. If confirmed:
   - Call `mahi_remove_worktree(workflowId)` — removes the worktree and associated branch server-side.
   - Call `mahi_fire_event(workflowId, event="discard")` — marks the workflow as discarded on the server.
   - Remove `.sdd/specs/YYYY/MM/<id>/`.
3. Call `mahi_update_registry(specId, "discarded")` to mark the row as discarded in registry.
4. Call `ExitWorktree()` to return to the main branch, then call `mahi_deactivate()` to delete `.sdd/local/active.json`.
5. Confirm completion.

## SPLIT

0. Call `mahi_get_active()`. If null or `type != "spec"`: fail.
Read and follow `references/protocol-split.md`.

## CLOSE

0. Call `mahi_get_active()`. If null or `type != "spec"`: fail — "Aucun spec actif. Utilisez `/adr close` si un ADR est actif."
Read and follow `references/protocol-context.md` section **CLOSE**.

## SWITCH

Execute OPEN on the requested spec, skipping OPEN step 0 (the /clear warning is not appropriate when switching within the same session). OPEN handles closing the current active automatically.

## Key Principles

**Feedback:** Always tell the user (in French) what phase they're in, what happened, what comes next. During implementation: show subtask progress X/Y.

**Parallelization:** During implementation, dispatch all independent subtasks simultaneously via Agent tool. Respect `parallelTaskLimit` from config.

**Security:** No secrets in spec docs. Validate file paths. Double-confirm destructive actions.

**Token efficiency:** Load phase references only when entering that phase. Agent prompts contain only relevant task context, not full specs.

**FSM Server:** All phase transitions are managed by the Mahi server via MCP calls. Never implement local transition logic. If `mahi_fire_event` returns an error, display it in French and stop.

## Phase References

| Phase | Reference |
|-------|-----------|
| Requirements | `references/phase-requirements.md` |
| Design | `references/phase-design.md` |
| Worktree | `references/phase-worktree.md` |
| Planning | `references/phase-planning.md` |
| Implementation | `references/phase-execution.md` (delegates to orchestrator agent) |
| Finishing | `references/phase-finish.md` |
| Retrospective | `references/phase-retro.md` |
| Clarify | `references/protocol-clarify.md` |
| Recap | `references/phase-recap.md` |
| Split | `references/protocol-split.md` |
| Close / Switch | `references/protocol-context.md` |
| State machine | `references/state-machine.md` |
| Resume protocol | `references/protocol-resume.md` |

## Related Skills

| Skill | Purpose |
|-------|---------|
| `/sdd-status` | Vue d'ensemble : spec active, specs en cours, specs terminées |
| `/spec-review [--no-fix]` | Revue manuelle spec/code : détecte et corrige les incohérences |
| `/doc <module \| --all \| update \| analyse \| status>` | Documenter, analyser et maintenir la doc codebase (économie 80-90% tokens) |
| `/sdd-evolve <action>` | Faire évoluer la configuration .claude/ (ajouter, optimiser, auditer) |
