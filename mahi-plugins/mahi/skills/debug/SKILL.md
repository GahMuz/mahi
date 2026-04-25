---
name: debug
description: "This skill should be used when the user invokes '/debug' to investigate and fix a bug using a structured workflow with a dedicated git branch. Accepts a stacktrace, a textual description, or both. Orchestrates 5 phases: reported → reproducing → analyzing → fixing → validating. Uses process-debugging methodology, TDD (RED test before fix), and targeted context loading from graphs, docs, and specs."
argument-hint: "new <titre> [stacktrace/description] | open [titre] | approve | recap | close"
context: fork
allowed-tools: ["Read", "Write", "Edit", "Bash", "Glob", "Grep", "Agent", "EnterWorktree", "ExitWorktree", "AskUserQuestion", "mcp__plugin_mahi_mahi__*"]
---

# Debug Workflow Orchestrator

All communication with the user MUST be in French.

## RÈGLES ABSOLUES — non-négociables

1. **MCP only.** Toutes les transitions d'état passent par `mcp__plugin_mahi_mahi__fire_event`. Jamais de logique FSM locale.

2. **write_artifact AVANT fire_event.** Toujours écrire l'artifact de la phase via `mcp__plugin_mahi_mahi__write_artifact` avant d'appeler `fire_event`. Les guards vérifient que l'artifact est VALID.

3. **Erreur serveur → stop.** Si `fire_event` retourne une erreur, afficher en français et stopper. Ne jamais contourner.

4. **Pas de lecture directe de `active.json`.** Toujours passer par `mcp__plugin_mahi_mahi__get_active()`.

5. **Pas de code en dehors du worktree.** Toutes les modifications de fichiers source se font dans `.worktrees/<debug-id>/`. Les artifacts de documentation vont dans `.mahi/artifacts/<flowId>/` via MCP.

## Active Item

La session debug courante est trackée dans `.mahi/local/active.json` — gitignored, local. Lire via `mcp__plugin_mahi_mahi__get_active()`.

```json
{ "type": "debug", "id": "mon-bug", "path": ".mahi/debug/2026/04/mon-bug", "activatedAt": "ISO-8601", "workflowId": "<uuid>" }
```

Un seul item actif à la fois. `new` et `open` ferment l'item courant avant d'en ouvrir un nouveau.

## Parse Arguments

Format : `/debug <subcommand> [<args>...]`

### `/debug new <titre> [<description/stacktrace>]`
- `<titre>` = 2-4 premiers mots kebab-case → `<debug-id>`
- Tout ce qui suit = `INPUT_CONTENT` (stacktrace, explication, ou mix) — stocker pour phase-reported
- Si la ligne entière après `new` semble être une description longue (verbes, ponctuation, > 5 mots sans tirets), appeler `AskUserQuestion` pour confirmer le titre court

### `/debug open [<titre>]`
- `<titre>` optionnel, un seul mot kebab-case
- Si absent → lister les sessions non terminées depuis `.mahi/registry.json`

### `/debug approve | recap | close`
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

1. `mcp__plugin_mahi_mahi__get_active()`. Si présent : `get_workflow(workflowId)` → afficher la phase courante en français. Si absent : "Aucune session debug active — lancez `/debug new <titre>` ou `/debug open <titre>`."

## START_NEW

1. `mcp__plugin_mahi_mahi__get_active()`. Si présent → exécuter CLOSE, puis continuer.
2. Convertir `<titre>` en kebab-case → `<debug-id>`. Récupérer `YYYY/MM` depuis la date du jour.
3. Créer `.mahi/debug/YYYY/MM/<debug-id>/` et écrire `log.md` :
   ```
   # Log debug : <titre>
   Créé le <date>
   Input initial : <résumé 1 ligne de INPUT_CONTENT>
   ```
4. `mcp__plugin_mahi_mahi__create_workflow(flowId=<debug-id>, workflowType="debug")` → stocker `workflowId`.
5. `EnterWorktree(branch="debug/<username>/<debug-id>", path=".worktrees/<debug-id>")` — crée la branche isolée.
6. `mcp__plugin_mahi_mahi__update_registry(debugId, "debug", "reported", title, period)`.
7. `mcp__plugin_mahi_mahi__activate(debugId, "debug", ".mahi/debug/YYYY/MM/<debug-id>", workflowId)`.
8. Lire et suivre `references/phase-reported.md` avec `INPUT_CONTENT` comme entrée.

## OPEN

1. Lire `.mahi/registry.json`. Titre donné → trouver l'entrée. Absent → lister les sessions `status != "completed" && type == "debug"`, demander le choix.
2. `mcp__plugin_mahi_mahi__get_active()`. Si présent avec id différent → exécuter CLOSE.
3. `mcp__plugin_mahi_mahi__activate(debugId, "debug", path, workflowId)`.
4. `EnterWorktree(branch="debug/<username>/<debug-id>", path=".worktrees/<debug-id>")`.
5. Si `.mahi/debug/YYYY/MM/<debug-id>/prefill.md` existe (session créée depuis un `/bug-hunt`) :
   → Lire ce fichier et l'utiliser comme contexte initial pour `references/phase-reported.md`
     (le développeur n'a pas besoin de re-saisir la description — elle est déjà structurée)
   → Afficher en français : "Contexte pré-rempli depuis un bug-hunt — vérifiez les informations
     ci-dessous et tapez `/debug approve` pour confirmer le bug-report."
   → Charger `references/phase-reported.md` : présenter le contenu du prefill.md pour
     confirmation, ne pas redemander les champs déjà remplis.
6. `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → `currentPhase`. Si serveur KO : "Le serveur Mahi ne répond pas." et stopper.
7. Afficher un résumé de la session (titre, phase courante, dernier artifact écrit).
8. Charger la reference correspondant à la phase courante.

## APPROVE

1. `mcp__plugin_mahi_mahi__get_active()`. Si null ou `type != "debug"` : "Aucune session debug active."
2. `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → `currentPhase`.
3. Vérifier l'artifact de la phase (doit être VALID — sinon le guard de `fire_event` rejettera) :
   - `reported` → artifact `bug-report`
   - `reproducing` → artifact `reproduction`
   - `analyzing` → artifact `root-cause`
   - `fixing` → artifact `fix`
   - `validating` → artifact `test-report`
4. `mcp__plugin_mahi_mahi__fire_event(workflowId, <event>)` selon la phase :
   - reported → `REPRODUCE`
   - reproducing → `ANALYZE`
   - analyzing → `FIX`
   - fixing → `VALIDATE`
   - validating → `CLOSE`
5. `mcp__plugin_mahi_mahi__update_registry(debugId, "debug", <newPhase>)`.
6. Appender dans `log.md` : `[<date>] Phase <old> → <new>`.
7. Si phase suivante != done → charger la reference de la phase suivante et continuer.
8. Si DONE → exécuter CLOSE.

## RECAP

1. `mcp__plugin_mahi_mahi__get_active()`. Si null → fail.
2. `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → artifacts + phase courante.
3. Afficher en français :
   ```
   # Récap debug : <titre>
   Phase courante : <phase>
   
   ## Artifacts produits
   - bug-report : [VALID | MISSING]
   - reproduction : [VALID | MISSING]
   - root-cause : [VALID | MISSING]
   - fix : [VALID | MISSING]
   - test-report : [VALID | MISSING]
   
   ## Résumé
   <lire les artifacts VALID et en extraire les points clés>
   ```

## CLOSE

1. `mcp__plugin_mahi_mahi__get_active()`. Si null → "Aucune session debug active."
2. Appender dans `log.md` : `[<date>] Session fermée — phase : <currentPhase>`.
3. `ExitWorktree()` — retourner sur la branche principale.
4. `mcp__plugin_mahi_mahi__deactivate()` — supprimer `.mahi/local/active.json`.
5. Confirmer : "Session debug '<titre>' sauvegardée. Rouvrir avec `/debug open <titre>`."

## Principes clés

**Feedback** : toujours dire en français où en est la session, ce qui vient d'être fait, et quelle est l'étape suivante.

**Worktree** : tout le code (tests, fix) est écrit dans `.worktrees/<debug-id>/`. Ne jamais modifier des fichiers source sur la branche principale pendant une session debug.

**Artifacts MCP** : les 5 artifacts (bug-report, reproduction, root-cause, fix, test-report) passent tous par `mcp__plugin_mahi_mahi__write_artifact`. Ce sont des documents de session, pas du code.

**Escalade** : si 3 tentatives de fix échouent → dispatcher `mahi:spec-deep-dive` avec tout le contexte accumulé (bug-report + root-cause + fix attempts).

## Phase References

| Phase | Reference |
|-------|-----------|
| reported | `references/phase-reported.md` |
| reproducing | `references/phase-reproducing.md` |
| analyzing | `references/phase-analyzing.md` |
| fixing | `references/phase-fixing.md` |
| validating | `references/phase-validating.md` |

## Related Skills

| Skill | Purpose |
|-------|---------|
| `/spec new <titre>` | Développer une nouvelle feature de façon structurée |
| `/adr new <titre>` | Documenter une décision d'architecture |
| `/status [--all]` | Vue d'ensemble de tous les workflows actifs |
| `/spec-review` | Vérifier la cohérence spec/code après un fix impactant |
