# Vérification : appels MCP par commande spec

## Vue d'ensemble

Chaque commande `/spec` doit déléguer la gestion d'état au serveur Mahi via les outils MCP.
Aucune transition de phase ne doit être gérée localement par le LLM.

---

## `/spec new <titre>`

**Appels MCP attendus :**
```
mcp__plugin_mahi_mahi__create_workflow(type="spec", title="<titre>")
EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")
mcp__plugin_mahi_mahi__update_registry(specId, "spec", "requirements", title, period)
mcp__plugin_mahi_mahi__activate(specId, "spec", path, workflowId)
```

**Critères :**
- [ ] `mcp__plugin_mahi_mahi__create_workflow` est appelé avec `type="spec"`
- [ ] Le `workflowId` retourné est stocké dans `active.json`
- [ ] `EnterWorktree` est appelé avec `branch="spec/<username>/<spec-id>"` et `path=".worktrees/<spec-id>"`
- [ ] `mcp__plugin_mahi_mahi__update_registry` est appelé avec `specId`, `"spec"`, `"requirements"`, `title`, `period`
- [ ] `mcp__plugin_mahi_mahi__activate` est appelé avec `specId`, `type="spec"`, `path`, `workflowId`
- [ ] Le LLM n'écrit PAS `active.json` directement
- [ ] Le LLM n'écrit PAS de ligne dans `registry.json` directement
- [ ] `state.json` n'est PAS créé
- [ ] La phase initiale est retournée par le serveur, pas définie localement

---

## `/spec open [titre]`

**Appels MCP attendus :**
```
mcp__plugin_mahi_mahi__activate(specId, "spec", path, workflowId)
EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")
mcp__plugin_mahi_mahi__get_workflow(workflowId)
```

**Critères :**
- [ ] `mcp__plugin_mahi_mahi__activate` est appelé avec `specId`, `type="spec"`, `path`, `workflowId`
- [ ] `EnterWorktree` est appelé avec `branch="spec/<username>/<spec-id>"` et `path=".worktrees/<spec-id>"`
- [ ] `mcp__plugin_mahi_mahi__get_workflow` est appelé avec le `workflowId` depuis `active.json`
- [ ] La phase courante est lue depuis la réponse serveur
- [ ] Aucune lecture de `state.json` pour déterminer la phase
- [ ] Le LLM n'écrit PAS `active.json` directement
- [ ] Le briefing de contexte est construit depuis la réponse `mcp__plugin_mahi_mahi__get_workflow`

---

## `/spec approve`

**Appels MCP attendus (selon la phase courante) :**
```
# requirements → design
mcp__plugin_mahi_mahi__fire_event(workflowId, event="APPROVE_REQUIREMENTS")

# design → worktree
mcp__plugin_mahi_mahi__fire_event(workflowId, event="APPROVE_DESIGN")

# planning → implementation
mcp__plugin_mahi_mahi__fire_event(workflowId, event="APPROVE_PLANNING")

# implementation → finishing
mcp__plugin_mahi_mahi__fire_event(workflowId, event="APPROVE_IMPLEMENTATION")

# finishing → retrospective
mcp__plugin_mahi_mahi__fire_event(workflowId, event="APPROVE_FINISHING")

# retrospective → completed
mcp__plugin_mahi_mahi__fire_event(workflowId, event="APPROVE_RETROSPECTIVE")

mcp__plugin_mahi_mahi__update_registry(specId, "spec", <newPhase>)
mcp__plugin_mahi_mahi__update_state(specPath, <newPhase>, changelogEntry)
```

**Critères :**
- [ ] `mcp__plugin_mahi_mahi__get_workflow(workflowId)` est appelé pour lire la phase courante avant validation
- [ ] `mcp__plugin_mahi_mahi__fire_event` est appelé avec l'événement spécifique à la phase (ex. `APPROVE_REQUIREMENTS`, pas `approve`)
- [ ] Si le serveur retourne une erreur (transition invalide), elle est affichée en français
- [ ] `mcp__plugin_mahi_mahi__update_registry` est appelé avec le nouveau statut après transition réussie
- [ ] `mcp__plugin_mahi_mahi__update_state` est appelé pour mettre à jour la phase dans `state.json`
- [ ] Le LLM n'écrit PAS `state.json` directement
- [ ] Le LLM n'écrit PAS dans `registry.json` directement
- [ ] Aucune logique locale de validation de transition (ex. `requirements → design`) dans SKILL.md

---

## `/spec close`

**Appels MCP attendus :**
```
ExitWorktree()
mcp__plugin_mahi_mahi__save_context(flowId, context)
mcp__plugin_mahi_mahi__deactivate()
```

**Critères :**
- [ ] `ExitWorktree()` est appelé avant la sauvegarde du contexte (Step 0 de CLOSE)
- [ ] `mcp__plugin_mahi_mahi__save_context` est appelé pour persister le contexte côté serveur
- [ ] `mcp__plugin_mahi_mahi__deactivate()` est appelé pour supprimer `active.json`
- [ ] Le LLM ne supprime PAS `active.json` directement
- [ ] Aucune sauvegarde locale de `state.json`

---

## `/spec discard`

**Appels MCP attendus (dans cet ordre) :**
```
mcp__plugin_mahi_mahi__remove_worktree(workflowId)
mcp__plugin_mahi_mahi__update_registry(specId, "spec", "discarded")
ExitWorktree()
mcp__plugin_mahi_mahi__deactivate()
```

**Critères :**
- [ ] Confirmation explicite demandée à l'utilisateur avant tout appel MCP
- [ ] `mcp__plugin_mahi_mahi__remove_worktree` est appelé pour supprimer le worktree côté serveur
- [ ] `mcp__plugin_mahi_mahi__update_registry` est appelé avec `specId`, `"spec"`, `"discarded"` pour marquer l'entrée en registry
- [ ] Le répertoire `.mahi/specs/YYYY/MM/<id>/` est supprimé localement
- [ ] `ExitWorktree()` est appelé avant la désactivation
- [ ] `mcp__plugin_mahi_mahi__deactivate()` est appelé pour supprimer `active.json`
- [ ] Le LLM ne supprime PAS `active.json` directement
- [ ] Le LLM n'écrit PAS dans `registry.json` directement
- [ ] Aucun `fire_event` n'est appelé pour "discard" (événement inexistant dans le FSM)

---

## `/spec recap`

**Appels MCP attendus :**
```
mcp__plugin_mahi_mahi__get_active()
mcp__plugin_mahi_mahi__get_workflow(workflowId)
```

**Critères :**
- [ ] `mcp__plugin_mahi_mahi__get_active()` est appelé pour vérifier qu'un spec est actif
- [ ] `mcp__plugin_mahi_mahi__get_workflow(workflowId)` est appelé pour récupérer la phase et les artifacts
- [ ] Le briefing complet (phase, REQs, DESs, tasks en cours) est construit depuis la réponse serveur
- [ ] Aucune lecture directe de `state.json`

---

## `/spec clarify`

**Appels MCP attendus :**
```
# Pour un changement de requirement :
mcp__plugin_mahi_mahi__add_requirement_info(flowId, info: "<résumé>")

# Pour un changement de design :
mcp__plugin_mahi_mahi__add_design_info(flowId, info: "<résumé>")
```

**Critères :**
- [ ] `mcp__plugin_mahi_mahi__get_active()` est appelé pour vérifier qu'un spec est actif
- [ ] Les documents impactés (requirement.md / design.md / plan.md) sont modifiés en place
- [ ] `mcp__plugin_mahi_mahi__add_requirement_info` ou `mcp__plugin_mahi_mahi__add_design_info` est appelé selon le type de changement
- [ ] Aucun `fire_event` n'est appelé (pas d'événement "clarify" dans le FSM)
- [ ] La propagation en cascade est effectuée (REQ → DES → TASK)
- [ ] Un rapport de propagation est présenté en français

---

## `/spec split`

**Appels MCP attendus :**
```
# Crée un nouveau workflow pour le sous-spec
mcp__plugin_mahi_mahi__create_workflow(flowId=<new-spec-id>, workflowType="spec")
mcp__plugin_mahi_mahi__update_registry(newSpecId, "spec", "requirements", newTitle, period)
mcp__plugin_mahi_mahi__activate(newSpecId, "spec", newPath, newWorkflowId)
```

**Critères :**
- [ ] `mcp__plugin_mahi_mahi__get_active()` est appelé pour vérifier qu'un spec est actif
- [ ] `references/protocol-split.md` est suivi pour la logique de découpage
- [ ] Le spec parent est mis à jour (tâches extraites retirées ou marquées)
- [ ] Un nouveau workflow est créé pour le sous-spec via `mcp__plugin_mahi_mahi__create_workflow`
- [ ] Le registre est mis à jour avec la nouvelle entrée

---

## `/spec switch <titre>`

**Appels MCP attendus :**
```
# Fermeture du spec courant (comme /spec close)
mcp__plugin_mahi_mahi__save_context(flowId, context)
ExitWorktree()
mcp__plugin_mahi_mahi__deactivate()

# Ouverture du nouveau spec (comme /spec open)
mcp__plugin_mahi_mahi__activate(specId, "spec", path, workflowId)
EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")
mcp__plugin_mahi_mahi__get_workflow(workflowId)
```

**Critères :**
- [ ] Le spec courant est correctement fermé (contexte sauvegardé) avant ouverture du nouveau
- [ ] `mcp__plugin_mahi_mahi__deactivate()` est appelé pour le spec courant
- [ ] `mcp__plugin_mahi_mahi__activate` est appelé pour le nouveau spec
- [ ] `EnterWorktree` est appelé pour entrer dans le worktree du nouveau spec
- [ ] Le briefing est présenté après le switch

---

## Règle générale

Le LLM ne valide JAMAIS localement si une transition est légale.
Toute tentative de transition invalide est rejetée par le serveur Mahi avec un message d'erreur.
Le LLM affiche ce message en français et s'arrête.
