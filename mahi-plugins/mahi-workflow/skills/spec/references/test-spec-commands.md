# Vérification : appels MCP par commande spec

## Vue d'ensemble

Chaque commande `/spec` doit déléguer la gestion d'état au serveur Mahi via les outils MCP.
Aucune transition de phase ne doit être gérée localement par le LLM.

---

## `/spec new <titre>`

**Appels MCP attendus :**
```
mahi_create_workflow(type="spec", title="<titre>")
EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")
mahi_update_registry(specId, "requirements", title, period)
mahi_activate(specId, "spec", path, workflowId)
```

**Critères :**
- [ ] `mahi_create_workflow` est appelé avec `type="spec"`
- [ ] Le `workflowId` retourné est stocké dans `active.json`
- [ ] `EnterWorktree` est appelé avec `branch="spec/<username>/<spec-id>"` et `path=".worktrees/<spec-id>"`
- [ ] `mahi_update_registry` est appelé avec `specId`, `"requirements"`, `title`, `period`
- [ ] `mahi_activate` est appelé avec `specId`, `type="spec"`, `path`, `workflowId`
- [ ] Le LLM n'écrit PAS `active.json` directement
- [ ] Le LLM n'écrit PAS de ligne dans `registry.md` directement
- [ ] `state.json` n'est PAS créé
- [ ] La phase initiale est retournée par le serveur, pas définie localement

---

## `/spec open [titre]`

**Appels MCP attendus :**
```
mahi_activate(specId, "spec", path, workflowId)
EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")
mahi_get_workflow(workflowId)
```

**Critères :**
- [ ] `mahi_activate` est appelé avec `specId`, `type="spec"`, `path`, `workflowId`
- [ ] `EnterWorktree` est appelé avec `branch="spec/<username>/<spec-id>"` et `path=".worktrees/<spec-id>"`
- [ ] `mahi_get_workflow` est appelé avec le `workflowId` depuis `active.json`
- [ ] La phase courante est lue depuis la réponse serveur
- [ ] Aucune lecture de `state.json` pour déterminer la phase
- [ ] Le LLM n'écrit PAS `active.json` directement
- [ ] Le briefing de contexte est construit depuis la réponse `mahi_get_workflow`

---

## `/spec approve`

**Appels MCP attendus :**
```
mahi_fire_event(workflowId, event="approve")
mahi_update_registry(specId, <newPhase>)
mahi_update_state(specPath, <newPhase>, changelogEntry)
```

**Critères :**
- [ ] `mahi_get_workflow(workflowId)` est appelé pour lire la phase courante avant validation
- [ ] `mahi_fire_event` est appelé avec `event="approve"` pour déclencher la transition
- [ ] Si le serveur retourne une erreur (transition invalide), elle est affichée en français
- [ ] `mahi_update_registry` est appelé avec le nouveau statut après transition réussie
- [ ] `mahi_update_state` est appelé pour mettre à jour la phase dans `state.json`
- [ ] Le LLM n'écrit PAS `state.json` directement
- [ ] Le LLM n'écrit PAS dans `registry.md` directement
- [ ] Aucune logique locale de validation de transition (ex. `requirements → design`) dans SKILL.md

---

## `/spec close`

**Appels MCP attendus :**
```
ExitWorktree()
mahi_save_context(flowId, context)
mahi_deactivate()
```

**Critères :**
- [ ] `ExitWorktree()` est appelé avant la sauvegarde du contexte (Step 0 de CLOSE)
- [ ] `mahi_save_context` est appelé pour persister le contexte côté serveur
- [ ] `mahi_deactivate()` est appelé pour supprimer `active.json`
- [ ] Le LLM ne supprime PAS `active.json` directement
- [ ] Aucune sauvegarde locale de `state.json`

---

## `/spec discard`

**Appels MCP attendus (dans cet ordre) :**
```
mahi_remove_worktree(workflowId)
mahi_fire_event(workflowId, event="discard")
mahi_update_registry(specId, "discarded")
ExitWorktree()
mahi_deactivate()
```

**Critères :**
- [ ] Confirmation explicite demandée à l'utilisateur avant tout appel MCP
- [ ] `mahi_remove_worktree` est appelé pour supprimer le worktree côté serveur
- [ ] `mahi_fire_event` avec `event="discard"` est appelé pour marquer le workflow comme annulé
- [ ] `mahi_update_registry` est appelé avec `"discarded"` pour marquer la ligne en registry
- [ ] Le répertoire `.sdd/specs/YYYY/MM/<id>/` est supprimé localement
- [ ] `ExitWorktree()` est appelé avant la suppression
- [ ] `mahi_deactivate()` est appelé pour supprimer `active.json`
- [ ] Le LLM ne supprime PAS `active.json` directement
- [ ] Le LLM n'écrit PAS dans `registry.md` directement

---

## Règle générale

Le LLM ne valide JAMAIS localement si une transition est légale.
Toute tentative de transition invalide est rejetée par le serveur Mahi avec un message d'erreur.
Le LLM affiche ce message en français et s'arrête.
