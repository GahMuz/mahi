# Exigences : mcp-for-spec — Extension du serveur Mahi MCP

## Contexte

Le serveur Mahi MCP (spec `mahi`, completed) gère actuellement la machine d'état de workflow
via des outils coarse-grained (`mahi_write_artifact` stocke un Markdown brut, `mahi_fire_event`
avance la phase). Ce spec étend le serveur pour gérer les artefacts de manière structurée,
valider la cohérence du spec, persister le contexte de session, et exposer les durées de phase.

---

## REQ-001 — Opérations granulaires sur les exigences

**QUAND** un agent rédige ou met à jour une exigence
**ALORS** le serveur DOIT exposer des outils pour manipuler chaque exigence individuellement

**Outils requis :**
- `mahi_add_requirement(flowId, req)` — ajouter une exigence structurée
- `mahi_update_requirement(flowId, reqId, req)` — mettre à jour une exigence existante
- `mahi_list_requirements(flowId)` — lister toutes les exigences avec leurs statuts
- `mahi_get_requirement(flowId, reqId)` — obtenir une exigence complète

**Structure d'une exigence :**
```json
{
  "id": "REQ-001",
  "title": "...",
  "priority": "must | should | could",
  "status": "VALID | STALE | MISSING",
  "acceptanceCriteria": [
    { "id": "REQ-001.AC-1", "description": "..." },
    { "id": "REQ-001.AC-2", "description": "..." }
  ],
  "content": "Markdown libre (description, contexte)"
}
```

**Critères d'acceptance :**
- REQ-001.AC-1 : `mahi_add_requirement` retourne une erreur si l'ID existe déjà
- REQ-001.AC-2 : `mahi_update_requirement` déclenche la propagation STALE (voir REQ-003)
- REQ-001.AC-3 : `mahi_list_requirements` retourne IDs, titres et statuts — pas le contenu Markdown ni le détail des AC
- REQ-001.AC-4 : le champ `content` est stocké tel quel — le serveur ne le parse pas
- REQ-001.AC-5 : les IDs des AC suivent le format `<reqId>.AC-<n>` (ex. `REQ-001.AC-1`)

---

## REQ-002 — Opérations granulaires sur les éléments de design

**QUAND** un agent rédige ou met à jour un élément de design
**ALORS** le serveur DOIT exposer des outils pour manipuler chaque élément individuellement

**Outils requis :**
- `mahi_add_design_element(flowId, des)` — ajouter un élément de design
- `mahi_update_design_element(flowId, desId, des)` — mettre à jour un élément
- `mahi_list_design_elements(flowId)` — lister tous les éléments avec leurs statuts
- `mahi_get_design_element(flowId, desId)` — obtenir un élément complet

**Structure d'un élément de design :**
```json
{
  "id": "DES-001",
  "title": "...",
  "status": "VALID | STALE | MISSING",
  "coversAC": ["REQ-001.AC-1", "REQ-002.AC-3"],
  "implementedBy": ["TASK-001"],
  "content": "Markdown libre"
}
```

**Note :** `coversAC` peut référencer des AC de plusieurs REQ différents (DES transversal).

**Critères d'acceptance :**
- REQ-002.AC-1 : `mahi_add_design_element` requiert au moins un `coversAC` référençant des AC existantes
- REQ-002.AC-2 : `mahi_update_design_element` déclenche la propagation STALE sur les TASKs (voir REQ-003)
- REQ-002.AC-3 : `mahi_list_design_elements` retourne IDs, titres, statuts et `coversAC` — pas le contenu Markdown
- REQ-002.AC-4 : un DES peut couvrir des AC appartenant à des REQ différents

---

## REQ-003 — Cohérence — propagation STALE automatique

**QUAND** une exigence est mise à jour via `mahi_update_requirement`
**ALORS** le serveur DOIT passer en STALE tous les éléments de design qui couvrent au moins un AC de cette exigence

**QUAND** un élément de design est mis à jour via `mahi_update_design_element`
**ALORS** le serveur DOIT passer en STALE toutes les tâches qui le référencent

**Critères d'acceptance :**
- REQ-003.AC-1 : la propagation est immédiate et synchrone (avant le retour de l'outil)
- REQ-003.AC-2 : seuls les éléments qui référencent directement l'élément modifié sont invalidés
- REQ-003.AC-3 : la réponse de l'outil inclut la liste des éléments passés STALE (`{ "stalePropagated": ["DES-002", "DES-005"] }`)
- REQ-003.AC-4 : un élément déjà STALE ou MISSING n'est pas re-signalé dans `stalePropagated`

---

## REQ-004 — Cohérence — vérification de présence et couverture des AC

**QUAND** `mahi_check_coherence(flowId)` est appelé
**ALORS** le serveur DOIT retourner la liste des violations de cohérence

**Violations détectées :**
- AC orpheline : un AC d'un REQ n'est couvert par aucun DES → `"REQ-001.AC-2 n'est couverte par aucun élément de design"`
- DES sans AC : un DES n'a aucun `coversAC` → `"DES-003 ne couvre aucun critère d'acceptance"`
- REQ sans AC : un REQ n'a aucun `acceptanceCriteria` → `"REQ-004 n'a aucun critère d'acceptance défini"`
- AC inexistante : un DES référence un AC qui n'existe pas → `"DES-002 référence REQ-001.AC-9 qui n'existe pas"`

**Critères d'acceptance :**
- REQ-004.AC-1 : retourne un tableau vide si aucune violation
- REQ-004.AC-2 : chaque violation précise le type, l'ID concerné et un message lisible en français
- REQ-004.AC-3 : `mahi_fire_event(flowId, "approve")` en phase design appelle automatiquement `check_coherence` et refuse la transition si des violations existent

---

## REQ-005 — Contexte de session persisté

**QUAND** `/spec close` est invoqué
**ALORS** le skill DOIT appeler `mahi_save_context(flowId, context)` pour persister le contexte de session

**QUAND** `mahi_get_workflow(flowId)` est appelé après une réouverture
**ALORS** la réponse DOIT inclure le contexte de session sauvegardé

**Structure du contexte :**
```json
{
  "savedAt": "ISO-8601",
  "lastAction": "Description de la dernière action significative",
  "keyDecisions": ["Décision 1", "Décision 2"],
  "openQuestions": ["Question en suspens"],
  "nextStep": "Ce qui était prévu pour la prochaine session"
}
```

**Critères d'acceptance :**
- REQ-005.AC-1 : `mahi_save_context` écrase le contexte précédent (un seul contexte actif par workflow)
- REQ-005.AC-2 : `mahi_get_workflow` inclut le champ `sessionContext` si un contexte existe
- REQ-005.AC-3 : le contexte est persisté dans `.sdd/specs/YYYY/MM/<id>/context.md` (lisible par un humain)
- REQ-005.AC-4 : si aucun contexte sauvegardé, `sessionContext` est absent de la réponse (pas `null`)

---

## REQ-006 — Métriques de durée par phase

**QUAND** `mahi_get_workflow(flowId)` est appelé
**ALORS** la réponse DOIT inclure les durées de chaque phase complétée, calculées depuis l'historique de transitions

**Format :**
```json
{
  "phaseDurations": {
    "requirements": 2820000,
    "design": 1380000,
    "planning": 480000,
    "implementation": 15120000
  }
}
```
(valeurs en millisecondes)

**Critères d'acceptance :**
- REQ-006.AC-1 : aucun nouveau tool call requis — calculé depuis les `TransitionRecord` existants
- REQ-006.AC-2 : phase en cours → durée depuis l'entrée jusqu'à `now` (durée partielle)
- REQ-006.AC-3 : phase non encore atteinte → absente du dictionnaire
- REQ-006.AC-4 : si la même phase est entrée plusieurs fois (ex. retour arrière), les durées s'additionnent

---

## REQ-NF-001 — Convention de nommage des outils MCP

Tous les nouveaux outils DOIVENT suivre le pattern `mahi_<verbe>_<entité>` en snake_case.

---

## Couverture

| Axe | REQs |
|-----|------|
| Opérations granulaires exigences | REQ-001 |
| Opérations granulaires design | REQ-002 |
| Cohérence propagation STALE | REQ-003 |
| Cohérence présence et couverture AC | REQ-004 |
| Contexte de session | REQ-005 |
| Métriques durée de phase | REQ-006 |
| Convention de nommage | REQ-NF-001 |