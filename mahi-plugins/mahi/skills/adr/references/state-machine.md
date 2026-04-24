# State Machine — ADR

## États valides (côté serveur)

| État serveur | Nom affiché | Description |
|---|---|---|
| `FRAMING` | framing | Définir le problème, les contraintes, les non-objectifs |
| `EXPLORING` | exploration | Identifier et analyser les options possibles |
| `DISCUSSING` | discussion | Comparer les options, converger vers une décision |
| `DECIDING` | decision | Formaliser la décision dans l'ADR |
| `RETROSPECTIVE` | retrospective | Valider les règles candidates découvertes pendant l'ADR |
| `DONE` | completed | ADR finalisé, prêt pour l'implémentation |

## Événements et transitions

| Depuis | Événement | Vers | Guard |
|---|---|---|---|
| `FRAMING` | `START_EXPLORATION` | `EXPLORING` | artefact `framing` doit être VALID |
| `EXPLORING` | `START_DISCUSSION` | `DISCUSSING` | artefact `options` doit être VALID |
| `DISCUSSING` | `FORMALIZE_DECISION` | `DECIDING` | aucun guard |
| `DECIDING` | `START_RETROSPECTIVE` | `RETROSPECTIVE` | artefact `adr` doit être VALID |
| `RETROSPECTIVE` | `COMPLETE` | `DONE` | aucun guard |

Aucune autre transition n'est valide. Les phases ne peuvent pas être sautées.

## Artefacts et invalidation

| Artefact modifié | Invalide |
|---|---|
| `framing` | `options`, `adr` |
| `options` | `adr` |

**Conséquence :** si le framing est réécrit après exploration, `options.md` et `adr.md` doivent être refaits — le guard bloquera `START_DISCUSSION` jusqu'à ce que `write_artifact("options", ...)` soit rappelé.

## Procédure de transition (via MCP)

Pour avancer de la phase X à la phase Y :

1. Écrire le contenu de l'artefact de sortie dans le fichier physique (ex: `framing.md`)
2. Appeler `mcp__plugin_mahi_mahi__write_artifact(flowId, "<artefact>", <contenu>)` — marque l'artefact VALID côté serveur (requis pour les transitions guardées)
3. Appeler `mcp__plugin_mahi_mahi__fire_event(workflowId, "<EVENT>")` — effectue la transition
4. Si le serveur retourne une erreur : afficher en français, stopper — ne pas tenter de transition locale
5. Appeler `mcp__plugin_mahi_mahi__update_registry(adrId, "adr", <newPhase>)` pour mettre à jour le registre

## Workflow JSON — structure de référence

Le workflow est géré par le serveur Mahi. Structure retournée par `get_workflow(workflowId)` :

```json
{
  "schemaVersion": "1.0.0",
  "specId": "gestion-secrets-spring",
  "title": "Gestion des secrets Spring",
  "currentPhase": "FRAMING",
  "createdAt": "2026-04-10T12:00:00Z",
  "updatedAt": "2026-04-10T12:00:00Z",
  "phases": {
    "FRAMING":       { "status": "in-progress", "startedAt": "2026-04-10T12:00:00Z" },
    "EXPLORING":     { "status": "pending" },
    "DISCUSSING":    { "status": "pending" },
    "DECIDING":      { "status": "pending" },
    "RETROSPECTIVE": { "status": "pending" },
    "DONE":          { "status": "pending" }
  },
  "artifacts": {
    "framing":       { "valid": false },
    "options":       { "valid": false },
    "adr":           { "valid": false },
    "retrospective": { "valid": false }
  }
}
```
