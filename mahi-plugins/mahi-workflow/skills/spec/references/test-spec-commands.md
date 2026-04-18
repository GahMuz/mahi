# Vérification : appels MCP par commande spec

## Vue d'ensemble

Chaque commande `/spec` doit déléguer la gestion d'état au serveur Mahi via les outils MCP.
Aucune transition de phase ne doit être gérée localement par le LLM.

---

## `/spec new <titre>`

**Appel MCP attendu :**
```
mahi_create_workflow(type="spec", title="<titre>")
```

**Critères :**
- [ ] `mahi_create_workflow` est appelé avec `type="spec"`
- [ ] Le `workflowId` retourné est stocké dans `active.json`
- [ ] `state.json` n'est PAS créé
- [ ] La phase initiale est retournée par le serveur, pas définie localement

---

## `/spec open [titre]`

**Appel MCP attendu :**
```
mahi_get_workflow(workflowId)
```

**Critères :**
- [ ] `mahi_get_workflow` est appelé avec le `workflowId` depuis `active.json`
- [ ] La phase courante est lue depuis la réponse serveur
- [ ] Aucune lecture de `state.json` pour déterminer la phase
- [ ] Le briefing de contexte est construit depuis la réponse `mahi_get_workflow`

---

## `/spec approve`

**Appel MCP attendu :**
```
mahi_fire_event(workflowId, event="approve")
```

**Critères :**
- [ ] `mahi_get_workflow(workflowId)` est appelé pour lire la phase courante avant validation
- [ ] `mahi_fire_event` est appelé avec `event="approve"` pour déclencher la transition
- [ ] Si le serveur retourne une erreur (transition invalide), elle est affichée en français
- [ ] `state.json` n'est PAS mis à jour par le LLM
- [ ] Aucune logique locale de validation de transition (ex. `requirements → design`) dans SKILL.md

---

## `/spec close`

**Appel MCP attendu :**
```
mahi_fire_event(workflowId, event="close")
```

**Critères :**
- [ ] `mahi_fire_event` avec `event="close"` est appelé avant suppression de `active.json`
- [ ] `active.json` est supprimé après l'appel MCP
- [ ] Aucune sauvegarde locale de `state.json`

---

## `/spec discard`

**Appels MCP attendus (dans cet ordre) :**
```
mahi_remove_worktree(workflowId)
mahi_fire_event(workflowId, event="discard")
```

**Critères :**
- [ ] Confirmation explicite demandée à l'utilisateur avant tout appel MCP
- [ ] `mahi_remove_worktree` est appelé pour supprimer le worktree côté serveur
- [ ] `mahi_fire_event` avec `event="discard"` est appelé pour marquer le workflow comme annulé
- [ ] Le répertoire `.sdd/specs/YYYY/MM/<id>/` est supprimé localement
- [ ] `active.json` est supprimé
- [ ] La ligne correspondante est retirée de `.sdd/specs/registry.md`

---

## Règle générale

Le LLM ne valide JAMAIS localement si une transition est légale.
Toute tentative de transition invalide est rejetée par le serveur Mahi avec un message d'erreur.
Le LLM affiche ce message en français et s'arrête.
