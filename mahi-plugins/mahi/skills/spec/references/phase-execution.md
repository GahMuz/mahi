# Phase : Implementation

Report all progress in French.

## Process

### Step 1: Read Configuration
From `.mahi/config.json`:
- `parallelTaskLimit`: max concurrent agents (0 = unlimited)
- `pipelineReviews`: overlap reviews with next batch
- `models`: model assignments per agent

### Step 2: Verify Baseline Exists
Check that `.mahi/work/spec/<spec-path>/baseline-tests.json` exists (captured during worktree phase).
- If missing: capture now (run test suite, save results)
- If exists: read and report "Baseline existante : X tests."
- Append log.md entry: "Phase d'implémentation démarrée."

### Step 3: Get Current State via Mahi MCP
Lire l'état du workflow depuis le serveur :
```
mcp__plugin_mahi_mahi__get_workflow(workflowId: <lire depuis active.json>)
```
Vérifier que la phase retournée est bien `implementation`. Si erreur du serveur : afficher en français.

> Note mahi : il n'y a pas de lecture de state.json pour obtenir la phase ou la progression.
> La source de vérité est toujours `mcp__plugin_mahi_mahi__get_workflow`.

### Step 4: Dispatch Orchestrator
Delegate all wave execution to the orchestrator agent:

```
Agent({
  description: "Orchestrer l'implémentation de <spec-id>",
  subagent_type: "mahi:spec-orchestrator",
  model: <from config.models.orchestrator, default "sonnet">,
  prompt: "Spec: <spec-id>
    WorkflowId: <workflowId depuis active.json>
    Plan: .mahi/work/spec/<spec-path>/plan.md
    Design: .mahi/work/spec/<spec-path>/design.md
    Requirements: .mahi/work/spec/<spec-path>/requirement.md
    Config: .mahi/config.json
    Worktree: .worktrees/<spec-id>
    Rules: charger via sdd-rules protocol (Glob **/sdd-rules/SKILL.md)
    Execute all waves, update checkboxes, run phantom checks, dispatch reviews.
    Ne pas lire state.json — utiliser mcp__plugin_mahi_mahi__get_workflow pour l'état courant."
})
```

The orchestrator handles all internal steps: wave building, parallel agent dispatch, checkpoint verification, phantom detection, code review, progress reporting. See `agents/spec-orchestrator.md` for the full process.

### Step 5: Monitor and Report
After orchestrator completes:
- Read updated plan.md to confirm all subtasks are `[x]`
- Vérifier la progression via `mcp__plugin_mahi_mahi__get_workflow(workflowId)` — ne pas lire state.json
- Run full test suite in worktree
- **Breaking change detection**: Compare results against `baseline-tests.json`. Tests that passed in baseline but now fail = potential breaking changes. For each:
  - Ask user: "Le test `<name>` passait avant l'implémentation et échoue maintenant. Bug ou changement cassant intentionnel ?"
  - If breaking change: record in `baseline-tests.json` `breakingChanges` array with test name, file, reason, taskId
  - If bug: report for fix before finishing
- Append log.md entry: "Implémentation terminée. X/Y sous-tâches complétées. Z changements cassants documentés."
- Report: "Toutes les sous-tâches terminées. Suite de tests : X tests passent. Y changements cassants documentés."

### Step 6: Dispatch Spec Reviewer
Dispatcher d'abord en mode rapport uniquement :
```
Agent({
  description: "Revue spec/code de <spec-id>",
  subagent_type: "mahi:spec-reviewer",
  model: <from config.models.spec-reviewer, default "sonnet">,
  prompt: "specId: <spec-id>
    specPath: <spec-path>
    worktreePath: .worktrees/<spec-id>
    fix: false
    interactive: false"
})
```

Présenter le rapport à l'utilisateur.
Si le rapport contient des corrections proposées : demander "Appliquer ces corrections ? (oui/non)"
- Si oui : redispatcher avec `fix: true`, `interactive: false` et le même modèle (`config.models.spec-reviewer`, default "sonnet")
- Si non : continuer sans appliquer

Selon la recommandation finale :
- "prêt pour finishing" → follow `references/phase-finish.md`
- "nouvelles tâches à implémenter" → reprendre l'implémentation avec les nouveaux TASKs ajoutés
- "violations à corriger manuellement" → présenter le rapport, demander comment procéder

### Error Handling
- Orchestrator reports critical review issues → present to user in French, ask how to proceed
- Orchestrator reports phantom completions → log and present to user
- Orchestrator fails entirely → report error, suggest `/spec open`
