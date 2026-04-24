---
name: spec-review
description: "This skill should be used when the user invokes '/spec-review' to manually trigger a spec-vs-code consistency audit on the active spec. Detects phantom completions, unmarked completions, missing implementations, unsatisfied acceptance criteria, and project rule violations."
argument-hint: "[--no-fix]"
context: fork
allowed-tools: ["Read", "Agent", "mcp__plugin_mahi_mahi__get_active", "mcp__plugin_mahi_mahi__get_workflow"]
---

# Revue manuelle du spec actif

All output in French.

## Process

1. Appeler `mcp__plugin_mahi_mahi__get_active()`. Si absent ou `type != "spec"` : fail — "Aucun spec actif. Lancez `/spec open <titre>` pour en ouvrir un."

2. Appeler `mcp__plugin_mahi_mahi__get_workflow(workflowId)` pour obtenir `currentPhase` et les métadonnées du workflow.

3. Dispatcher l'agent spec-reviewer en mode rapport uniquement :

```
Agent({
  description: "Revue spec/code de <spec-id>",
  subagent_type: "mahi:spec-reviewer",
  prompt: "specId: <spec-id>
    specPath: <spec-path>
    worktreePath: <worktreePath ou null si pas encore en implementation>
    fix: false
    interactive: true"
})
```

4. Présenter le rapport de l'agent à l'utilisateur.

5. Si le rapport contient des corrections proposées ET que `--no-fix` n'est PAS passé :
   Demander : "Appliquer ces corrections ? (oui/non)"
   - Si oui : dispatcher à nouveau l'agent avec `fix: true` et `interactive: true`, confirmer les corrections appliquées.
   - Si non : terminé — rapport présenté, aucune modification.

## Related Skills

| Skill | Purpose |
|-------|---------|
| `/status` | Vue d'ensemble de tous les workflows |
| `/spec recap` | Briefing complet de la spec active avec contexte |
