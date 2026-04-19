# Plan d'implémentation : mahi-workflow — Migration fonctionnelle de sdd-spec vers Mahi

## Règles à vérifier post-implémentation

- [x] **SOLID-S** : Chaque skill/agent a une responsabilité unique décrite en une phrase
- [x] **SOLID-O** : Les points d'extension (appels MCP) sont identifiés sans logique conditionnelle LLM
- [x] **SOLID-D** : Les skills délèguent les transitions à la FSM serveur (pas d'implémentation locale)
- [x] **MCP-STDIO** : Tous les appels MCP utilisent des outils standard (mahi_create_workflow, mahi_fire_event, etc.)
- [x] **Pas de state.json** : Aucune référence à state.json dans les fichiers mahi-workflow
- [x] **Pas de logique de transition LLM** : Aucun `requirements → design` validé en local

---

## Graphe de dépendances

```
TASK-001 (Structure plugin)
  └─> TASK-002 (active.json / workflowId)
        └─> TASK-003 (spec.md commandes)
              ├─> TASK-004 (mahi_write_artifact dans phases)
              ├─> TASK-005 (agents — suppression state.json)
              └─> TASK-006 (protocol-context.md)
TASK-007 (state-machine.md) [indépendant de TASK-002..006]
```

**Lot 1 (parallélisable) :** TASK-001 seule — doit être complétée en premier (crée la structure).
**Lot 2 (parallélisable) :** TASK-002 et TASK-007 (indépendants entre eux après TASK-001).
**Lot 3 (après TASK-002) :** TASK-003.
**Lot 4 (après TASK-003) :** TASK-004, TASK-005, TASK-006 en parallèle.

---

## TASK-001 : Créer la structure du plugin mahi-workflow

**Implémente :** DES-001
**Satisfait :** REQ-001, REQ-NF-001

### TASK-001.1 — Copier la structure sdd-spec dans mahi-plugins/mahi-workflow/

**Type :** Configuration (pas de RED/GREEN — copie de fichiers)

**Description :** Copier le répertoire du plugin sdd-spec (skills/spec/ et agents/) dans
`mahi-plugins/mahi-workflow/`, en préservant la structure.

**Fichiers à créer :**
- `mahi-plugins/mahi-workflow/skills/spec/spec.md` (copie de sdd-spec/skills/spec/spec.md)
- `mahi-plugins/mahi-workflow/skills/spec/references/` (copie de toutes les références)
- `mahi-plugins/mahi-workflow/agents/` (copie de tous les agents)

**Source :** `C:\Users\User\.claude\plugins\cache\sdd-marketplace\sdd-spec\0.42.0\`

**Commande de vérification :**
```
ls mahi-plugins/mahi-workflow/skills/spec/references/
ls mahi-plugins/mahi-workflow/agents/
```

**Dépendances :** aucune

---

### TASK-001.2 — Créer plugin.json

**Type :** Configuration

**Description :** Créer `mahi-plugins/mahi-workflow/plugin.json` avec les métadonnées du plugin.

**Fichier :** `mahi-plugins/mahi-workflow/plugin.json`

**Contenu attendu :**
```json
{
  "name": "mahi-workflow",
  "version": "0.1.0",
  "description": "Mahi Workflow — SDD spec workflow plugin backed by the Mahi MCP state machine server. Replaces file-based state management (state.json, active.json) with strict server-side FSM via MCP calls.",
  "author": { "name": "vincent-bailly" },
  "keywords": ["workflow", "mcp", "sdd", "mahi", "spec", "state-machine", "fsm"]
}
```

**Commande de vérification :**
```
cat mahi-plugins/mahi-workflow/plugin.json | grep '"name"' | grep 'mahi-workflow'
cat mahi-plugins/mahi-workflow/plugin.json | grep '"workflow"'
```

**Dépendances :** TASK-001.1

---

### TASK-001.3 — Enregistrer dans mahi-marketplace/marketplace.json

**Type :** Configuration

**Description :** Ajouter une entrée `mahi-workflow` dans le tableau `plugins` de
`mahi-marketplace/marketplace.json`, en suivant le format de l'entrée `mahi` existante.

**Fichier :** `mahi-marketplace/marketplace.json`

**Commande de vérification :**
```
cat mahi-marketplace/marketplace.json | grep 'mahi-workflow'
```

**Dépendances :** TASK-001.2

---

## TASK-002 : Adapter active.json — ajout du champ workflowId

**Implémente :** DES-002
**Satisfait :** REQ-002, REQ-003, REQ-NF-002

### TASK-002.1 [RED] — Écrire le test de vérification du format active.json

**Type :** RED (test de vérification de contenu)

**Description :** Documenter et créer le critère de vérification : après `/spec new`, le fichier
`active.json` doit contenir `workflowId` et ne pas contenir `currentPhase`.

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/test-active-json.md`

**Contenu :**
```markdown
# Vérification : format de active.json

## Critères
- [ ] active.json contient le champ "workflowId" non nul
- [ ] active.json contient les champs "type", "id", "path", "activatedAt"
- [ ] active.json NE contient PAS de champ "currentPhase"

## Commande de vérification
cat .sdd/local/active.json | grep 'workflowId'
cat .sdd/local/active.json | grep -v 'currentPhase'
```

**Commande de vérification :**
```
ls mahi-plugins/mahi-workflow/skills/spec/references/test-active-json.md
```

**Dépendances :** TASK-001.1

---

### TASK-002.2 [GREEN] — Mettre à jour les instructions spec.md pour active.json

**Type :** GREEN (implémentation)

**Description :** Modifier `spec.md` pour que les étapes START_NEW et OPEN :
1. Écrivent `workflowId` dans `active.json` (champ ajouté)
2. Ne mentionnent plus `currentPhase` dans `active.json`
3. Lisent la phase via `mahi_get_workflow(workflowId)`

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/spec.md`
(Uniquement les sections START_NEW et OPEN relatives à `active.json`)

**Commande de vérification :**
```
grep 'workflowId' mahi-plugins/mahi-workflow/skills/spec/spec.md
grep -v 'currentPhase' mahi-plugins/mahi-workflow/skills/spec/spec.md | grep 'active.json'
```

**Dépendances :** TASK-002.1

---

## TASK-003 : Adapter spec.md — remplacement des opérations d'état par appels MCP

**Implémente :** DES-003
**Satisfait :** REQ-002, REQ-003, REQ-004, REQ-007, REQ-NF-002, REQ-NF-003

### TASK-003.1 [RED] — Documenter les critères de vérification des commandes MCP

**Type :** RED

**Description :** Créer `test-spec-commands.md` avec les critères de vérification pour
chaque commande (`new`, `open`, `approve`, `close`, `discard`).

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/test-spec-commands.md`

**Contenu attendu :**
- `/spec new` → `mahi_create_workflow` + stocker `workflowId`
- `/spec open` → `mahi_get_workflow(workflowId)` pour phase courante
- `/spec approve` → `mahi_fire_event(workflowId, "approve")`
- `/spec close` → `mahi_fire_event(workflowId, "close")`
- `/spec discard` → `mahi_remove_worktree` + `mahi_fire_event(workflowId, "discard")`

**Commande de vérification :**
```
ls mahi-plugins/mahi-workflow/skills/spec/references/test-spec-commands.md
```

**Dépendances :** TASK-002.2

---

### TASK-003.2 [GREEN] — Adapter START_NEW dans spec.md

**Type :** GREEN

**Description :** Remplacer dans la section START_NEW de `spec.md` :
- La création de `state.json` → `mahi_create_workflow(type="spec")` + stocker `workflowId`
- La lecture de phase locale → `mahi_get_workflow(workflowId)`

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/spec.md` (section START_NEW)

**Commande de vérification :**
```
grep 'mahi_create_workflow' mahi-plugins/mahi-workflow/skills/spec/spec.md
grep -v 'state.json' mahi-plugins/mahi-workflow/skills/spec/spec.md | head -20
```

**Dépendances :** TASK-003.1

---

### TASK-003.3 [GREEN] — Adapter OPEN dans spec.md

**Type :** GREEN

**Description :** Remplacer dans la section OPEN de `spec.md` :
- La lecture de `state.json` → `mahi_get_workflow(workflowId)` pour phase + briefing
- L'écriture de `active.json` → inclure `workflowId` sans `currentPhase`

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/spec.md` (section OPEN)

**Commande de vérification :**
```
grep 'mahi_get_workflow' mahi-plugins/mahi-workflow/skills/spec/spec.md
```

**Dépendances :** TASK-003.1

---

### TASK-003.4 [GREEN] — Adapter APPROVE dans spec.md

**Type :** GREEN

**Description :** Remplacer dans la section APPROVE de `spec.md` :
- Lecture phase courante : `mahi_get_workflow(workflowId)` au lieu de `state.json`
- Avancement phase : `mahi_fire_event(workflowId, "approve")`
- Gestion erreur : afficher le message serveur en français si transition invalide
- Supprimer toute mise à jour de `state.json`

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/spec.md` (section APPROVE)

**Commande de vérification :**
```
grep 'mahi_fire_event' mahi-plugins/mahi-workflow/skills/spec/spec.md
```

**Dépendances :** TASK-003.1

---

### TASK-003.5 [GREEN] — Adapter CLOSE et DISCARD dans spec.md

**Type :** GREEN

**Description :**
- CLOSE : remplacer sauvegarde état locale → `mahi_fire_event(workflowId, "close")` + supprimer `active.json`
- DISCARD : appeler `mahi_remove_worktree(workflowId)` + `mahi_fire_event(workflowId, "discard")`

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/spec.md` (sections CLOSE, DISCARD)

**Commande de vérification :**
```
grep 'mahi_remove_worktree' mahi-plugins/mahi-workflow/skills/spec/spec.md
grep -c 'mahi_fire_event' mahi-plugins/mahi-workflow/skills/spec/spec.md
```

**Dépendances :** TASK-003.1

---

### TASK-003.6 [REFACTOR] — Supprimer toutes les références à state.json dans spec.md

**Type :** REFACTOR

**Description :** Relire `spec.md` et supprimer toute référence résiduelle à `state.json`,
`currentPhase` comme stockage local, et toute logique de transition LLM.

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/spec.md`

**Commande de vérification :**
```
grep -n 'state.json' mahi-plugins/mahi-workflow/skills/spec/spec.md || echo "OK: aucune référence"
grep -n 'currentPhase' mahi-plugins/mahi-workflow/skills/spec/spec.md || echo "OK: aucune référence"
```

**Dépendances :** TASK-003.2, TASK-003.3, TASK-003.4, TASK-003.5

---

## TASK-004 : Adapter les références de phase — ajout de mahi_write_artifact

**Implémente :** DES-004
**Satisfait :** REQ-005, REQ-NF-003

### TASK-004.1 [GREEN] — Adapter phase-requirements.md

**Type :** GREEN

**Description :** Ajouter dans `phase-requirements.md`, après l'instruction d'écriture de
`requirement.md` :
```
Après écriture du fichier, appeler :
mahi_write_artifact(workflowId: <depuis active.json>, artifactKey: "requirement.md", content: <contenu>)
Par exigence finalisée, appeler mahi_add_requirement_info avec les métadonnées structurées.
```

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/phase-requirements.md`

**Commande de vérification :**
```
grep 'mahi_write_artifact' mahi-plugins/mahi-workflow/skills/spec/references/phase-requirements.md
grep 'mahi_add_requirement_info' mahi-plugins/mahi-workflow/skills/spec/references/phase-requirements.md
```

**Dépendances :** TASK-003.6

---

### TASK-004.2 [GREEN] — Adapter phase-design.md

**Type :** GREEN

**Description :** Ajouter dans `phase-design.md`, après l'instruction d'écriture de `design.md` :
```
Après écriture du fichier, appeler :
mahi_write_artifact(workflowId: <depuis active.json>, artifactKey: "design.md", content: <contenu>)
Par élément DES-xxx finalisé, appeler mahi_add_design_info avec les métadonnées structurées.
```

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/phase-design.md`

**Commande de vérification :**
```
grep 'mahi_write_artifact' mahi-plugins/mahi-workflow/skills/spec/references/phase-design.md
grep 'mahi_add_design_info' mahi-plugins/mahi-workflow/skills/spec/references/phase-design.md
```

**Dépendances :** TASK-003.6

---

### TASK-004.3 [GREEN] — Adapter phase-planning.md

**Type :** GREEN

**Description :** Ajouter dans `phase-planning.md`, après l'instruction d'écriture de `plan.md` :
```
Après écriture du fichier, appeler :
mahi_write_artifact(workflowId: <depuis active.json>, artifactKey: "plan.md", content: <contenu>)
```

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/phase-planning.md`

**Commande de vérification :**
```
grep 'mahi_write_artifact' mahi-plugins/mahi-workflow/skills/spec/references/phase-planning.md
```

**Dépendances :** TASK-003.6

---

### TASK-004.4 [GREEN] — Adapter protocol-clarify.md

**Type :** GREEN

**Description :** Ajouter dans `protocol-clarify.md`, après la modification du changelog local :
```
Appeler aussi : mahi_fire_event(workflowId, event="clarify", data={summary: ..., affectedItems: [...]})
```

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/protocol-clarify.md`

**Commande de vérification :**
```
grep 'mahi_fire_event' mahi-plugins/mahi-workflow/skills/spec/references/protocol-clarify.md
```

**Dépendances :** TASK-003.6

---

## TASK-005 : Adapter les agents — suppression des lectures de state.json

**Implémente :** DES-005
**Satisfait :** REQ-003, REQ-005, REQ-006, REQ-NF-002

### TASK-005.1 [GREEN] — Adapter spec-orchestrator.md

**Type :** GREEN

**Description :** Remplacer dans `spec-orchestrator.md` :
- `Read state.json` → `Call mahi_get_workflow(workflowId)` pour le routing de phase
- Supprimer toute référence à `currentPhase` local

**Fichier :** `mahi-plugins/mahi-workflow/agents/spec-orchestrator.md`

**Commande de vérification :**
```
grep -n 'state.json' mahi-plugins/mahi-workflow/agents/spec-orchestrator.md || echo "OK"
grep 'mahi_get_workflow' mahi-plugins/mahi-workflow/agents/spec-orchestrator.md
```

**Dépendances :** TASK-003.6

---

### TASK-005.2 [GREEN] — Adapter spec-requirements.md

**Type :** GREEN

**Description :** Ajouter dans `spec-requirements.md` :
- Après génération de `requirement.md` : instruction `mahi_write_artifact`
- Par exigence : instruction `mahi_add_requirement_info`

**Fichier :** `mahi-plugins/mahi-workflow/agents/spec-requirements.md`

**Commande de vérification :**
```
grep 'mahi_write_artifact' mahi-plugins/mahi-workflow/agents/spec-requirements.md
grep 'mahi_add_requirement_info' mahi-plugins/mahi-workflow/agents/spec-requirements.md
```

**Dépendances :** TASK-003.6

---

### TASK-005.3 [GREEN] — Adapter spec-design.md

**Type :** GREEN

**Description :** Ajouter dans `spec-design.md` :
- Après génération de `design.md` : instruction `mahi_write_artifact`
- Par élément DES-xxx : instruction `mahi_add_design_info`

**Fichier :** `mahi-plugins/mahi-workflow/agents/spec-design.md`

**Commande de vérification :**
```
grep 'mahi_write_artifact' mahi-plugins/mahi-workflow/agents/spec-design.md
grep 'mahi_add_design_info' mahi-plugins/mahi-workflow/agents/spec-design.md
```

**Dépendances :** TASK-003.6

---

### TASK-005.4 [GREEN] — Adapter spec-planner.md

**Type :** GREEN

**Description :** Ajouter dans `spec-planner.md` :
- Après génération de `plan.md` : instruction `mahi_write_artifact`
- Supprimer toute écriture dans `state.json`

**Fichier :** `mahi-plugins/mahi-workflow/agents/spec-planner.md`

**Commande de vérification :**
```
grep 'mahi_write_artifact' mahi-plugins/mahi-workflow/agents/spec-planner.md
grep -n 'state.json' mahi-plugins/mahi-workflow/agents/spec-planner.md || echo "OK"
```

**Dépendances :** TASK-003.6

---

## TASK-006 : Adapter protocol-context.md — sauvegarde et chargement de contexte

**Implémente :** DES-006
**Satisfait :** REQ-003, REQ-NF-002

### TASK-006.1 [GREEN] — Adapter la section CLOSE de protocol-context.md

**Type :** GREEN

**Description :** Remplacer dans la section CLOSE :
- Sauvegarde de `state.json` → `mahi_fire_event(workflowId, event="close")`
- Supprimer `active.json` (comportement inchangé)

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/protocol-context.md`

**Commande de vérification :**
```
grep 'mahi_fire_event' mahi-plugins/mahi-workflow/skills/spec/references/protocol-context.md
```

**Dépendances :** TASK-003.6

---

### TASK-006.2 [GREEN] — Adapter la section Chargement du contexte de protocol-context.md

**Type :** GREEN

**Description :** Remplacer dans la section Chargement du contexte (OPEN) :
- Lecture `state.json` → `mahi_get_workflow(workflowId)` pour phase, artifacts, statuts
- Utiliser la réponse comme briefing de contexte

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/protocol-context.md`

**Commande de vérification :**
```
grep 'mahi_get_workflow' mahi-plugins/mahi-workflow/skills/spec/references/protocol-context.md
grep -n 'state.json' mahi-plugins/mahi-workflow/skills/spec/references/protocol-context.md || echo "OK"
```

**Dépendances :** TASK-006.1

---

## TASK-007 : Adapter state-machine.md — note FSM serveur

**Implémente :** DES-007
**Satisfait :** REQ-004, REQ-NF-002

### TASK-007.1 [GREEN] — Ajouter la note mahi-workflow en en-tête de state-machine.md

**Type :** GREEN (pas de RED — ajout documentaire simple)

**Description :** Ajouter en tout début de `state-machine.md` (après le titre H1) :
```markdown
> Note mahi-workflow : cette FSM est implémentée côté serveur Mahi.
> Les transitions sont déclenchées via `mahi_fire_event` et validées par le serveur.
> Le LLM ne doit pas implémenter de logique de validation locale.
```

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/state-machine.md`

**Commande de vérification :**
```
head -10 mahi-plugins/mahi-workflow/skills/spec/references/state-machine.md | grep 'Note mahi-workflow'
```

**Dépendances :** TASK-001.1

---

### TASK-007.2 [REFACTOR] — Vérifier l'absence de validation de transition LLM dans state-machine.md

**Type :** REFACTOR

**Description :** Relire `state-machine.md` et supprimer tout texte impliquant que
le LLM doit valider les transitions localement.

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/state-machine.md`

**Commande de vérification :**
```
grep -n 'state.json' mahi-plugins/mahi-workflow/skills/spec/references/state-machine.md || echo "OK"
grep -in 'validate.*locally\|LLM.*valide\|LLM.*validates' mahi-plugins/mahi-workflow/skills/spec/references/state-machine.md || echo "OK"
```

**Dépendances :** TASK-007.1

---

## Couverture des exigences

| Exigence | TASKs | Statut |
|----------|-------|--------|
| REQ-001 | TASK-001 | [x] |
| REQ-002 | TASK-002, TASK-003 | [x] |
| REQ-003 | TASK-002, TASK-003, TASK-006 | [x] |
| REQ-004 | TASK-003, TASK-007 | [x] |
| REQ-005 | TASK-004, TASK-005 | [x] |
| REQ-006 | TASK-004, TASK-005 | [x] |
| REQ-007 | TASK-003 | [x] |
| REQ-NF-001 | TASK-001 | [x] |
| REQ-NF-002 | TASK-002, TASK-003, TASK-005, TASK-006, TASK-007 | [x] |
| REQ-NF-003 | TASK-003, TASK-004 | [x] |

---

## Résumé

**7 tâches parentes, 23 sous-tâches**

**Lot 1 :** TASK-001.1..3 séquentiellement (3 sous-tâches)
**Lot 2 :** TASK-002.1 + TASK-007.1 en parallèle (2 sous-tâches)
**Lot 3 :** TASK-002.2, TASK-003.1..6 séquentiellement (7 sous-tâches)
**Lot 4 :** TASK-004.1..4 + TASK-005.1..4 + TASK-006.1..2 + TASK-007.2 en parallèle (11 sous-tâches, limitées par parallelTaskLimit)

**Exceptions RED/GREEN documentées :**
- TASK-001 : configuration pure (copie de fichiers, plugin.json) — pas de RED
- TASK-007.1 : ajout documentaire simple — pas de RED
