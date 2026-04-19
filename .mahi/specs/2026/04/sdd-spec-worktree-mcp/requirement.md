# Exigences — sdd-spec-worktree-mcp

Refonte architecturale du plugin sdd-spec/mahi-workflow : le MCP Java prend la responsabilité de `active.json`
et `registry.md`, ces fichiers restent sur la branche principale, et le worktree est créé dès l'ouverture d'une spec.

---

### REQ-001 — Le MCP est le seul écrivain de `active.json`, `registry.md` et `state.json`

**Récit utilisateur :**
En tant que développeur utilisant le plugin sdd-spec, je veux que `active.json`, `registry.md` et `state.json`
soient gérés exclusivement par le serveur MCP Java afin de garantir l'atomicité des écritures et la validation
du schéma, sans risque de corruption par le LLM.

**Critères d'acceptation :**

1. LE Système DOIT exposer des outils MCP pour toutes les opérations sur `active.json`, `registry.md` et
   `state.json` (création, mise à jour, suppression, lecture).
2. LE Système DOIT valider le schéma de `active.json`, `registry.md` et `state.json` avant toute écriture.
3. LE Système DOIT garantir l'atomicité des écritures (pas d'état partiel en cas d'erreur).
4. LE LLM (skill/agent) NE DOIT PAS écrire directement dans `active.json`, `registry.md` ou `state.json` —
   il DOIT appeler les outils MCP correspondants.
5. QUAND un outil MCP écrit `active.json`, `registry.md` ou `state.json`, ALORS LE Système DOIT retourner
   l'état résultant dans la réponse de l'outil.

**Priorité :** obligatoire

**Statut :** brouillon

---

### REQ-002 — `active.json` et `registry.md` résident sur la branche principale uniquement

**Récit utilisateur :**
En tant que développeur travaillant en worktree, je veux que `active.json` et `registry.md` soient toujours
sur la branche principale afin d'éviter les conflits de merge et de rendre l'état de la machine visible à
tous les outils indépendamment du worktree actif.

**Critères d'acceptation :**

1. LE serveur MCP Java DOIT résoudre le chemin absolu vers la racine du dépôt au démarrage (par exemple via
   `git rev-parse --show-toplevel` depuis son propre répertoire de travail), et utiliser ce chemin pour toutes
   les opérations sur `active.json` et `registry.md`.
2. LE skill (LLM) NE DOIT PAS passer de chemin dans ses appels aux outils MCP — il appelle `mahi_activate`,
   `mahi_deactivate`, `mahi_fire_event` sans argument de path ; c'est le serveur qui résout le chemin.
3. QUAND le LLM travaille dans un worktree, ALORS LE Système DOIT continuer à lire et écrire `active.json`
   et `registry.md` dans le répertoire racine du dépôt principal, sans dépendance au `cwd` du LLM.
4. LE Système NE DOIT PAS créer ni modifier `active.json` ou `registry.md` dans un répertoire worktree.
5. QUAND `registry.md` est mis à jour (nouvelle spec, changement de statut), ALORS LE Système DOIT s'assurer
   que la modification cible le fichier sur la branche principale, en utilisant le repo root résolu au démarrage.

**Priorité :** obligatoire

**Statut :** brouillon

---

### REQ-003 — Le worktree est créé à l'ouverture et quitté à la fermeture de la spec

**Récit utilisateur :**
En tant que développeur ouvrant une spec, je veux qu'un worktree dédié soit automatiquement créé et activé
afin de travailler dans un contexte isolé sans polluer la branche principale pendant l'implémentation.

**Critères d'acceptation :**

1. QUAND le skill exécute `/spec new` ou `/spec open`, ALORS LE Système DOIT créer (ou réutiliser s'il existe
   déjà) un worktree via le harness Claude Code (`EnterWorktree`) avant tout travail sur les fichiers de la spec.
2. QUAND le skill exécute `/spec close`, ALORS LE Système DOIT quitter le worktree via le harness Claude Code
   (`ExitWorktree`) après avoir sauvegardé le contexte.
3. LE Système DOIT placer le worktree dans `.worktrees/<spec-id>/` à la racine du dépôt principal.
4. QUAND le worktree est créé, ALORS LE Système DOIT créer ou basculer sur une branche `spec/<auteur>/<spec-id>`.
5. QUAND le worktree est quitté (`/spec close`), ALORS LE Système NE DOIT PAS supprimer le worktree — il reste
   disponible pour une réouverture ultérieure.
6. LE harness Claude Code (`EnterWorktree`/`ExitWorktree`) DOIT être le seul mécanisme de navigation entre
   worktree et branche principale — le LLM NE DOIT PAS appeler `git checkout` manuellement.

**Priorité :** obligatoire

**Statut :** brouillon

---

### REQ-004 — Les fichiers du plugin mahi-workflow à modifier sont définis et traçables

**Récit utilisateur :**
En tant que développeur implémentant la refonte, je veux que la liste minimale des fichiers à modifier soit
formalisée dans les exigences afin de garantir un périmètre d'implémentation clair et auditable, tout en
laissant la possibilité d'identifier d'autres fichiers affectés lors de la phase de design.

**Critères d'acceptation :**

1. LE Système DOIT modifier `skills/spec/SKILL.md` pour remplacer les écritures directes de `active.json`,
   `registry.md` et `state.json` par les appels MCP (`mahi_activate`, `mahi_deactivate`, `mahi_fire_event`,
   `mahi_update_state`) et ajouter la création de branche + `EnterWorktree` dans START_NEW/OPEN, ainsi que
   `ExitWorktree` dans CLOSE/DISCARD.
2. LE Système DOIT modifier `skills/spec/references/phase-worktree.md` pour supprimer les étapes de
   création de branche et `mahi_create_worktree` (déplacées dans START_NEW).
3. LE Système DOIT modifier `skills/spec/references/protocol-context.md` pour remplacer la suppression
   directe de `active.json` (étape CLOSE) par `mahi_deactivate()` + `ExitWorktree`.
4. LE Système DOIT modifier `skills/spec/references/protocol-split.md` pour que le registry soit
   auto-géré par `mahi_create_workflow` / `mahi_fire_event`, et `active.json` par `mahi_activate(workflowId)`.
5. LE Système DOIT modifier `skills/spec/references/protocol-resume.md` pour remplacer `mahi_create_worktree`
   par `EnterWorktree` (crée si absent, réutilise si existant).
6. LE Système DOIT modifier `skills/spec/references/test-spec-commands.md` pour ajouter les critères de
   vérification : `mahi_activate` (new/open), `mahi_deactivate` (close/discard), registry auto-géré,
   `EnterWorktree` (new/open), `ExitWorktree` (close/discard), et `mahi_update_state` (toute transition de phase).
7. LE Système PEUT identifier lors du design d'autres fichiers du plugin à modifier (ex. `skills/adr/SKILL.md`,
   fichiers de test, autres références), et DOIT les documenter dans `design.md` avec leur justification.

**Priorité :** obligatoire

**Statut :** brouillon

---

## Contexte codebase

### Modules concernés

- `mahi-plugins/mahi-workflow/skills/spec/` — skills `/spec new`, `/spec open`, `/spec close`
- `mahi-plugins/mahi-workflow/skills/spec/references/protocol-context.md` — OPEN/CLOSE/SWITCH
- `mahi-mcp/` — serveur MCP Java (Spring AI), `WorkflowTools.java`, `WorkflowService.java`
- `.sdd/local/active.json` — état machine local (gitignored)
- `.sdd/specs/registry.md` — registre partagé (commité sur main)
- `.sdd/specs/YYYY/MM/<id>/state.json` — état du spec (phase courante, changelog) — commité sur branche spec

### Patterns en place

- Le MCP Java expose des outils via `@Tool` (Spring AI MCP)
- `active.json` est actuellement écrit directement par le LLM (skill) — c'est le comportement à remplacer
- `state.json` est actuellement écrit directement par le LLM (skill) — idem, à déléguer au MCP
- Le harness Claude Code fournit `EnterWorktree` / `ExitWorktree` comme outils natifs
- Les chemins dans `active.json` sont relatifs — doivent devenir absolus ou résolus côté MCP

### Points d'attention

- `active.json` est gitignored : le MCP doit le gérer en dehors du worktree (chemin absolu vers repo root)
- `registry.md` est commité sur main : les écritures MCP doivent cibler la racine du dépôt, pas le worktree
- `state.json` est commité sur la branche `spec/<auteur>/<spec-id>` du worktree : le MCP écrit dans le worktree courant
- Le skill ne doit pas `git checkout` manuellement — uniquement `EnterWorktree`/`ExitWorktree`

### Fichiers du plugin mahi-workflow à modifier

| Fichier | REQ(s) | Changement |
|---------|--------|------------|
| `skills/spec/SKILL.md` | REQ-001, REQ-003 | START_NEW/OPEN : remplacer écriture directe de `active.json`, `registry.md`, `state.json` par appels MCP (`mahi_activate`, `mahi_fire_event`, `mahi_update_state`) ; ajouter création branche + `EnterWorktree`. APPROVE : supprimer écriture directe `registry.md` et `state.json` (gérées par `mahi_fire_event` / `mahi_update_state`). DISCARD/CLOSE : remplacer suppression directe par `mahi_deactivate()` + `ExitWorktree`. |
| `skills/spec/references/phase-worktree.md` | REQ-003 | Supprimer Step 1 (git branch) et Step 2 (`mahi_create_worktree`) — déplacés dans `START_NEW`. La phase ne gère plus que le setup projet et la baseline. |
| `skills/spec/references/protocol-context.md` | REQ-001, REQ-003 | CLOSE Step 6 : remplacer suppression directe de `active.json` par `mahi_deactivate()` + appel `ExitWorktree`. |
| `skills/spec/references/protocol-split.md` | REQ-001 | Step 8 : registry auto-géré par `mahi_create_workflow` et `mahi_fire_event` — pas d'écriture directe. Step 9 : remplacer écriture directe `active.json` par `mahi_activate(workflowId)`. `state.json` du spec dérivé créé par `mahi_update_state`. |
| `skills/spec/references/protocol-resume.md` | REQ-003 | Section "Worktree Phase" : remplacer `mahi_create_worktree` par `EnterWorktree` (crée si absent, réutilise si existant). |
| `skills/spec/references/test-spec-commands.md` | REQ-001, REQ-003 | Ajouter critères de vérification : `mahi_activate` (new/open), `mahi_deactivate` (close/discard), registry auto-géré par serveur, `EnterWorktree` (new/open), `ExitWorktree` (close/discard), `mahi_update_state` (transitions de phase). |
| *(autres fichiers à identifier en design)* | REQ-004 (AC-7) | Ex. `skills/adr/SKILL.md`, fichiers de test — à documenter en phase design si concernés. |