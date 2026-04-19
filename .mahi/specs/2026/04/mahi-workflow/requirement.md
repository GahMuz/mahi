# Exigences : mahi-workflow — Migration fonctionnelle de sdd-spec vers Mahi

## Contexte codebase

**Module `mahi-mcp/`** — Serveur MCP Java Spring Boot opérationnel (spec `mahi` complété).
Expose une FSM (machine à états finis) stricte côté serveur via STDIO MCP.
Outils MCP disponibles :
- `mahi_create_workflow` — crée une instance de workflow (retourne un `workflowId`)
- `mahi_get_workflow` — lit l'état courant d'un workflow (phase, artifacts, statuts)
- `mahi_fire_event` — déclenche une transition FSM (ex. `approve`, `discard`, `clarify`)
- `mahi_write_artifact` — écrit ou met à jour un artifact attaché à un workflow
- `mahi_add_requirement_info` — enrichit les infos d'une exigence dans le workflow
- `mahi_add_design_info` — enrichit les infos d'un élément de design dans le workflow
- `mahi_create_worktree` — crée le worktree git associé au workflow
- `mahi_remove_worktree` — supprime le worktree git associé au workflow

**Module `mahi-plugins/mahi/`** — Plugin Mahi existant avec `plugin.json` et le fat jar
pré-compilé versionné `mahi-plugins/mahi/mahi-mcp-server.jar`.
La configuration `.mcp.json` est gérée hors périmètre de ce spec (dans `mahi-plugins/mahi/`).

**Plugin sdd-spec (source)** — Plugin Claude Code installé via sdd-marketplace.
Implémente le workflow SDD complet (requirements → design → worktree → planning →
implementation → finishing → retrospective) via des skills et agents qui gèrent l'état
localement : `state.json`, `active.json`, lectures/écritures manuelles de fichiers Markdown.
Structure : `skills/spec/`, `skills/adr/`, `agents/*.md`, références de phase.

**Module `mahi-marketplace/`** — Registre JSON des plugins Mahi du repo.

---

## Glossaire

| Terme | Définition |
|-------|------------|
| Plugin Mahi | Répertoire dans `mahi-plugins/<nom>/` contenant au minimum un `plugin.json` et des skills/agents Claude Code |
| sdd-spec | Plugin source installé depuis sdd-marketplace — gère l'état workflow par fichiers locaux (state.json, active.json) |
| mahi-workflow | Plugin cible à créer dans `mahi-plugins/mahi-workflow/` — remplace la gestion d'état LLM par des appels MCP Mahi |
| FSM Mahi | Machine à états finis stricte implémentée côté serveur MCP Mahi — les transitions sont validées par le serveur, pas par le LLM |
| Workflow Mahi | Instance d'une FSM côté serveur, identifiée par un `workflowId` retourné par `mahi_create_workflow` |
| Artifact | Document attaché à un workflow Mahi (ex. `requirement.md`, `design.md`, `plan.md`) — géré via `mahi_write_artifact` |
| state.json | Fichier local gérant la phase courante dans sdd-spec — **remplacé** par `mahi_get_workflow` dans mahi-workflow |
| active.json | Fichier local `.sdd/local/active.json` indiquant le spec actif — **remplacé** par lecture du workflow courant via MCP |
| Gestion d'état LLM | Pattern sdd-spec où le LLM maintient l'état en lisant/écrivant des fichiers locaux — **éliminé** dans mahi-workflow |
| MCP | Model Context Protocol — protocole permettant à un LLM d'appeler des outils et lire des ressources |
| STDIO | Mode de communication MCP par entrée/sortie standard |

---

## Périmètre

### Dans le périmètre

| Fonctionnalité | Description |
|----------------|-------------|
| Copie de la structure sdd-spec | Copier le contenu de sdd-spec dans `mahi-plugins/mahi-workflow/` comme point de départ |
| Adaptation des skills spec | Remplacer la gestion d'état fichier (state.json, active.json) par des appels MCP Mahi dans les skills du workflow spec |
| Adaptation des agents | Remplacer les lectures de state.json par `mahi_get_workflow`, les écritures par `mahi_fire_event` et `mahi_write_artifact` |
| Suppression de la gestion locale d'état | Éliminer toutes les lectures/écritures de state.json, active.json et des transitions implicites gérées par le LLM |
| Délégation FSM au serveur | Toutes les transitions de phase passent par `mahi_fire_event` — la FSM stricte du serveur valide la transition |
| Structure du plugin | Répertoire `mahi-plugins/mahi-workflow/` avec `plugin.json` conforme au format Mahi |
| Enregistrement marketplace | Ajout du plugin dans `mahi-marketplace/marketplace.json` |

### Hors périmètre

| Fonctionnalité | Raison |
|----------------|--------|
| Installation du jar | Géré dans `mahi-plugins/mahi/` — hors de ce spec |
| Configuration `.mcp.json` | Géré dans `mahi-plugins/mahi/` via `/mahi-setup` — hors de ce spec |
| Commande `/mahi-setup` | Reste dans `mahi-plugins/mahi/` — hors de ce spec |
| Nouveaux types de workflow FSM | Les définitions spec/ADR/debug/find-bug sont dans `mahi-mcp/` — hors de ce spec |
| Modifications du serveur MCP | Le serveur est opérationnel et ne doit pas être modifié dans ce spec |
| Skills adr, analyse, doc, commit | Hors périmètre initial — ce spec se concentre sur le workflow spec uniquement |

---

## Exigences fonctionnelles

### REQ-001 : Structure du plugin mahi-workflow

**Récit utilisateur :**
En tant que développeur du projet Mahi, je veux que le plugin `mahi-workflow` soit correctement
structuré dans `mahi-plugins/mahi-workflow/` afin qu'il soit reconnu par le système de plugins
Mahi et publiable dans le marketplace.

**Critères d'acceptation :**

1. LE répertoire `mahi-plugins/mahi-workflow/` DOIT exister dans le repo
2. LE plugin DOIT contenir un fichier `plugin.json` avec les champs :
   - `name` : `"mahi-workflow"`
   - `version` : version sémantique (ex. `"0.1.0"`)
   - `description` : description en anglais du plugin
   - `author` avec `name`
   - `keywords` incluant au minimum `"workflow"`, `"mcp"`, `"sdd"`, `"mahi"`
3. LE plugin DOIT être référencé dans `mahi-marketplace/marketplace.json` avec ses métadonnées
4. LA structure du plugin DOIT reproduire l'organisation de sdd-spec (skills/, agents/)
   adaptée au contexte plugin Mahi

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-002 : Création du workflow Mahi via `mahi_create_workflow`

**Récit utilisateur :**
En tant qu'utilisateur lançant `/spec new <titre>`, je veux que le workflow soit créé côté
serveur Mahi via `mahi_create_workflow` afin que la FSM stricte gère les transitions
à la place du LLM.

**Critères d'acceptation :**

1. QUAND l'utilisateur lance `/spec new <titre>` ALORS LE skill DOIT appeler `mahi_create_workflow`
   avec le type de workflow approprié (ex. `spec`)
2. LE `workflowId` retourné par `mahi_create_workflow` DOIT être persisté localement
   (ex. dans `.sdd/local/active.json`) pour les appels MCP ultérieurs
3. LA phase initiale DOIT être lue via `mahi_get_workflow` plutôt qu'initialisée localement
4. LE skill DOIT entrer dans la phase requirements après création (conforme à la FSM Mahi)
5. QUAND un autre workflow est actif ALORS LE skill DOIT d'abord fermer le workflow courant
   (via `mahi_fire_event` close) avant d'en créer un nouveau

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-003 : Lecture d'état via `mahi_get_workflow` (remplacement de state.json)

**Récit utilisateur :**
En tant qu'utilisateur reprenant un spec ouvert, je veux que l'état courant du workflow
soit lu depuis le serveur Mahi via `mahi_get_workflow` afin d'avoir un état fiable et
cohérent, sans dépendre de fichiers locaux potentiellement désynchronisés.

**Critères d'acceptation :**

1. TOUTES les lectures de phase courante DOIVENT utiliser `mahi_get_workflow` au lieu de
   lire `state.json` localement
2. LE fichier `state.json` NE DOIT PAS être créé ni maintenu par mahi-workflow
3. QUAND l'utilisateur lance `/spec open <titre>` ALORS LE skill DOIT retrouver le
   `workflowId` (depuis `active.json` ou un registre local minimal) et appeler
   `mahi_get_workflow` pour obtenir la phase courante
4. LA phase retournée par `mahi_get_workflow` DOIT déterminer quel comportement de phase
   est actif (requirements, design, planning, implementation, finishing, retrospective)
5. QUAND `mahi_get_workflow` retourne une erreur ALORS LE skill DOIT informer l'utilisateur
   que le serveur MCP Mahi doit être démarré

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-004 : Transitions de phase via `mahi_fire_event` (remplacement de la FSM LLM)

**Récit utilisateur :**
En tant qu'utilisateur lançant `/spec approve`, je veux que la transition de phase soit
validée et déclenchée par le serveur Mahi via `mahi_fire_event` afin que la FSM stricte
du serveur empêche toute transition invalide, indépendamment du contexte LLM.

**Critères d'acceptation :**

1. TOUTES les transitions de phase (approve, discard, etc.) DOIVENT passer par
   `mahi_fire_event` avec l'événement approprié
2. LE skill DOIT utiliser la réponse de `mahi_fire_event` pour confirmer que la transition
   a réussi ou signaler une erreur (transition invalide selon la FSM)
3. LE skill NE DOIT PAS implémenter sa propre logique de validation des transitions —
   cette responsabilité appartient exclusivement à la FSM du serveur Mahi
4. QUAND `mahi_fire_event` retourne une erreur de transition invalide ALORS LE skill
   DOIT afficher le message d'erreur du serveur à l'utilisateur en français
5. LA progression de phase dans les skills DOIT être pilotée par la réponse de
   `mahi_fire_event`, pas par une logique conditionnelle LLM

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-005 : Persistance des artifacts via `mahi_write_artifact`

**Récit utilisateur :**
En tant qu'utilisateur rédigeant les requirements d'un spec, je veux que les documents
(requirement.md, design.md, plan.md) soient écrits via `mahi_write_artifact` afin que
les artifacts soient attachés au workflow Mahi et accessibles via le serveur.

**Critères d'acceptation :**

1. QUAND le skill génère ou met à jour `requirement.md` ALORS il DOIT appeler
   `mahi_write_artifact` avec le contenu du document et le `workflowId` courant
2. QUAND le skill génère ou met à jour `design.md` ALORS il DOIT appeler
   `mahi_write_artifact` avec le contenu et le `workflowId` courant
3. QUAND le skill génère ou met à jour `plan.md` ALORS il DOIT appeler
   `mahi_write_artifact` avec le contenu et le `workflowId` courant
4. LES appels à `mahi_write_artifact` PEUVENT coexister avec des écritures fichier locales
   (les fichiers `.sdd/specs/YYYY/MM/<id>/` restent présents pour la lisibilité humaine)
5. LE `workflowId` courant DOIT être passé à chaque appel `mahi_write_artifact`

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-006 : Enrichissement des exigences et du design via les outils MCP dédiés

**Récit utilisateur :**
En tant que skill gérant la phase requirements, je veux utiliser `mahi_add_requirement_info`
pour enrichir les exigences côté serveur afin que le serveur Mahi maintienne une
représentation structurée des exigences au-delà du simple artifact Markdown.

**Critères d'acceptation :**

1. QUAND une exigence REQ-xxx est finalisée ALORS LE skill DOIT appeler
   `mahi_add_requirement_info` avec les métadonnées structurées de l'exigence
2. QUAND un élément de design DES-xxx est finalisé ALORS LE skill DOIT appeler
   `mahi_add_design_info` avec les métadonnées structurées
3. CES appels DOIVENT être effectués en complément de `mahi_write_artifact` (pas en remplacement)
4. LE skill DOIT gérer les erreurs de ces appels sans bloquer la progression du workflow

**Priorité :** souhaitable
**Statut :** brouillon

---

### REQ-007 : Gestion du worktree via `mahi_create_worktree` / `mahi_remove_worktree`

**Récit utilisateur :**
En tant qu'utilisateur passant à la phase worktree, je veux que le worktree git soit créé
via `mahi_create_worktree` afin que la création soit tracée par le serveur Mahi.

**Critères d'acceptation :**

1. QUAND le workflow entre en phase worktree ALORS LE skill DOIT appeler
   `mahi_create_worktree` avec les paramètres appropriés
2. QUAND le workflow est supprimé (discard) ALORS LE skill DOIT appeler
   `mahi_remove_worktree` avant de supprimer les artifacts locaux
3. LA gestion d'erreur de `mahi_create_worktree` DOIT être traitée explicitement
   (worktree déjà existant, branche déjà présente, etc.)

**Priorité :** obligatoire
**Statut :** brouillon

---

## Exigences non fonctionnelles

### REQ-NF-001 : Compatibilité avec le protocole MCP STDIO

**Récit utilisateur :**
En tant qu'utilisateur du plugin mahi-workflow, je veux que tous les appels MCP fonctionnent
via le serveur STDIO Mahi déjà configuré afin de ne pas nécessiter de configuration
supplémentaire.

**Critères d'acceptation :**

1. TOUS les appels MCP (mahi_create_workflow, mahi_get_workflow, etc.) DOIVENT être
   des appels d'outils MCP standard, compatibles avec le protocole STDIO
2. LE plugin NE DOIT PAS introduire de dépendances réseau ou de configuration supplémentaire
3. LE plugin DOIT fonctionner dès que le serveur MCP Mahi est configuré dans `.mcp.json`

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-NF-002 : Élimination de la gestion d'état LLM pour les transitions

**Récit utilisateur :**
En tant que développeur du plugin mahi-workflow, je veux que la logique de transition de phase
soit entièrement déléguée à la FSM du serveur Mahi afin d'éliminer les risques d'incohérence
d'état introduits par la gestion implicite dans le contexte LLM.

**Critères d'acceptation :**

1. LE plugin NE DOIT PAS maintenir de fichier `state.json` local pour les transitions
2. LE plugin NE DOIT PAS implémenter de logique de validation de transition (ex. `requirements → design`)
   dans les skills — cette logique appartient à la FSM du serveur
3. LE plugin NE DOIT PAS écrire dans `active.json` la phase courante — seul le `workflowId`
   y est stocké pour identifier le workflow actif
4. TOUTE décision de transition DOIT être prise en appelant `mahi_fire_event` et en interprétant
   la réponse du serveur

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-NF-003 : Maintien de l'expérience utilisateur sdd-spec

**Récit utilisateur :**
En tant qu'utilisateur de sdd-spec migrant vers mahi-workflow, je veux que les commandes
slash (`/spec new`, `/spec open`, `/spec approve`, etc.) conservent le même comportement
observable afin que la migration soit transparente.

**Critères d'acceptation :**

1. LES commandes slash disponibles DOIVENT être identiques à celles de sdd-spec
   (`/spec new`, `/spec open`, `/spec approve`, `/spec clarify`, `/spec close`, etc.)
2. LES messages utilisateur (en français) DOIVENT conserver le même ton et la même
   structure que dans sdd-spec
3. LA progression par phases DOIT être identique du point de vue de l'utilisateur
4. LES artifacts locaux (`requirement.md`, `design.md`, `plan.md`) DOIVENT continuer
   à exister dans `.sdd/specs/YYYY/MM/<id>/` pour la lisibilité humaine

**Priorité :** obligatoire
**Statut :** brouillon
