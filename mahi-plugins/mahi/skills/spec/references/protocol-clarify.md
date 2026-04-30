# Clarification Handler

## Purpose

Update an in-progress spec to reflect a new clarification — edit documents in-place, log the change, and propagate impact downstream.

## Process

### Step 1: Identify Active Spec and Affected Documents

1. Read `.mahi/.local/active.json` to get the active spec's `path` and `workflowId`. (The parent handler already failed if absent.)
2. Read the user's clarification request.
3. Determine which documents are affected:
   - Requirement change → `requirement.md` (and potentially `design.md`, `plan.md`)
   - Design change → `design.md` (and potentially `plan.md`)
   - Planning change → `plan.md` only

### Step 2: Edit In-Place

For each affected document:
- Locate the relevant REQ-xxx / DES-xxx / TASK-xxx item
- Edit the item content in-place (do not create new IDs unless new requirements are added)
- Update status icon if applicable

### Step 3: Log in Changelog and Notify Server

Ajouter une entrée dans `log.md` (journal local conservé pour l'historique) :
```markdown
## <ISO-8601> — Clarification

<summary of change>

Éléments impactés : REQ-xxx, DES-xxx
```

Notifier le serveur Mahi selon le type de clarification :
- Changement de requirement :
  ```
  mcp__plugin_mahi_mahi__add_requirement_info(flowId: <depuis active.json>, info: "<summary of change> — impacte REQ-xxx")
  ```
- Changement de design :
  ```
  mcp__plugin_mahi_mahi__add_design_info(flowId: <depuis active.json>, info: "<summary of change> — impacte DES-xxx")
  ```

### Step 4: Propagate Downstream

Follow the dependency chain — a change to a requirement cascades:

```
REQ-xxx changed
  → Review all DES items that implement this REQ
    → Review all TASK items that implement those DES items
      → Mark affected incomplete subtasks [!]
```

- If a downstream item is already `[x]` completed: warn the user ("Cette tâche terminée est peut-être impactée")
- If a downstream item is `[ ]` pending: no action needed (it will pick up the change when executed)
- If a downstream item is `[~]` in-progress or `[!]` failed: mark `[!]` and add a note

### Step 5: Persist Context

Appeler `mcp__plugin_mahi_mahi__save_context` pour enregistrer la décision de clarification côté serveur (résistant aux interruptions de session) :

```
mcp__plugin_mahi_mahi__save_context(flowId: <depuis active.json>, context: {
  lastAction: "Clarification : <résumé de la modification>",
  keyDecisions: ["REQ-xxx modifié : <décision prise>"],
  openQuestions: [],
  nextStep: "Poursuivre la phase <currentPhase>"
})
```

### Step 6: Report (in French)

```
Clarification appliquée à '<titre>'.

Modifié :
- REQ-xxx : <résumé de la modification>

Propagation :
- DES-xxx impacté — mis à jour
- TASK-002.3 marqué [!] — en cours, vérification requise

Aucune tâche terminée impactée.

Contexte persisté via save_context.
```
