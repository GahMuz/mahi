---
name: bug-hunt
description: "This skill should be used when the user invokes '/bug-hunt' to investigate a functional scope and identify bugs — without fixing them. Accepts a vague description, exchanges with the developer to define scope via core classes, analyzes only confirmed classes for token efficiency (Grep-first + 6-layer analysis), and proposes creating individual /debug sessions per identified bug (each validated individually)."
argument-hint: "new <titre> [description] | open [titre] | approve | recap | close"
context: fork
allowed-tools: ["Read", "Write", "Glob", "Grep", "Agent", "AskUserQuestion", "mcp__plugin_mahi_mahi__*"]
---

# Bug Hunt Workflow Orchestrator

All communication with the user MUST be in French.

## RÈGLES ABSOLUES — non-négociables

1. **MCP only.** Toutes les transitions d'état passent par `mcp__plugin_mahi_mahi__fire_event`. Jamais de logique FSM locale.

2. **write_artifact AVANT fire_event.** Toujours écrire l'artifact de la phase via `mcp__plugin_mahi_mahi__write_artifact` avant d'appeler `fire_event`. Les guards vérifient que l'artifact est VALID.

3. **Erreur serveur → stop.** Si `fire_event` retourne une erreur, afficher en français et stopper. Ne jamais contourner.

4. **Pas de lecture directe de `active.json`.** Toujours passer par `mcp__plugin_mahi_mahi__get_active()`.

5. **Read-only.** Bug-hunt est une investigation sans modification de code source. Aucune écriture dans des fichiers source ou de test.

6. **Contrainte MCP active item.** Pendant une session bug-hunt active, ne pas appeler `write_artifact` pour des sessions debug — utiliser le Write tool directement pour créer les fichiers `prefill.md`.

## Active Item

La session bug-hunt courante est trackée dans `.mahi/.local/active.json` — gitignored, local. Lire via `mcp__plugin_mahi_mahi__get_active()`.

```json
{ "type": "bug-hunt", "id": "mon-hunt", "path": ".mahi/bug-hunt/YYYY/MM/mon-hunt", "activatedAt": "ISO-8601", "workflowId": "<uuid>" }
```

Un seul item actif à la fois. `new` et `open` ferment l'item courant avant d'en ouvrir un nouveau.

## Parse Arguments

Format : `/bug-hunt <subcommand> [<args>...]`

### `/bug-hunt new <titre> [<description libre>]`
- `<titre>` = 2-4 premiers mots kebab-case → `<hunt-id>`
- Tout ce qui suit = `INITIAL_DESCRIPTION` — stocker pour phase-scoping (contexte, pas une to-do list)
- Si la ligne après `new` dépasse 5 mots sans tirets, contient des verbes d'action ou fait plus d'une phrase → appeler `AskUserQuestion` pour confirmer le titre court

### `/bug-hunt open [<titre>]`
- `<titre>` optionnel, un seul mot kebab-case
- Si absent → lister les sessions `status != "completed" && type == "bug-hunt"`, demander le choix

### `/bug-hunt approve | recap | close`
- Pas d'argument attendu

---

Actions :
- `new <...>` → START_NEW
- `open [...]` → OPEN
- `approve` → APPROVE
- `recap` → RECAP
- `close` → CLOSE
- no args → CHECK_STATE

## CHECK_STATE

1. `mcp__plugin_mahi_mahi__get_active()`. Si présent : `get_workflow(workflowId)` → afficher la phase courante en français. Si absent : "Aucune session bug-hunt active — lancez `/bug-hunt new <titre>` ou `/bug-hunt open <titre>`."

## START_NEW

1. `mcp__plugin_mahi_mahi__get_active()`. Si présent → exécuter CLOSE, puis continuer.
2. Convertir `<titre>` en kebab-case → `<hunt-id>`. Récupérer `YYYY/MM` depuis la date du jour.
3. Créer `.mahi/bug-hunt/YYYY/MM/<hunt-id>/` et écrire `log.md` :
   ```
   # Log bug-hunt : <titre>
   Créé le <date>
   Description initiale : <résumé 1 ligne de INITIAL_DESCRIPTION>
   ```
4. `mcp__plugin_mahi_mahi__create_workflow(flowId=<hunt-id>, workflowType="bug-hunt")` → stocker `workflowId`.
5. `mcp__plugin_mahi_mahi__update_registry(huntId, "bug-hunt", "scoping", title, period)`.
6. `mcp__plugin_mahi_mahi__activate(huntId, "bug-hunt", ".mahi/bug-hunt/YYYY/MM/<hunt-id>", workflowId)`.
7. Lire et suivre `references/phase-scoping.md` avec `INITIAL_DESCRIPTION` comme contexte initial.

## OPEN

1. Lire `.mahi/work/registry.json`. Titre donné → trouver l'entrée `type == "bug-hunt"`. Absent → lister les sessions non complétées, demander le choix.
2. `mcp__plugin_mahi_mahi__get_active()`. Si présent avec id différent → exécuter CLOSE.
3. `mcp__plugin_mahi_mahi__activate(huntId, "bug-hunt", path, workflowId)`.
4. `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → `currentPhase`. Si serveur KO : "Le serveur Mahi ne répond pas. Vérifiez que le plugin `mahi` est actif et que le processus Java est lancé." et stopper.
5. Afficher un résumé de la session (titre, phase courante, dernier artifact écrit).
6. Charger la reference correspondant à la phase courante et reprendre.

## APPROVE

1. `mcp__plugin_mahi_mahi__get_active()`. Si null ou `type != "bug-hunt"` : "Aucune session bug-hunt active."
2. `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → `currentPhase`.
3. `mcp__plugin_mahi_mahi__fire_event(workflowId, <event>)` selon la phase :
   - scoping → `SCOPE_CONFIRMED`
   - hunting → `HUNT_DONE`
   - reporting → `COMPLETE`
4. `mcp__plugin_mahi_mahi__update_registry(huntId, "bug-hunt", <newPhase>)`.
5. Appender dans `log.md` : `[<date>] Phase <old> → <new>`.
6. Si phase suivante != done → charger la reference de la phase suivante et continuer.
7. Si DONE → exécuter CLOSE.

## RECAP

1. `mcp__plugin_mahi_mahi__get_active()`. Si null ou `type != "bug-hunt"` → fail.
2. `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → artifacts + phase courante.
3. Afficher en français :
   ```
   # Récap bug-hunt : <titre>
   Phase courante : <phase>

   ## Artifacts produits
   - scope    : [VALID | MISSING | STALE]
   - findings : [VALID | MISSING | STALE]
   - bug-list : [VALID | MISSING | STALE]

   ## Bugs identifiés
   <lire le dernier artifact VALID (findings ou scope) et lister les findings>
   ```

## CLOSE

1. `mcp__plugin_mahi_mahi__get_active()`. Si null → "Aucune session bug-hunt active."
2. Appender dans `log.md` : `[<date>] Session fermée — phase : <currentPhase>`.
3. `mcp__plugin_mahi_mahi__deactivate()` — supprimer `.mahi/.local/active.json`.
4. Confirmer : "Session bug-hunt '<titre>' sauvegardée. Rouvrir avec `/bug-hunt open <titre>`."

## Principes clés

**Read-only** : aucune modification de code source. Bug-hunt lit, analyse, et documente uniquement.

**Scope limité** : maximum 10 classes en scope simultané. Si le développeur propose plus → demander de prioriser ou de diviser en deux hunts.

**Validation individuelle** : chaque bug identifié est présenté individuellement au développeur avant la création d'une session `/debug`.

**prefill.md** : pour chaque bug validé, écrire `.mahi/debug/YYYY/MM/<debug-id>/prefill.md` via le Write tool (PAS `write_artifact` MCP — bug-hunt est l'item actif et le MCP rejetterait).

**Token efficiency** : Grep-first (ne lire que les zones suspectes), docs-over-code si disponible, analyse en parallèle des classes indépendantes.

## Phase References

| Phase | Reference |
|-------|-----------|
| scoping   | `references/phase-scoping.md` |
| hunting   | `references/phase-hunting.md` |
| reporting | `references/phase-reporting.md` |

## Related Skills

| Skill | Purpose |
|-------|---------|
| `/debug new <titre>` | Corriger un bug spécifique avec branche dédiée et TDD |
| `/debug open <titre>` | Reprendre une session debug (lit automatiquement le prefill.md si présent) |
| `/spec new <titre>` | Développer une nouvelle feature de façon structurée |
| `/status [--all]` | Vue d'ensemble de tous les workflows actifs |
