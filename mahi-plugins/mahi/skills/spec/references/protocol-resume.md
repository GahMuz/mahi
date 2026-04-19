# Resume Protocol

## Resuming by Phase

### Interactive Phases (requirements, design, planning)
1. Read current document
2. Present to user (in French)
3. Ask: "Continuer l'édition ou approuver en l'état ?"

### Worktree Phase
1. Check worktree exists at `worktreePath` (obtenu via `mcp__plugin_mahi_mahi__get_workflow`)
2. Exists → verify branch, continue to test baseline
3. Missing → call `EnterWorktree(branch, path)` to recreate it (creates if absent, reuses if existing)
4. Branch also missing → report error: "Branche manquante — suggérer de re-créer la branche puis relancer `/spec open`"

### Implementation Phase
Follow half-done detection below.

### Finishing Phase
Re-run verification, re-present options.

## Half-Done Implementation Detection

### Step 1: Read Plan State
- Parse plan.md for all TASK/subtask items and statuses
- Lire la progression depuis le serveur :
  ```
  mcp__plugin_mahi_mahi__get_workflow(workflowId: <lire depuis active.json>)
  ```
  > Note mahi : il n'y a pas de lecture de state.json pour la progression.
  > Utiliser `mcp__plugin_mahi_mahi__get_workflow` comme source de vérité.

### Step 2: Inspect Worktree
```bash
git status --short
git log --oneline $(git merge-base HEAD <baseBranch>)..HEAD
```

### Step 3: Classify Each Subtask

| Recorded Status | Commits | Uncommitted | Tests Pass | Classification |
|----------------|---------|-------------|------------|----------------|
| `[x]` completed | Yes | No | Yes | Confirmé terminé |
| `[x]` completed | Yes | Yes | — | Nettoyage requis |
| `[~]` in-progress | Yes | No | Yes | Probablement terminé — vérifier |
| `[~]` in-progress | Yes | No | No | Partiellement fait — tests échouent |
| `[~]` in-progress | No | Yes | — | Commencé mais non commité |
| `[ ]` pending | No | No | — | Non commencé |

### Step 4: Report (in French)

```
Rapport de reprise : <titre>
Phase : implémentation
Progression : 5/12 sous-tâches terminées

Terminées :
  TASK-001.1, TASK-001.2, TASK-001.3, TASK-002.1, TASK-002.2 ✓

Partiellement terminées (attention requise) :
  TASK-002.3 : implémentation présente mais tests échouent
  TASK-003.1 : test écrit, pas d'implémentation

Non commencées :
  TASK-003.2, TASK-003.3, TASK-004.1, TASK-004.2, TASK-004.3

Action recommandée : Corriger TASK-002.3 et terminer TASK-003.1 avant le prochain lot.
```

### Step 5: Resume
- Fix partial subtasks first (re-dispatch task-implementer)
- Then continue normal batch execution

## Worktree Health Check

Before resuming implementation:
1. Worktree exists in `git worktree list`
2. Correct branch checked out
3. No merge conflicts
4. Dependencies installed (node_modules, vendor, etc.)

Report failures and suggest remediation before proceeding.
