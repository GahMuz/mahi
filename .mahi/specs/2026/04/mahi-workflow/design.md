# Design : mahi-workflow — Migration fonctionnelle de sdd-spec vers Mahi

## Contexte

Spec lié : **mahi** (complété) — fournit le serveur MCP Mahi avec les outils
`mahi_create_workflow`, `mahi_get_workflow`, `mahi_fire_event`, `mahi_write_artifact`,
`mahi_add_requirement_info`, `mahi_add_design_info`, `mahi_create_worktree`, `mahi_remove_worktree`.

**Source :** Plugin `sdd-spec` (sdd-marketplace v0.42.0) — gestion d'état par fichiers locaux
(`state.json`, `active.json`), transitions validées par le LLM.

**Cible :** Plugin `mahi-workflow` dans `mahi-plugins/mahi-workflow/` — gestion d'état déléguée
à la FSM stricte du serveur Mahi via appels MCP.

**Principe de migration :** copier sdd-spec comme base, puis éliminer systématiquement
toute lecture/écriture de `state.json` et toute logique de transition LLM, en les remplaçant
par les appels MCP appropriés.

---

## DES-001 : Structure du plugin mahi-workflow

**Problème :** Créer le répertoire `mahi-plugins/mahi-workflow/` avec la structure attendue
par le système de plugins Mahi et le sdd-marketplace, en partant de la structure de sdd-spec.

**Approche retenue :** Copie de la structure sdd-spec, adaptée au format plugin Mahi.

**Structure cible du répertoire :**

```
mahi-plugins/mahi-workflow/
├── plugin.json                    # Métadonnées du plugin (commité)
├── skills/
│   └── spec/
│       ├── SKILL.md               # Point d'entrée du skill /spec (orchestrateur) — format standard plugins Mahi
│       └── references/            # Fichiers de phase et protocoles (adaptés)
│           ├── phase-requirements.md
│           ├── phase-design.md
│           ├── phase-worktree.md
│           ├── phase-planning.md
│           ├── phase-execution.md
│           ├── phase-finish.md
│           ├── phase-retro.md
│           ├── protocol-clarify.md
│           ├── protocol-context.md
│           ├── protocol-resume.md
│           ├── protocol-split.md
│           └── state-machine.md
└── agents/
    ├── spec-orchestrator.md
    ├── spec-requirements.md
    ├── spec-design.md
    ├── spec-design-validator.md
    ├── spec-planner.md
    ├── spec-task-implementer.md
    ├── spec-reviewer.md
    ├── spec-code-reviewer.md
    └── spec-deep-dive.md
```

**Contenu de `plugin.json` :**

```json
{
  "name": "mahi-workflow",
  "version": "0.1.0",
  "description": "Mahi Workflow — SDD spec workflow plugin backed by the Mahi MCP state machine server. Replaces file-based state management (state.json, active.json) with strict server-side FSM via MCP calls.",
  "author": {
    "name": "vincent-bailly"
  },
  "keywords": ["workflow", "mcp", "sdd", "mahi", "spec", "state-machine", "fsm"]
}
```

**Enregistrement dans `mahi-marketplace/marketplace.json` :**
Ajout d'une entrée dans le tableau `plugins` avec les mêmes champs que l'entrée `mahi`.

**Implémente :** REQ-001

**Contrat de test :**
- Le répertoire `mahi-plugins/mahi-workflow/` existe dans le repo
- `plugin.json` contient les champs obligatoires avec les keywords requis
- La structure `skills/spec/` et `agents/` est présente
- `mahi-marketplace/marketplace.json` contient une entrée `mahi-workflow`

---

## DES-002 : Remplacement de `active.json` — stockage du `workflowId`

**Problème :** Dans sdd-spec, `.sdd/local/active.json` stocke `type`, `id`, `path` et `currentPhase`.
Dans mahi-workflow, la phase courante est lue via `mahi_get_workflow` — `active.json` ne doit
plus stocker que les informations nécessaires pour retrouver le workflow sur le serveur.

**Options considérées :**

Option A — Supprimer totalement `active.json`
- Avantages : suppression complète de la gestion d'état locale
- Inconvénients : nécessite de lister les workflows MCP pour retrouver le workflow actif —
  le serveur MCP Mahi ne fournit pas nécessairement un outil `list_workflows`

**Option B retenue — `active.json` réduit au strict minimum (workflowId + path)**
- Avantages : compatibilité avec la contrainte "un seul workflow actif par machine",
  retrouver le `workflowId` sans appel réseau supplémentaire, migration minimale
- Inconvénients : maintien d'un fichier local, mais sans information de phase (éliminé)

**Format cible de `.sdd/local/active.json` :**

```json
{
  "type": "spec",
  "id": "mon-spec",
  "workflowId": "<uuid-retourné-par-mahi_create_workflow>",
  "path": ".sdd/specs/2026/04/mon-spec",
  "activatedAt": "2026-04-18T10:00:00Z"
}
```

**Champs supprimés par rapport à sdd-spec :** aucun (les champs existants sont conservés
pour compatibilité) ; le champ `workflowId` est **ajouté**.

**Justification :** La phase courante n'est plus stockée localement — elle est toujours
lue via `mahi_get_workflow(workflowId)`. Cela garantit que l'état présenté à l'utilisateur
reflète l'état réel de la FSM du serveur.

**Implémente :** REQ-002, REQ-003, REQ-NF-002

**Contrat de test :**
- Après `/spec new`, `active.json` contient `workflowId` non nul
- `mahi_get_workflow(workflowId)` retourne le même `id` et la phase `requirements`
- `active.json` ne contient pas de champ `currentPhase`

---

## DES-003 : Adaptation du skill `spec.md` — commandes principales

**Problème :** Le skill `spec.md` (orchestrateur principal) de sdd-spec gère les commandes
`new`, `open`, `approve`, `close`, `clarify`, etc. en lisant/écrivant `state.json` et
`active.json` directement. Il faut remplacer chaque opération d'état par l'appel MCP correspondant.

**Mapping des opérations sdd-spec → MCP Mahi :**

| Opération sdd-spec | Appel MCP Mahi |
|--------------------|----------------|
| Créer `state.json` avec `currentPhase: "requirements"` | `mahi_create_workflow(type="spec")` → stocker `workflowId` |
| Lire `state.json` → `currentPhase` | `mahi_get_workflow(workflowId)` → lire `phase` |
| Écrire `state.json` → avancer la phase | `mahi_fire_event(workflowId, event="approve")` |
| Écrire `state.json` → supprimer le workflow | `mahi_fire_event(workflowId, event="discard")` |
| Créer le worktree git | `mahi_create_worktree(workflowId, ...)` |
| Supprimer le worktree git | `mahi_remove_worktree(workflowId)` |
| Ajouter entrée changelog dans `state.json` | `mahi_fire_event(workflowId, event="clarify", ...)` |

**Adaptation de chaque sous-commande :**

**`/spec new <titre>` (START_NEW) :**
1. Lire `active.json` — si présent, appeler `mahi_fire_event(workflowId, "close")` puis continuer
2. Appeler `mahi_create_workflow(type="spec", title=<titre>)` → récupérer `workflowId`
3. Créer le répertoire `.sdd/specs/YYYY/MM/<kebab-titre>/` et sous-répertoire `reviews/`
4. Écrire `active.json` avec `workflowId` (sans `currentPhase`)
5. Écrire `log.md` avec entrée de création
6. Mettre à jour `registry.md`
7. Lire phase via `mahi_get_workflow(workflowId)` → entrer en phase requirements

**`/spec open <titre>` (OPEN) :**
1. Lire `registry.md` → trouver le spec correspondant
2. Lire `active.json` — si différent : fermer le workflow courant via `mahi_fire_event` close
3. Écrire `active.json` avec `workflowId` du spec cible
4. Appeler `mahi_get_workflow(workflowId)` → obtenir la phase courante
5. Activer le comportement de la phase correspondante

**`/spec approve` (APPROVE) :**
1. Lire `active.json` → `workflowId`
2. Appeler `mahi_get_workflow(workflowId)` → valider que la phase courante est correcte
3. Valider le contenu du document de la phase (critères identiques à sdd-spec)
4. Appeler `mahi_fire_event(workflowId, event="approve")`
5. Lire la réponse : si erreur → afficher l'erreur du serveur en français
6. Si succès → entrer dans la phase suivante selon la réponse du serveur

**`/spec close` (CLOSE) :**
1. Lire `active.json` → `workflowId`
2. Appeler `mahi_fire_event(workflowId, event="close")` (save context)
3. Supprimer `active.json`

**`/spec discard` (DISCARD) :**
1. Demander confirmation explicite
2. Appeler `mahi_remove_worktree(workflowId)` si worktree existant
3. Appeler `mahi_fire_event(workflowId, event="discard")`
4. Supprimer les fichiers locaux et `active.json`

**Implémente :** REQ-002, REQ-003, REQ-004, REQ-007, REQ-NF-002, REQ-NF-003

**Contrat de test :**
- `/spec new` : `active.json` créé avec `workflowId`, phase = requirements (via `mahi_get_workflow`)
- `/spec approve` en requirements : `mahi_fire_event` appelé avec `approve`, phase passe à design
- `/spec approve` avec transition invalide : message d'erreur du serveur affiché à l'utilisateur
- `/spec close` : `active.json` supprimé, `mahi_fire_event` close appelé

---

## DES-004 : Adaptation des phases — écriture des artifacts via `mahi_write_artifact`

**Problème :** Les références de phase (`phase-requirements.md`, `phase-design.md`, etc.)
dans sdd-spec instruisent l'agent de générer et écrire des documents Markdown localement.
Dans mahi-workflow, chaque écriture de document DOIT être accompagnée d'un appel
`mahi_write_artifact`.

**Approche retenue :** Double écriture — fichier local (lisibilité humaine) + `mahi_write_artifact`
(state machine côté serveur). Les fichiers locaux dans `.sdd/specs/YYYY/MM/<id>/` sont
conservés tels quels.

**Modifications dans les références de phase :**

Pour chaque phase générant un document, ajouter après l'instruction d'écriture fichier :

```
Après écriture du fichier, appeler :
mahi_write_artifact(
  workflowId: <lire depuis active.json>,
  artifactKey: "<nom-du-fichier>",  // ex. "requirement.md", "design.md", "plan.md"
  content: <contenu du document>
)
```

**Documents concernés :**
- Phase requirements → `requirement.md` → `mahi_write_artifact(key="requirement.md")`
- Phase design → `design.md` → `mahi_write_artifact(key="design.md")`
- Phase planning → `plan.md` → `mahi_write_artifact(key="plan.md")`

**Phase clarify :**
Au lieu de modifier uniquement `state.json` changelog, appeler aussi :
`mahi_fire_event(workflowId, event="clarify", data={summary: ..., affectedItems: [...]})`

**Implémente :** REQ-005, REQ-NF-003

**Contrat de test :**
- Après phase requirements complète : `mahi_write_artifact` appelé avec `requirement.md`
- Contenu de l'artifact côté serveur identique au fichier local
- Après clarify : `mahi_fire_event("clarify")` appelé en plus des modifications locales

---

## DES-005 : Adaptation des agents — suppression des lectures de `state.json`

**Problème :** Les agents de sdd-spec (`spec-orchestrator.md`, `spec-requirements.md`, etc.)
lisent parfois `state.json` pour connaître la phase courante ou le statut des tâches.
Dans mahi-workflow, cette information doit venir de `mahi_get_workflow`.

**Approche retenue :** Modification des prompts d'agents pour remplacer :
- `Read state.json` → `Call mahi_get_workflow(workflowId)` 
- `Write state.json` → `Call mahi_fire_event(workflowId, event)`
- Toute référence à `currentPhase` en local → champ `phase` de la réponse `mahi_get_workflow`

**Agents concernés et modifications :**

| Agent | Modification principale |
|-------|------------------------|
| `spec-orchestrator.md` | Remplacer lecture `state.json` par `mahi_get_workflow` pour routing de phase |
| `spec-requirements.md` | Appeler `mahi_write_artifact` après génération de `requirement.md` ; `mahi_add_requirement_info` par exigence |
| `spec-design.md` | Appeler `mahi_write_artifact` après génération de `design.md` ; `mahi_add_design_info` par élément |
| `spec-design-validator.md` | Lire artifacts via `mahi_get_workflow` si besoin (optionnel) |
| `spec-planner.md` | Appeler `mahi_write_artifact` après génération de `plan.md` |
| `spec-task-implementer.md` | Lire plan.md localement (pas de modification majeure) |
| `spec-reviewer.md` | Lire artifacts localement (pas de modification majeure) |

**Implémente :** REQ-003, REQ-005, REQ-006, REQ-NF-002

**Contrat de test :**
- `spec-orchestrator` ne contient aucune référence à `Read state.json`
- `spec-requirements` contient une instruction `mahi_write_artifact` après génération
- `spec-design` contient une instruction `mahi_add_design_info` après chaque DES-xxx

---

## DES-006 : Adaptation du `protocol-context.md` — sauvegarde et chargement de contexte

**Problème :** Le protocole de contexte (CLOSE / OPEN) dans sdd-spec sauvegarde et restaure
le contexte du spec en lisant `state.json`. Dans mahi-workflow, la source de vérité est
`mahi_get_workflow`.

**Approche retenue :**

**Section CLOSE (sauvegarde) :**
- Appeler `mahi_fire_event(workflowId, event="close")` pour signaler la mise en pause
- Supprimer `active.json` (comportement inchangé)

**Section Chargement du contexte (OPEN) :**
- Lire `active.json` → `workflowId`
- Appeler `mahi_get_workflow(workflowId)` → obtenir phase, artifacts, statuts
- Utiliser la réponse comme briefing de contexte (remplace la lecture de `state.json`)
- Les fichiers locaux (`requirement.md`, `design.md`, etc.) restent disponibles pour lecture

**Implémente :** REQ-003, REQ-NF-002

**Contrat de test :**
- CLOSE : `mahi_fire_event(close)` appelé + `active.json` supprimé
- OPEN : `mahi_get_workflow` appelé et sa réponse utilisée pour le briefing

---

## DES-007 : Adaptation de `state-machine.md` — FSM déclarative vs implémentée

**Problème :** Dans sdd-spec, `state-machine.md` définit les transitions valides que le LLM
doit respecter. Dans mahi-workflow, la FSM est implémentée côté serveur — ce fichier devient
une **référence documentaire** des transitions, pas une logique à implémenter par le LLM.

**Approche retenue :** Conserver `state-machine.md` comme documentation de référence,
en précisant explicitement que :
1. La FSM est implémentée par le serveur Mahi
2. Le LLM NE DOIT PAS valider les transitions localement
3. La source de vérité est la réponse de `mahi_fire_event`

**Ajout en en-tête de `state-machine.md` :**
```
> Note mahi-workflow : cette FSM est implémentée côté serveur Mahi.
> Les transitions sont déclenchées via `mahi_fire_event` et validées par le serveur.
> Le LLM ne doit pas implémenter de logique de validation locale.
```

**Implémente :** REQ-004, REQ-NF-002

**Contrat de test :**
- `state-machine.md` contient la note mahi-workflow en en-tête
- Aucune référence à une validation de transition LLM dans les skills/agents

---

## Couverture des exigences

| Exigence | DES couvrant | Statut |
|----------|-------------|--------|
| REQ-001 — Structure du plugin mahi-workflow | DES-001 | ✅ |
| REQ-002 — Création workflow via `mahi_create_workflow` | DES-002, DES-003 | ✅ |
| REQ-003 — Lecture état via `mahi_get_workflow` | DES-002, DES-003, DES-006 | ✅ |
| REQ-004 — Transitions via `mahi_fire_event` | DES-003, DES-007 | ✅ |
| REQ-005 — Artifacts via `mahi_write_artifact` | DES-004, DES-005 | ✅ |
| REQ-006 — Enrichissement via `mahi_add_requirement_info` / `mahi_add_design_info` | DES-005 | ✅ |
| REQ-007 — Worktree via `mahi_create_worktree` / `mahi_remove_worktree` | DES-003 | ✅ |
| REQ-NF-001 — Compatibilité MCP STDIO | DES-001 | ✅ |
| REQ-NF-002 — Élimination gestion d'état LLM | DES-002, DES-003, DES-005, DES-006, DES-007 | ✅ |
| REQ-NF-003 — Maintien UX sdd-spec | DES-003, DES-004 | ✅ |
