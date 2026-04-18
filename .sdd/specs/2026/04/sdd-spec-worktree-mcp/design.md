# Design : sdd-spec-worktree-mcp — Refonte architecturale MCP, worktree et active.json

## Contexte

Specs liés chargés comme contexte :
- **mahi** (completed) — serveur MCP Java, architecture hexagonale, `WorkflowTools`, `WorkflowService`
- **mahi-workflow** (completed) — plugin mahi-workflow, migration sdd-spec vers MCP Mahi
- **mcp-for-spec** (finishing) — opérations granulaires REQ/DES, `mahi_save_context`, `mahi_check_coherence`

Le serveur MCP Mahi (`mahi-mcp/`) expose actuellement les outils suivants :
`mahi_create_workflow`, `mahi_get_workflow`, `mahi_fire_event`, `mahi_write_artifact`,
`mahi_add_requirement_info`, `mahi_add_design_info`, `mahi_create_worktree`, `mahi_remove_worktree`,
`mahi_add_requirement`, `mahi_update_requirement`, `mahi_list_requirements`, `mahi_get_requirement`,
`mahi_add_design_element`, `mahi_update_design_element`, `mahi_list_design_elements`, `mahi_get_design_element`,
`mahi_check_coherence`, `mahi_save_context`.

Le plugin `mahi-plugins/mahi-workflow/` expose actuellement le skill `/spec` qui :
- écrit `active.json` directement (LLM — à éliminer)
- écrit `registry.md` directement (LLM — à éliminer)
- écrit `state.json` directement (LLM — à éliminer)
- utilise `mahi_create_worktree` (outil MCP Java — à remplacer par `EnterWorktree` harness)
- crée la branche git manuellement dans `phase-worktree.md` Step 1 (à déplacer dans START_NEW)

**Principe directeur :** Ce spec complète la migration vers le MCP strictement du côté du serveur
(nouveaux outils) et du côté du skill (adapter les 6 fichiers identifiés par REQ-004).
Le moteur FSM `WorkflowEngine` n'est pas modifié.

---

## DES-001 : Nouveaux outils MCP côté serveur — gestion de `active.json`, `registry.md` et `state.json`

**Problème :**
`active.json`, `registry.md` et `state.json` sont encore écrits directement par le LLM
dans le plugin `mahi-workflow`. REQ-001 exige que le serveur MCP Java soit le seul écrivain
de ces trois fichiers. Trois nouveaux outils doivent être exposés.

**Options considérées :**

Option A — Un seul outil `mahi_activate` qui écrit `active.json` ET `registry.md` en une seule opération
- Avantages : atomicité forte pour l'opération "activer un spec"
- Inconvénients :
  - Deux responsabilités dans un même outil — violation du principe S (Single Responsibility)
  - Le skill ne peut pas mettre à jour `registry.md` sans activer un spec

**Option B retenue — Responsabilités séparées : 4 outils distincts**
- `mahi_activate` : écrit `.sdd/local/active.json` uniquement
- `mahi_deactivate` : supprime `.sdd/local/active.json` uniquement
- `mahi_update_registry` : met à jour le statut d'une ligne dans `.sdd/specs/registry.md`
- `mahi_update_state` : met à jour `state.json` dans le répertoire du spec
- Avantages :
  - Chaque outil a une responsabilité unique et un nom déclaratif
  - Testable indépendamment
  - Le skill peut appeler uniquement l'outil dont il a besoin (ex. mettre à jour registry sans activer)
- Inconvénients : légèrement plus d'appels MCP par opération — acceptable pour la clarté

**Résolution des chemins (REQ-002) :**
Le serveur MCP Java résout le chemin absolu vers la racine du dépôt au démarrage via :
```bash
git rev-parse --show-toplevel
```
Ce chemin est stocké dans un bean Spring `@Value` ou résolu dans un `@PostConstruct` de la couche service.
Toutes les opérations sur `active.json` et `registry.md` utilisent ce chemin — jamais le `cwd` du LLM.

**Signatures des nouveaux outils dans `WorkflowTools.java` :**

```java
@Tool(name = "mahi_activate",
      description = "Write .sdd/local/active.json to mark a spec as active on this machine. Resolves path relative to git repo root.")
public ActiveState activate(
    @ToolParam(description = "Spec identifier (kebab-case)", required = true) String specId,
    @ToolParam(description = "Type of item: spec | adr", required = true) String type,
    @ToolParam(description = "Relative path to spec directory (e.g., .sdd/specs/2026/04/my-spec)", required = true) String path,
    @ToolParam(description = "Workflow identifier (UUID)", required = true) String workflowId);

@Tool(name = "mahi_deactivate",
      description = "Delete .sdd/local/active.json to release the active spec on this machine.")
public void deactivate();

@Tool(name = "mahi_update_registry",
      description = "Update the status of a spec row in .sdd/specs/registry.md. Creates the row if absent.")
public void updateRegistry(
    @ToolParam(description = "Spec identifier", required = true) String specId,
    @ToolParam(description = "New status (requirements | design | worktree | planning | implementation | finishing | retrospective | completed)", required = true) String status,
    @ToolParam(description = "Spec title (used only when creating a new row)", required = false) String title,
    @ToolParam(description = "Period YYYY/MM (used only when creating a new row)", required = false) String period);

@Tool(name = "mahi_update_state",
      description = "Write or update state.json for a spec. Manages currentPhase, changelog and phase statuses.")
public StateSnapshot updateState(
    @ToolParam(description = "Absolute path to the spec directory", required = true) String specPath,
    @ToolParam(description = "New current phase", required = true) String currentPhase,
    @ToolParam(description = "Optional changelog entry (JSON-serializable object)", required = false) ChangelogEntry changelogEntry);
```

**Nouvelles classes de retour dans `ia.mahi.workflow.core` :**

```java
// ActiveState — état de active.json retourné par mahi_activate
public record ActiveState(
    String type,          // "spec" | "adr"
    String id,            // specId
    String workflowId,    // UUID
    String path,          // chemin relatif
    Instant activatedAt
) {}

// StateSnapshot — snapshot de state.json retourné par mahi_update_state
public record StateSnapshot(
    String specId,
    String currentPhase,
    Instant updatedAt,
    List<ChangelogEntry> changelog
) {}

// ChangelogEntry — entrée du changelog state.json
public record ChangelogEntry(
    Instant date,
    String type,        // "clarification" | "transition" | etc.
    String description,
    List<String> affectedItems
) {}
```

**Nouveau service `ActiveStateService` dans `ia.mahi.service` :**
- Responsabilité unique : lecture/écriture de `active.json` et `registry.md` avec résolution de chemin absolu
- Résout le repo root au démarrage (`@PostConstruct`)
- Injected dans `WorkflowTools` (dépendance via interface)

**Nouveau service `StateFileService` dans `ia.mahi.service` :**
- Responsabilité unique : lecture/écriture de `state.json` dans le répertoire du spec
- Opère sur le chemin absolu passé en paramètre (le worktree path est connu du LLM au moment de l'appel)

**Justification SOLID :**
- **S** : `ActiveStateService` = gestion `active.json` et `registry.md` ; `StateFileService` = gestion `state.json` — deux responsabilités distinctes, deux services
- **O** : ajout de nouveaux outils dans `WorkflowTools` sans modifier les outils existants
- **L** : `ActiveState` et `StateSnapshot` sont des records immuables substituables
- **I** : `mahi_activate` et `mahi_deactivate` sont deux outils séparés — le skill n'est pas forcé de dépendre des deux
- **D** : `WorkflowTools` dépend de `ActiveStateService` (interface) et `StateFileService` (interface), jamais des implémentations concrètes

**Implémente :** REQ-001, REQ-002

**Contrat de test :**
- `mahi_activate("my-spec", "spec", ".sdd/specs/2026/04/my-spec", "<uuid>")` → `active.json` créé dans `<repoRoot>/.sdd/local/` avec les champs corrects
- `mahi_deactivate()` → `active.json` supprimé ; si absent, pas d'exception (idempotent)
- `mahi_update_registry("my-spec", "design", ...)` → ligne mise à jour dans `<repoRoot>/.sdd/specs/registry.md`
- `mahi_update_state("<path>", "design", null)` → `state.json` mis à jour avec `currentPhase: "design"` et `updatedAt` ISO-8601
- `mahi_activate` depuis un worktree → `active.json` créé dans le répertoire repo root, pas dans le worktree

---

## DES-002 : Intégration `EnterWorktree` / `ExitWorktree` dans les commandes du skill

**Problème :**
Le plugin `mahi-workflow` utilise actuellement :
- `git branch spec/<username>/<spec-id>` (commande git manuelle dans `phase-worktree.md` Step 1)
- `mahi_create_worktree` (outil MCP Java dans `phase-worktree.md` Step 2)
- Aucune gestion du contexte de répertoire de travail (pas d'`EnterWorktree` / `ExitWorktree`)

REQ-003 exige que :
- Le worktree soit créé dès `START_NEW` / `OPEN` (pas seulement en phase worktree)
- `EnterWorktree` et `ExitWorktree` soient les seuls mécanismes de navigation
- `ExitWorktree` soit appelé à la fermeture (`CLOSE`, `DISCARD`)

**Options considérées :**

Option A — Créer le worktree en phase `worktree` (comportement actuel)
- Avantages : cohérence avec le workflow actuel
- Inconvénients : pendant les phases requirements/design/planning, le LLM travaille sur la branche principale — risque de modifier des fichiers non isolés

**Option B retenue — Créer le worktree dès `START_NEW` et `OPEN`**
- Avantages :
  - Isolation immédiate : le LLM travaille toujours dans un contexte dédié dès l'ouverture
  - `active.json` et `registry.md` restent sur la branche principale (REQ-002) — `EnterWorktree` les sépare naturellement du worktree
  - Cohérence : `CLOSE` appelle toujours `ExitWorktree` (workflow unifié)
- Inconvénients :
  - La phase `worktree` devient plus légère (elle ne crée plus le worktree, seulement le setup projet + baseline)
  - `EnterWorktree` crée la branche si elle n'existe pas déjà

**Comportement cible de chaque commande :**

| Commande | EnterWorktree | ExitWorktree |
|----------|--------------|-------------|
| `START_NEW` | Oui — après création du workflow Mahi | Non |
| `OPEN` | Oui — après identification du workflowId | Non |
| `CLOSE` | Non | Oui — avant la sauvegarde du contexte |
| `DISCARD` | Non | Oui — avant suppression des fichiers |
| `phase-worktree.md` | Non (déjà dans worktree) | Non |
| `SWITCH` | Via OPEN | Via CLOSE |

**Signature de `EnterWorktree` (harness Claude Code) :**
```
EnterWorktree(branch: "spec/<username>/<spec-id>", path: ".worktrees/<spec-id>")
```
- Crée la branche si elle n'existe pas
- Crée le worktree si absent, réutilise si existant (idempotent)
- Change le CWD du LLM vers le worktree

**Signature de `ExitWorktree` (harness Claude Code) :**
```
ExitWorktree()
```
- Retourne le CWD du LLM vers la racine du dépôt principal

**Règle :** Le LLM NE DOIT PAS appeler `git checkout`, `git worktree add` ou `cd` manuellement.

**Justification SOLID :**
- **S** : `EnterWorktree` / `ExitWorktree` ont une seule responsabilité (navigation) — ils ne touchent pas `active.json` ni `state.json`
- **O** : L'ajout de ces appels dans `START_NEW`/`OPEN`/`CLOSE`/`DISCARD` n'altère pas la logique existante — il l'étend
- **D** : Le skill dépend des outils du harness (abstractions), pas des commandes git directes

**Implémente :** REQ-003

**Contrat de test :**
- `/spec new` → `EnterWorktree` appelé avec `branch="spec/<username>/<spec-id>"`, `path=".worktrees/<spec-id>"`
- `/spec open` → `EnterWorktree` appelé avec les mêmes paramètres
- `/spec close` → `ExitWorktree` appelé avant sauvegarde du contexte
- `/spec discard` → `ExitWorktree` appelé avant suppression
- `phase-worktree.md` ne contient plus Step 1 (git branch) ni Step 2 (mahi_create_worktree)
- `protocol-resume.md` section Worktree Phase utilise `EnterWorktree` au lieu de `mahi_create_worktree`

---

## DES-003 : Adaptation du skill `SKILL.md` et des references du plugin

**Problème :**
Les 6 fichiers identifiés par REQ-004 doivent être mis à jour pour :
1. Remplacer les écritures directes de `active.json`, `registry.md`, `state.json` par des appels MCP
2. Remplacer `mahi_create_worktree` par `EnterWorktree` (et ajouter `ExitWorktree`)
3. Créer la branche dans `START_NEW` (pas dans `phase-worktree.md`)

**Approche retenue :** Adaptation minimale — modifier uniquement les instructions concernées dans
chaque fichier, sans refactoring hors périmètre. Les patterns existants (`mahi_create_workflow`,
`mahi_get_workflow`, `mahi_fire_event`, `mahi_save_context`) sont conservés tels quels.

**Modifications par fichier :**

### `skills/spec/SKILL.md`

**START_NEW — remplacements :**
- Step 5 (actuel) : `Call mahi_create_workflow(type="spec", title="<titre>")` → ajouter après : appel à `EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")` (créer la branche et entrer dans le worktree)
- Step 7 (actuel) : "Add a row to `.sdd/specs/registry.md`" → remplacer par : `mahi_update_registry(specId, "requirements", title, period)` — le LLM ne touche plus `registry.md` directement
- Step 8 (actuel) : "Write `.sdd/local/active.json`" → remplacer par : `mahi_activate(specId, "spec", path, workflowId)` — le LLM ne touche plus `active.json` directement

**OPEN — remplacements :**
- Step 3 (actuel) : "Write `.sdd/local/active.json`" → remplacer par : `mahi_activate(specId, "spec", path, workflowId)` + `EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")`

**APPROVE — remplacements :**
- Step 4 (actuel) : "Update `Statut` column in `.sdd/specs/registry.md`" → remplacer par : `mahi_update_registry(specId, <newPhase>)` — le LLM ne touche plus `registry.md` directement

**DISCARD — remplacements :**
- Step 4 (actuel) : "Delete `.sdd/local/active.json`" → remplacer par : `ExitWorktree()` + `mahi_deactivate()`
- Step 3 (actuel) : "Remove row from `.sdd/specs/registry.md`" → remplacer par : `mahi_update_registry(specId, "discarded")` ou suppression via un outil MCP dédié

**CLOSE — pas de modification directe** : CLOSE délègue à `protocol-context.md` (voir DES-003 section protocol-context.md)

### `skills/spec/references/phase-worktree.md`

**Modifications :**
- Supprimer **Step 1** (création de la branche git manuelle)
- Supprimer **Step 2** (`mahi_create_worktree`) — le worktree est déjà créé depuis `START_NEW`
- Renuméroter : l'ancien Step 3 devient Step 1 (Project Setup), etc.
- Ajouter une note en en-tête : "Le worktree est déjà créé et activé depuis `/spec new` ou `/spec open`."
- Conserver Step 4 (Capture Test Baseline) et Step 5 (`mahi_fire_event`) inchangés

### `skills/spec/references/protocol-context.md`

**Modifications — section CLOSE :**
- **Avant Step 1** : ajouter un nouvel Step 0 : appeler `ExitWorktree()` pour quitter le worktree avant la sauvegarde du contexte
- **Step 6** (actuel) : "Supprimer `.sdd/local/active.json`" → remplacer par : `mahi_deactivate()` — le LLM ne touche plus `active.json` directement
- Renommer Step 6 en "Libérer la spec active via le serveur Mahi"

### `skills/spec/references/protocol-split.md`

**Modifications :**
- Step 8 "Update Registry" : remplacer l'instruction d'édition directe de `registry.md` par :
  `mahi_update_registry(<new-spec-id>, <phase>, title, period)` pour la nouvelle spec,
  et `mahi_update_registry(<original-spec-id>, <phase>)` si le statut de l'originale a changé
- Step 9 "Activate One Spec" : remplacer "Write `.sdd/local/active.json`" par `mahi_activate(...)` pour la spec choisie

### `skills/spec/references/protocol-resume.md`

**Modifications — section Worktree Phase :**
- Step 3 : remplacer `mahi_create_worktree` par `EnterWorktree(branch, path)` (crée si absent, réutilise si existant)
- Mettre à jour le message d'erreur associé : "Branche manquante — suggérer de re-créer la branche puis relancer `/spec open`"

### `skills/spec/references/test-spec-commands.md`

**Ajouts** — nouveaux critères de vérification pour chaque commande :

Pour `/spec new` :
- `mahi_activate` est appelé avec `specId`, `type="spec"`, `path`, `workflowId`
- `EnterWorktree` est appelé avec `branch="spec/<username>/<spec-id>"`, `path=".worktrees/<spec-id>"`
- `mahi_update_registry` est appelé avec `specId`, `"requirements"`, `title`, `period`

Pour `/spec open` :
- `mahi_activate` est appelé avec `specId`, `type="spec"`, `path`, `workflowId`
- `EnterWorktree` est appelé avec `branch="spec/<username>/<spec-id>"`, `path=".worktrees/<spec-id>"`

Pour `/spec approve` :
- `mahi_update_registry` est appelé avec le nouveau statut après transition réussie
- `mahi_update_state` est appelé pour mettre à jour la phase dans `state.json`

Pour `/spec close` :
- `ExitWorktree` est appelé avant la sauvegarde du contexte
- `mahi_deactivate` est appelé pour supprimer `active.json`

Pour `/spec discard` :
- `ExitWorktree` est appelé avant la suppression
- `mahi_deactivate` est appelé pour supprimer `active.json`

**Justification SOLID :**
- **S** : chaque fichier a une responsabilité bien délimitée ; les modifications sont minimales et chirurgicales
- **O** : les patterns existants (`mahi_create_workflow`, `mahi_get_workflow`, etc.) ne sont pas modifiés
- **L** : les appels `mahi_activate` / `mahi_deactivate` sont substituables aux écritures directes de fichiers
- **D** : le skill dépend désormais du serveur MCP (abstraction) plutôt que d'opérations fichier directes

**Implémente :** REQ-004 (AC-1 à AC-6)

**Dépend de :** DES-001 (outils MCP), DES-002 (EnterWorktree / ExitWorktree)

**Contrat de test :**
- `SKILL.md` ne contient plus aucune instruction "Write `.sdd/local/active.json`" ni "Write `registry.md`" ni "Write `state.json`" en dehors des appels MCP
- `phase-worktree.md` ne contient plus Step 1 (git branch) ni `mahi_create_worktree`
- `protocol-context.md` CLOSE contient `mahi_deactivate()` et `ExitWorktree()` au lieu de suppression directe de `active.json`
- `protocol-split.md` contient `mahi_update_registry` et `mahi_activate` pour les opérations sur registry et active.json
- `protocol-resume.md` section Worktree Phase contient `EnterWorktree` au lieu de `mahi_create_worktree`
- `test-spec-commands.md` contient les critères pour `mahi_activate`, `mahi_deactivate`, `mahi_update_registry`, `mahi_update_state`, `EnterWorktree`, `ExitWorktree`

---

## DES-004 : Tests unitaires côté serveur MCP (Java) pour les nouveaux outils

**Problème :**
Les 4 nouveaux outils MCP (`mahi_activate`, `mahi_deactivate`, `mahi_update_registry`, `mahi_update_state`)
ajoutent une couche de services (`ActiveStateService`, `StateFileService`) qui opèrent sur le système de
fichiers. Il faut des tests unitaires garantissant les comportements attendus (idempotence, résolution
de chemins, atomicité).

**Approche retenue :** Tests JUnit 5 + AssertJ avec un répertoire temporaire JUnit (`@TempDir`) pour
simuler la structure du dépôt. Chaque service est testé isolément avec un faux repo root.

**Fichiers de test à créer :**
- `ActiveStateServiceTest.java` — couvre `activate`, `deactivate`, idempotence, chemin absolu
- `StateFileServiceTest.java` — couvre `updateState`, création/mise à jour de `state.json`, entrée changelog
- `WorkflowToolsActivationTest.java` — couvre les signatures MCP exposées

**Justification SOLID :**
- **S** : chaque fichier de test a une seule classe cible
- **D** : les tests passent `@TempDir` comme repoRoot — pas de dépendance sur le système de fichiers réel

**Implémente :** REQ-001 (AC-1 à AC-5), REQ-002 (AC-1 à AC-5)

**Dépend de :** DES-001

**Contrat de test :**
- `ActiveStateServiceTest` : `activate()` écrit dans `<tempDir>/.sdd/local/active.json` (pas dans un sous-répertoire)
- `ActiveStateServiceTest` : `deactivate()` supprime le fichier ; si absent, aucune exception
- `StateFileServiceTest` : `updateState()` crée `state.json` si absent ; le met à jour si existant
- `StateFileServiceTest` : entrée changelog ajoutée sans écraser les entrées précédentes
- `WorkflowToolsActivationTest` : `mahi_activate` depuis un worktree → `active.json` dans le repo root résolu au démarrage

---

## Auto-revue du design

**Pass 1 — Complétude :**
- Chaque DES a un "Contrat de test" ✅
- Dépendances entre DES déclarées ✅ (DES-003 dépend de DES-001 et DES-002 ; DES-004 dépend de DES-001)
- Table de couverture REQ → DES présente ✅ (voir section suivante)
- Chaque DES a un ID, une approche et une justification ✅

**Pass 2 — Correction :**
- Pas de décisions sans justification ✅
- Alternatives genuinement considérées : DES-001 Option A/B, DES-002 Option A/B ✅
- Principes SOLID vérifiés pour chaque DES ✅
- Contrats de test dérivés des critères d'acceptation REQ ✅

**Pass 3 — Cohérence :**
- DES cohérents avec REQs ✅
- Pas de contradiction avec les designs passés (mahi, mahi-workflow, mcp-for-spec) ✅ — on étend sans modifier les patterns existants
- Couverture REQ → DES complète ✅ (voir table ci-dessous)

---

## Couverture des exigences

| Exigence | DES couvrant | Statut |
|----------|-------------|--------|
| REQ-001 — MCP seul écrivain de `active.json`, `registry.md`, `state.json` | DES-001, DES-003 | ✅ |
| REQ-002 — `active.json` et `registry.md` résident sur la branche principale | DES-001 | ✅ |
| REQ-003 — Worktree créé à l'ouverture, quitté à la fermeture | DES-002, DES-003 | ✅ |
| REQ-004 — Fichiers du plugin mahi-workflow définis et traçables | DES-003 | ✅ |
