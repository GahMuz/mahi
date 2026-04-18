# Plan — sdd-spec-worktree-mcp

## Règles

Règles vérifiées post-implémentation :

**Règles SOLID (plugin)**
- [x] S : chaque nouveau service a une responsabilité unique, décrite en une phrase sans "et"
- [x] O : l'ajout des nouveaux outils dans `WorkflowTools` ne modifie pas les outils existants
- [x] L : `ActiveState` et `StateSnapshot` sont des records immuables substituables
- [x] I : `mahi_activate` et `mahi_deactivate` sont des outils séparés — pas de couplage forcé
- [x] D : `WorkflowTools` dépend d'interfaces (`ActiveStateService`, `StateFileService`), pas des implémentations

**Règles transversales (projet)**
- [x] Pas de secrets en dur
- [x] Pas de `System.out.println` oubliés
- [x] Gestion d'erreurs explicite (pas de catch vide)
- [x] Pas de modification de fichiers générés automatiquement
- [x] Placement des fichiers suit les conventions (`ia.mahi.service`, `ia.mahi.mcp`, `ia.mahi.workflow.core`)
- [x] Séparation des couches respectée (`WorkflowTools` → service interface, jamais implémentation concrète)
- [x] Tests passent (57 baseline → ≥ 57 + nouveaux tests)

---

## TASK-001 — Services côté serveur MCP Java : `ActiveStateService` et `StateFileService`

**Implémente :** DES-001
**Satisfait :** REQ-001 (AC-1, AC-3, AC-5), REQ-002 (AC-1, AC-3, AC-4, AC-5)

### TASK-001.1 [x] [RED] — Tests `ActiveStateServiceTest`

**Fichier :** `mahi-mcp/src/test/java/ia/mahi/service/ActiveStateServiceTest.java`

**Description :** Écrire les tests JUnit 5 pour `ActiveStateService` avec `@TempDir`.

**Tests à écrire (dérivés du contrat de test DES-001) :**
- `activate(specId, type, path, workflowId)` → `active.json` créé dans `<tempDir>/.sdd/local/active.json`
- `activate` : champs `id`, `type`, `path`, `workflowId`, `activatedAt` présents et corrects (JSON)
- `deactivate()` → `active.json` supprimé
- `deactivate()` si fichier absent → pas d'exception (idempotent)
- `activate` depuis un chemin worktree → `active.json` dans le repo root, pas dans le worktree

**Vérification :** Tests en échec (RED) — les classes n'existent pas encore.

**Dépend de :** rien

---

### TASK-001.2 [x] [RED] — Tests `StateFileServiceTest`

**Fichier :** `mahi-mcp/src/test/java/ia/mahi/service/StateFileServiceTest.java`

**Description :** Écrire les tests JUnit 5 pour `StateFileService` avec `@TempDir`.

**Tests à écrire :**
- `updateState(absPath, "design", null)` → crée `state.json` si absent avec `currentPhase: "design"` et `updatedAt` ISO-8601
- `updateState` → met à jour `state.json` si existant
- `updateState` avec `ChangelogEntry` → entrée ajoutée sans écraser les précédentes
- `updateState` retourne un `StateSnapshot` avec `specId`, `currentPhase`, `updatedAt`, `changelog`

**Vérification :** Tests en échec (RED) — les classes n'existent pas encore.

**Dépend de :** rien (parallélisable avec TASK-001.1)

---

### TASK-001.3 [x] [RED] — Tests `WorkflowToolsActivationTest`

**Fichier :** `mahi-mcp/src/test/java/ia/mahi/mcp/WorkflowToolsActivationTest.java`

**Description :** Écrire les tests pour les 4 nouvelles méthodes MCP dans `WorkflowTools`.

**Tests à écrire :**
- `activate(specId, type, path, workflowId)` → appelle `ActiveStateService.activate(...)` avec les bons args
- `deactivate()` → appelle `ActiveStateService.deactivate()`
- `updateRegistry(specId, status, title, period)` → appelle `ActiveStateService.updateRegistry(...)`
- `updateState(specPath, phase, null)` → appelle `StateFileService.updateState(...)` et retourne `StateSnapshot`

**Vérification :** Tests en échec (RED) — méthodes absentes de `WorkflowTools`.

**Dépend de :** TASK-001.1 (ActiveState record), TASK-001.2 (StateSnapshot record) — en pratique parallélisable si les records sont créés en TASK-001.4

---

### TASK-001.4 [x] [GREEN] — Records de domaine : `ActiveState`, `StateSnapshot`, `ChangelogEntry`

**Fichiers à créer :**
- `mahi-mcp/src/main/java/ia/mahi/workflow/core/ActiveState.java`
- `mahi-mcp/src/main/java/ia/mahi/workflow/core/StateSnapshot.java`
- `mahi-mcp/src/main/java/ia/mahi/workflow/core/ChangelogEntry.java`

**Description :** Créer les 3 records Java immuables définis dans DES-001.

```java
// ActiveState
public record ActiveState(String type, String id, String workflowId, String path, Instant activatedAt) {}

// StateSnapshot
public record StateSnapshot(String specId, String currentPhase, Instant updatedAt, List<ChangelogEntry> changelog) {}

// ChangelogEntry
public record ChangelogEntry(Instant date, String type, String description, List<String> affectedItems) {}
```

**Vérification :** `./gradlew compileJava` sans erreur.

**Dépend de :** rien (parallélisable avec RED tasks)

---

### TASK-001.5 [x] [GREEN] — Interfaces `ActiveStateService` et `StateFileService`

**Fichiers à créer :**
- `mahi-mcp/src/main/java/ia/mahi/service/ActiveStateService.java` (interface + implémentation)
- `mahi-mcp/src/main/java/ia/mahi/service/StateFileService.java` (interface + implémentation)

**Description :** Implémenter les deux services avec résolution de chemin absolu.

`ActiveStateService` :
- Résout `repoRoot` via `git rev-parse --show-toplevel` dans `@PostConstruct`
- `activate(...)` : écrit `<repoRoot>/.sdd/local/active.json` (crée le répertoire si absent)
- `deactivate()` : supprime `<repoRoot>/.sdd/local/active.json`, idempotent
- `updateRegistry(specId, status, title, period)` : met à jour la ligne dans `<repoRoot>/.sdd/specs/registry.md`

`StateFileService` :
- `updateState(absPath, phase, changelogEntry)` : lit `state.json` si existant, met à jour `currentPhase` et `updatedAt`, ajoute l'entrée changelog, écrit le fichier. Retourne `StateSnapshot`.

**Vérification :** Tests TASK-001.1 et TASK-001.2 passent.

**Dépend de :** TASK-001.1, TASK-001.2 (RED tests écrits), TASK-001.4 (records)

---

### TASK-001.6 [x] [GREEN] — 4 nouveaux outils MCP dans `WorkflowTools`

**Fichier à modifier :** `mahi-mcp/src/main/java/ia/mahi/mcp/WorkflowTools.java`

**Description :** Ajouter les 4 méthodes `mahi_activate`, `mahi_deactivate`, `mahi_update_registry`, `mahi_update_state` selon les signatures DES-001. Injecter `ActiveStateService` et `StateFileService` dans le constructeur.

**Vérification :** Tests TASK-001.3 passent. `./gradlew test` : ≥ 57 tests passent.

**Dépend de :** TASK-001.3, TASK-001.5

---

## TASK-002 — Adaptation du skill : `SKILL.md` (plugin mahi-workflow)

**Implémente :** DES-003 (partie SKILL.md)
**Satisfait :** REQ-001 (AC-4), REQ-003 (AC-1, AC-2, AC-6), REQ-004 (AC-1)

### TASK-002.1 [x] — Modifier `SKILL.md` : START_NEW, OPEN, APPROVE, DISCARD

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/SKILL.md`

**Description :** Modifications chirurgicales selon DES-003 :

**START_NEW :**
- Après step 5 (`mahi_create_workflow`) : ajouter `EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")`
- Step 7 : remplacer "Add a row to `.sdd/specs/registry.md`" par `mahi_update_registry(specId, "requirements", title, period)`
- Step 8 : remplacer "Write `.sdd/local/active.json`" par `mahi_activate(specId, "spec", path, workflowId)`

**OPEN :**
- Step 3 : remplacer "Write `.sdd/local/active.json`" par `mahi_activate(specId, "spec", path, workflowId)` + `EnterWorktree(branch, path)`

**APPROVE :**
- Step 4 : remplacer "Update `Statut` column in `.sdd/specs/registry.md`" par `mahi_update_registry(specId, <newPhase>)`
- Ajouter après la transition : `mahi_update_state(specPath, <newPhase>, changelogEntry)`

**DISCARD :**
- Step 4 : remplacer "Delete `.sdd/local/active.json`" par `ExitWorktree()` + `mahi_deactivate()`
- Step 3 : remplacer suppression directe de registry par `mahi_update_registry(specId, "discarded")`

**Vérification :** `grep -n "Write.*active.json\|Add a row.*registry.md\|Update.*Statut.*registry.md\|Delete.*active.json" SKILL.md` → 0 résultat.

**Dépend de :** TASK-001 (outils MCP disponibles côté serveur) — mais peut être développé en parallèle pour le plugin

---

## TASK-003 — Adaptation de `phase-worktree.md`

**Implémente :** DES-003 (partie phase-worktree.md)
**Satisfait :** REQ-003 (AC-4), REQ-004 (AC-2)

### TASK-003.1 [x] — Modifier `phase-worktree.md`

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/phase-worktree.md`

**Description :**
- Supprimer **Step 1** (création de la branche git manuelle)
- Supprimer **Step 2** (`mahi_create_worktree`) — le worktree est déjà créé depuis `START_NEW`/`OPEN`
- Renuméroter : ancien Step 3 → Step 1 (Project Setup), etc.
- Ajouter une note en en-tête : "Le worktree est déjà créé et activé depuis `/spec new` ou `/spec open`."
- Conserver Step 4 (Capture Test Baseline) et l'appel `mahi_fire_event` inchangés

**Vérification :** `grep -n "mahi_create_worktree\|git branch spec/" phase-worktree.md` → 0 résultat.

**Dépend de :** rien (parallélisable)

---

## TASK-004 — Adaptation de `protocol-context.md`

**Implémente :** DES-003 (partie protocol-context.md)
**Satisfait :** REQ-001 (AC-4), REQ-003 (AC-2, AC-6), REQ-004 (AC-3)

### TASK-004.1 [x] — Modifier `protocol-context.md` section CLOSE

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/protocol-context.md`

**Description :**
- Ajouter **Step 0** avant Step 1 : appeler `ExitWorktree()` pour quitter le worktree avant la sauvegarde du contexte
- **Step 6** (actuel "Libérer la spec active") : remplacer "Supprimer `.sdd/local/active.json`" par `mahi_deactivate()` — le LLM ne touche plus `active.json` directement
- Renommer Step 6 en "Libérer la spec active via le serveur Mahi"

**Vérification :** `grep -n "Supprimer.*active.json\|Delete.*active.json" protocol-context.md` → 0 résultat.

**Dépend de :** rien (parallélisable)

---

## TASK-005 — Adaptation de `protocol-split.md`

**Implémente :** DES-003 (partie protocol-split.md)
**Satisfait :** REQ-001 (AC-4), REQ-004 (AC-4)

### TASK-005.1 [x] — Modifier `protocol-split.md`

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/protocol-split.md`

**Description :**
- **Step 8** "Update Registry" : remplacer l'instruction d'édition directe de `registry.md` par :
  - `mahi_update_registry(<new-spec-id>, <phase>, title, period)` pour la nouvelle spec
  - `mahi_update_registry(<original-spec-id>, <phase>)` si le statut de l'originale a changé
- **Step 9** "Activate One Spec" : remplacer "Write `.sdd/local/active.json`" par `mahi_activate(specId, "spec", path, workflowId)`
- Ajouter : `mahi_update_state(specPath, <initialPhase>, null)` pour le state.json de la nouvelle spec dérivée

**Vérification :** `grep -n "Write.*active.json\|registry.md" protocol-split.md` → seules les références documentaires (pas d'instructions d'écriture directe).

**Dépend de :** rien (parallélisable)

---

## TASK-006 — Adaptation de `protocol-resume.md`

**Implémente :** DES-003 (partie protocol-resume.md)
**Satisfait :** REQ-003 (AC-1, AC-6), REQ-004 (AC-5)

### TASK-006.1 [x] — Modifier `protocol-resume.md` section Worktree Phase

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/protocol-resume.md`

**Description :**
- **Worktree Phase, Step 3** : remplacer `mahi_create_worktree` par `EnterWorktree(branch, path)` (crée si absent, réutilise si existant)
- Mettre à jour le message d'erreur associé : "Branche manquante — suggérer de re-créer la branche puis relancer `/spec open`"

**Vérification :** `grep -n "mahi_create_worktree" protocol-resume.md` → 0 résultat.

**Dépend de :** rien (parallélisable)

---

## TASK-007 — Adaptation de `test-spec-commands.md`

**Implémente :** DES-003 (partie test-spec-commands.md)
**Satisfait :** REQ-004 (AC-6)

### TASK-007.1 [x] — Mettre à jour `test-spec-commands.md`

**Fichier :** `mahi-plugins/mahi-workflow/skills/spec/references/test-spec-commands.md`

**Description :** Ajouter les nouveaux critères de vérification pour chaque commande :

**`/spec new` :**
- `mahi_activate` est appelé avec `specId`, `type="spec"`, `path`, `workflowId`
- `EnterWorktree` est appelé avec `branch="spec/<username>/<spec-id>"`, `path=".worktrees/<spec-id>"`
- `mahi_update_registry` est appelé avec `specId`, `"requirements"`, `title`, `period`

**`/spec open` :**
- `mahi_activate` est appelé avec `specId`, `type="spec"`, `path`, `workflowId`
- `EnterWorktree` est appelé avec `branch="spec/<username>/<spec-id>"`, `path=".worktrees/<spec-id>"`

**`/spec approve` :**
- `mahi_update_registry` est appelé avec le nouveau statut après transition réussie
- `mahi_update_state` est appelé pour mettre à jour la phase dans `state.json`

**`/spec close` :**
- `ExitWorktree` est appelé avant la sauvegarde du contexte (Step 0 de CLOSE)
- `mahi_deactivate` est appelé pour supprimer `active.json`

**`/spec discard` :**
- `ExitWorktree` est appelé avant la suppression
- `mahi_deactivate` est appelé pour supprimer `active.json`

**Vérification :** Le fichier contient les critères `mahi_activate`, `mahi_deactivate`, `mahi_update_registry`, `mahi_update_state`, `EnterWorktree`, `ExitWorktree` pour les commandes concernées.

**Dépend de :** rien (parallélisable)

---

## Graphe de dépendances

```
TASK-001.1 (RED ActiveStateServiceTest)  ─┐
TASK-001.2 (RED StateFileServiceTest)     ├──→ TASK-001.5 (GREEN services) ─→ TASK-001.6 (GREEN WorkflowTools)
TASK-001.3 (RED WorkflowToolsTest)        ┘                                         ↑
TASK-001.4 (records domaine) ─────────────────────────────────────────────────────┘

TASK-002.1 (SKILL.md) ──────────────────── indépendant
TASK-003.1 (phase-worktree.md) ─────────── indépendant
TASK-004.1 (protocol-context.md) ────────── indépendant
TASK-005.1 (protocol-split.md) ─────────── indépendant
TASK-006.1 (protocol-resume.md) ────────── indépendant
TASK-007.1 (test-spec-commands.md) ─────── indépendant

Premier lot parallèle : TASK-001.1, TASK-001.2, TASK-001.3, TASK-001.4,
                        TASK-002.1, TASK-003.1, TASK-004.1, TASK-005.1, TASK-006.1, TASK-007.1
Second lot : TASK-001.5 (après 001.1 + 001.2 + 001.4)
Troisième lot : TASK-001.6 (après 001.3 + 001.5)
```

---

## Couverture DES → TASK

| DES | TASKs |
|-----|-------|
| DES-001 | TASK-001.1 à TASK-001.6 |
| DES-002 | TASK-002.1 (EnterWorktree/ExitWorktree), TASK-003.1, TASK-004.1, TASK-006.1 |
| DES-003 | TASK-002.1, TASK-003.1, TASK-004.1, TASK-005.1, TASK-006.1, TASK-007.1 |
| DES-004 | TASK-001.1, TASK-001.2, TASK-001.3 |

---

## Totaux

**7 tâches parentes, 11 sous-tâches**
- 3 sous-tâches RED (tests Java)
- 4 sous-tâches GREEN (implémentation Java + plugin)
- 6 sous-tâches modifications directes de fichiers (références plugin)

**10 sous-tâches parallélisables dans le premier lot** (toutes sauf TASK-001.5 et TASK-001.6)
