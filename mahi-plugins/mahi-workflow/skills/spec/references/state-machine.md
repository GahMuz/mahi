# State Machine

> Note mahi-workflow : cette FSM est implémentée côté serveur Mahi.
> Les transitions sont déclenchées via `mahi_fire_event` et validées par le serveur.
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
requirements → design              (user approval → mahi_fire_event(workflowId, "approve"))
design → worktree                  (user approval → mahi_fire_event(workflowId, "approve"), auto-chain)
worktree → planning                (automatic after setup → mahi_fire_event(workflowId, "approve"))
planning → implementation          (user approval → mahi_fire_event(workflowId, "approve"))
implementation → finishing          (all subtasks [x] + /spec approve → mahi_fire_event(workflowId, "approve"))
finishing → retrospective          (user chooses "Valider" → mahi_fire_event(workflowId, "approve"))
retrospective → completed          (retro complete → mahi_fire_event(workflowId, "approve"))
```

No other transitions are valid. Phases cannot be skipped.
The server enforces these constraints — any invalid transition attempt will return an error.

## Transition Procedure

When advancing from phase X to phase Y:
1. Call `mahi_fire_event(workflowId, event="approve")` — the server handles the transition
2. If the server returns an error: display the error message in French and stop
3. The server response confirms the new currentPhase — use this for further instructions
4. Update `Statut` column in `.sdd/specs/registry.md` to reflect the new phase

Do NOT update `state.json` locally — all state is managed server-side.

## Reading Current Phase

To read the current phase of the active workflow:
```
mahi_get_workflow(workflowId)
```

The response includes `currentPhase`, `artifacts`, and any other server-managed state.
Never read `state.json` to determine the current phase in mahi-workflow.

## Error States

| Error | Action |
|-------|--------|
| Worktree creation fails | Call `mahi_fire_event(workflowId, "error")`, report git error |
| Test baseline fails | Stay in worktree phase (server rejects transition), report failing tests |
| Subtask fails | Mark `[!]` in plan.md, continue others, report after batch |
| Critical review issue | Block next batch, report to user |
| All subtasks in batch fail | Pause implementation, wait for user |
| `mahi_fire_event` returns error | Display server error in French, do not retry without user action |
