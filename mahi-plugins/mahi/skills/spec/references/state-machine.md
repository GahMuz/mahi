# State Machine

> Note mahi : cette FSM est implémentée côté serveur Mahi.
> Les transitions sont déclenchées via `mcp__plugin_mahi_mahi__fire_event` et validées par le serveur.
> Le LLM ne doit pas implémenter de logique de validation locale.

## Valid Phase Values

| Phase | Description |
|-------|-------------|
| `requirements` | Gathering and refining requirements |
| `design` | Creating technical design |
| `worktree` | Setting up isolated workspace |
| `planning` | Breaking design into tasks |
| `implementation` | Executing tasks with TDD |
| `finishing` | Completing the branch |
| `retrospective` | Extracting learnings and updating rules |
| `completed` | Spec fully done |

## Valid Transitions

```
requirements → design          (APPROVE_REQUIREMENTS — guards: requirements artifact VALID + coherence)
design → worktree              (APPROVE_DESIGN — guard: design artifact VALID)
worktree → planning            (APPROVE_WORKTREE — automatic after setup)
planning → implementation      (APPROVE_PLANNING — guard: plan artifact VALID)
implementation → finishing      (APPROVE_IMPLEMENTATION — all subtasks done)
finishing → retrospective      (APPROVE_FINISHING)
retrospective → completed      (APPROVE_RETROSPECTIVE)
```

No other transitions are valid. Phases cannot be skipped.
The server enforces these constraints — any invalid transition attempt will return an error.

## Transition Procedure

When advancing from phase X to phase Y:
1. Call `mcp__plugin_mahi_mahi__fire_event(workflowId, event="<APPROVE_X>")` — the server handles the transition
2. If the server returns an error: display the error message in French and stop
3. The server response confirms the new currentPhase — use this for further instructions
4. Call `mcp__plugin_mahi_mahi__update_registry(specId, <newPhase>)` to reflect the new phase
5. Call `mcp__plugin_mahi_mahi__update_state(specPath, <newPhase>, changelogEntry)` to persist state.json

Do NOT update `state.json` or `registry.json` locally — all state is managed server-side.

## Event Names per Phase

| Current phase | Event to fire |
|--------------|---------------|
| `requirements` | `APPROVE_REQUIREMENTS` |
| `design` | `APPROVE_DESIGN` |
| `worktree` | `APPROVE_WORKTREE` |
| `planning` | `APPROVE_PLANNING` |
| `implementation` | `APPROVE_IMPLEMENTATION` |
| `finishing` | `APPROVE_FINISHING` |
| `retrospective` | `APPROVE_RETROSPECTIVE` |

## Reading Current Phase

```
mcp__plugin_mahi_mahi__get_workflow(workflowId)
```

The response includes `currentPhase` (derived from server state via `getStateToPhaseMapping()`).
Never read `state.json` to determine the current phase in mahi.

## Error States

| Error | Action |
|-------|--------|
| Worktree creation fails | Report git error to user |
| Test baseline fails | Stay in worktree phase (fire APPROVE_WORKTREE only when tests pass), report failing tests |
| Subtask fails | Mark `[!]` in plan.md, continue others, report after batch |
| Critical review issue | Block next batch, report to user |
| All subtasks in batch fail | Pause implementation, wait for user |
| `mcp__plugin_mahi_mahi__fire_event` returns error | Display server error in French, do not retry without user action |