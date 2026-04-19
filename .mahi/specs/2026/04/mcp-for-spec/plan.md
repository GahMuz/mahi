# Plan d'implémentation — mcp-for-spec

## Règles (checklist post-implémentation)

### Règles plugin (SOLID)
- [x] S : chaque classe a une responsabilité décrite en une phrase sans "et"
- [x] O : ajout de comportement par extension (hiérarchie Artifact, factory ArtifactDefinition), pas par modification du moteur FSM
- [x] L : `FileArtifact` et `StructuredArtifact` substituables à `Artifact` partout dans `WorkflowEngine`
- [x] I : `mahi_list_requirements` retourne `RequirementSummary` (pas `RequirementItem`) — interface minimale
- [x] D : `WorkflowTools` ne dépend que de `WorkflowService`, guards injectés dans `SpecWorkflowDefinition`

### Règles projet (transversales)
- [x] Pas de secrets en dur
- [x] Pas de System.out.println oublié
- [x] Gestion d'erreurs explicite (pas de catch vide)
- [x] Pas de modification de fichiers générés
- [x] Pas de dépendances ajoutées sans justification
- [x] Tests passent (55 tests : 22 baseline + 33 nouveaux)
- [x] Séparation des couches respectée

---

## TASK-001 — Hiérarchie Artifact : nouveau modèle de données

**Implémente :** DES-001
**Satisfait :** REQ-001, REQ-002, REQ-005, REQ-006

### TASK-001.1 [x] [RED] — Tests de sérialisation polymorphique de la hiérarchie Artifact

**Fichier :** `src/test/java/ia/mahi/workflow/core/ArtifactSerializationTest.java`

Écrire les tests en échec suivants (dérivés du contrat de test DES-001) :
- `WorkflowContext` avec `RequirementsArtifact` sérialisé/désérialisé : `artifactType = "requirements"`, items présents
- `WorkflowContext` avec `FileArtifact` sérialisé/désérialisé : `artifactType = "file"`, rétro-compatible
- `WorkflowContext` sans `sessionContext` dans JSON : `getSessionContext()` retourne `null`
- Workflow non-spec : `context.getArtifacts()` ne contient que des `FileArtifact`

**Commande de vérification :** `./gradlew.bat test --tests "ia.mahi.workflow.core.ArtifactSerializationTest"` → doit ÉCHOUER (classes absentes)

**Dépend de :** aucune

---

### TASK-001.2 [x] [GREEN] — Créer les nouvelles classes de la hiérarchie Artifact

**Fichiers à créer dans `src/main/java/ia/mahi/workflow/core/` :**
- `Artifact.java` — classe abstraite avec `@JsonTypeInfo` + `@JsonSubTypes`, champs : `name`, `status` (`ArtifactStatus`), `version`, `path`, `updatedAt`. Méthodes : `markDraft`, `markValid`, `markStale`, `isValid`.
- `FileArtifact.java` — extend `Artifact`, name Jackson `"file"`, aucun champ supplémentaire.
- `StructuredArtifact.java` — abstract generic `<T>`, extend `Artifact`, champ `items: Map<String, T>`.
- `ItemStatus.java` — enum `VALID, STALE, MISSING`
- `AcceptanceCriterion.java` — record `(String id, String description)`
- `RequirementItem.java` — classe avec `id`, `title`, `priority`, `status` (`ItemStatus`), `acceptanceCriteria` (`List<AcceptanceCriterion>`), `content`
- `DesignItem.java` — classe avec `id`, `title`, `status` (`ItemStatus`), `coversAC` (`List<String>`), `implementedBy` (`List<String>`), `content`
- `RequirementsArtifact.java` — extend `StructuredArtifact<RequirementItem>`, name Jackson `"requirements"`
- `DesignArtifact.java` — extend `StructuredArtifact<DesignItem>`, name Jackson `"design"`
- `SessionContext.java` — classe avec `savedAt`, `lastAction`, `keyDecisions`, `openQuestions`, `nextStep`

**Commande de vérification :** `./gradlew.bat compileJava` → BUILD SUCCESSFUL (nouvelles classes compilent)

**Dépend de :** TASK-001.1

---

### TASK-001.2b [x] [GREEN] — Adapter les classes existantes pour utiliser la hiérarchie Artifact

**Fichiers à modifier :**
- `ArtifactDefinition.java` — ajouter champ `Supplier<Artifact> factory`, méthode statique `file(String name)` ; conserver `String name`
- `WorkflowContext.java` — changer `Map<String, ArtifactState>` → `Map<String, Artifact>` ; ajouter champ `SessionContext sessionContext` (`@JsonInclude(NON_NULL)`) ; adapter le constructeur (`v.factory().get()` au lieu de `new ArtifactState(k)`)
- `WorkflowEngine.java` — adapter `propagateInvalidation` : utiliser `Artifact` au lieu de `ArtifactState`
- `WorkflowService.java` — adapter cast `ArtifactState` → `Artifact`
- `SpecWorkflowDefinition.java` — utiliser `ArtifactDefinition.file(...)` pour les artefacts existants ; pour `requirements` et `design`, passer la factory correspondante
- `AdrWorkflowDefinition.java`, `DebugWorkflowDefinition.java`, `FindBugWorkflowDefinition.java` — utiliser `ArtifactDefinition.file(...)` sur tous les artefacts (aucune régression)
- `SpecWorkflowDefinitionTest.java` — adapter `markAndSave` et le cast `getArtifacts()` pour utiliser `Artifact`

**Commande de vérification :** `./gradlew.bat test` → 22 tests (existants) doivent passer

**Dépend de :** TASK-001.2

---

### TASK-001.3 [x] [REFACTOR] — Supprimer ArtifactState (dead code)

Une fois TASK-001.2 validé : supprimer `ArtifactState.java` si plus aucune référence.

**Commande de vérification :** `./gradlew.bat build` → BUILD SUCCESSFUL, aucun import `ArtifactState` restant

**Dépend de :** TASK-001.2

---

## TASK-002 — Outils MCP REQ : mahi_add/update/list/get_requirement

**Implémente :** DES-002
**Satisfait :** REQ-001

**Dépend de :** TASK-001.2 ✓

### TASK-002.1 [x] [RED] — Tests des opérations REQ dans WorkflowService

**Fichier :** `src/test/java/ia/mahi/engine/WorkflowServiceRequirementTest.java`

Tests (dérivés du contrat de test DES-002) :
- `addRequirement` avec ID existant → `IllegalArgumentException("Requirement already exists: REQ-001")`
- `listRequirements` → retourne IDs, titres, statuts uniquement (pas `content` ni `acceptanceCriteria`)
- `getRequirement` avec ID inexistant → `IllegalArgumentException("Requirement not found: REQ-999")`
- `addRequirement` avec IDs d'AC au format incorrect → `IllegalArgumentException` (format `<reqId>.AC-<n>`)

**Commande de vérification :** `./gradlew.bat test --tests "*WorkflowServiceRequirementTest"` → ÉCHOUE

**Dépend de :** TASK-001.2

---

### TASK-002.2 [x] [GREEN] — Implémenter les opérations REQ dans WorkflowService + outils dans WorkflowTools

**Fichiers à créer dans `src/main/java/ia/mahi/workflow/core/` :**
- `RequirementSummary.java` — record `(String id, String title, ItemStatus status)`

**Fichiers à modifier :**
- `WorkflowService.java` — ajouter 4 méthodes :
  - `addRequirement(String flowId, RequirementItem req)` : valider ID unique + format AC, déléguer au `RequirementsArtifact`
  - `updateRequirement(String flowId, String reqId, RequirementItem req)` : valider existence, appeler `propagateRequirementStale` (stub pour l'instant → délégué à TASK-004)
  - `listRequirements(String flowId)` : retourner `List<RequirementSummary>` depuis `RequirementsArtifact`
  - `getRequirement(String flowId, String reqId)` : retourner `RequirementItem` ou lever exception
- `WorkflowTools.java` — ajouter 4 méthodes `@Tool` : `mahi_add_requirement`, `mahi_update_requirement`, `mahi_list_requirements`, `mahi_get_requirement`

**Commande de vérification :** `./gradlew.bat test` → tous les tests passent

**Dépend de :** TASK-002.1

---

## TASK-003 — Outils MCP DES : mahi_add/update/list/get_design_element

**Implémente :** DES-003
**Satisfait :** REQ-002

**Dépend de :** TASK-001.2 ✓

### TASK-003.1 [x] [RED] — Tests des opérations DES dans WorkflowService

**Fichier :** `src/test/java/ia/mahi/engine/WorkflowServiceDesignTest.java`

Tests (dérivés du contrat de test DES-003) :
- `addDesignElement` sans `coversAC` → `IllegalArgumentException("At least one coversAC required")`
- `addDesignElement` avec AC inexistant → `IllegalArgumentException("AC not found: REQ-001.AC-9")`
- `listDesignElements` → retourne IDs, titres, statuts, `coversAC` — pas `content`
- `updateDesignElement` → retourne `WorkflowContext` (propagation STALE : stub vers TASK-004)

**Commande de vérification :** `./gradlew.bat test --tests "*WorkflowServiceDesignTest"` → ÉCHOUE

**Dépend de :** TASK-001.2

---

### TASK-003.2 [x] [GREEN] — Implémenter les opérations DES dans WorkflowService + outils dans WorkflowTools

**Fichiers à créer dans `src/main/java/ia/mahi/workflow/core/` :**
- `DesignSummary.java` — record `(String id, String title, ItemStatus status, List<String> coversAC)`

**Fichiers à modifier :**
- `WorkflowService.java` — ajouter 4 méthodes :
  - `addDesignElement(String flowId, DesignItem des)` : valider `coversAC` non vide + AC existantes
  - `updateDesignElement(String flowId, String desId, DesignItem des)` : valider existence
  - `listDesignElements(String flowId)` : retourner `List<DesignSummary>`
  - `getDesignElement(String flowId, String desId)` : retourner `DesignItem` ou lever exception
- `WorkflowTools.java` — ajouter 4 méthodes `@Tool` : `mahi_add_design_element`, `mahi_update_design_element`, `mahi_list_design_elements`, `mahi_get_design_element`

**Commande de vérification :** `./gradlew.bat test` → tous les tests passent

**Dépend de :** TASK-003.1

---

## TASK-004 — Propagation STALE fine-grained REQ → DES → TASK

**Implémente :** DES-004
**Satisfait :** REQ-003

**Dépend de :** TASK-002.2, TASK-003.2

### TASK-004.1 [x] [RED] — Tests de propagation STALE

**Fichier :** `src/test/java/ia/mahi/engine/StalePropagationTest.java`

Tests (dérivés du contrat de test DES-004) :
- `updateRequirement("REQ-001")` → DES qui ont un AC `REQ-001.AC-*` passent `STALE`
- DES sans lien avec REQ-001 restent inchangés
- DES déjà `STALE` → non inclus dans `stalePropagated`
- `stalePropagated` dans `metadata` contient exactement les IDs invalidés
- Propagation synchrone avant retour (garantie par nature Java séquentielle)

**Commande de vérification :** `./gradlew.bat test --tests "*StalePropagationTest"` → ÉCHOUE (méthode `propagateRequirementStale` absente)

**Dépend de :** TASK-002.2, TASK-003.2

---

### TASK-004.2 [x] [GREEN] — Implémenter propagateRequirementStale dans WorkflowService

**Fichier à modifier :** `WorkflowService.java`

Implémenter `propagateRequirementStale(WorkflowContext context, String reqId)` :
- Algorithme DES-004 : parcourir `DesignArtifact.getItems()`, marquer STALE les DES couvrant au moins un AC `reqId.*`
- Retourner `List<String> stalePropagated`
- Mettre le résultat dans `context.getMetadata().put("stalePropagated", stalePropagated)` (réinitialisé à chaque appel)
- Appeler depuis `updateRequirement` et `updateDesignElement` (propager sur les TASK via `ArtifactState` plan en coarse-grained)

**Commande de vérification :** `./gradlew.bat test` → tous les tests passent

**Dépend de :** TASK-004.1

---

## TASK-005 — mahi_check_coherence + Guard dans SpecWorkflowDefinition

**Implémente :** DES-005
**Satisfait :** REQ-004

**Dépend de :** TASK-002.2, TASK-003.2

### TASK-005.1 [x] [RED] — Tests de vérification de cohérence

**Fichier :** `src/test/java/ia/mahi/engine/CoherenceCheckTest.java`

Tests (dérivés du contrat de test DES-005) :
- Aucune violation → retourne `[]`
- REQ sans AC → violation type `"REQ_NO_AC"` avec message en français
- DES sans AC → violation type `"DES_NO_AC"`
- AC inexistante dans DES.coversAC → violation type `"AC_NOT_FOUND"`
- AC orpheline → violation type `"AC_ORPHAN"`
- `fire("approve")` avec violations présentes → `IllegalStateException` avec message

**Commande de vérification :** `./gradlew.bat test --tests "*CoherenceCheckTest"` → ÉCHOUE

**Dépend de :** TASK-002.2, TASK-003.2

---

### TASK-005.2 [x] [GREEN] — Implémenter checkCoherence + CoherenceViolation + Guard

**Fichiers à créer dans `src/main/java/ia/mahi/workflow/core/` :**
- `CoherenceViolation.java` — record `(String type, String itemId, String message)`

**Fichiers à modifier :**
- `WorkflowService.java` — ajouter `checkCoherence(String flowId)` : algorithme DES-005, retourner `List<CoherenceViolation>`
- `WorkflowTools.java` — ajouter `@Tool mahi_check_coherence(String flowId)` → délègue à `workflowService.checkCoherence(flowId)`
- `SpecWorkflowDefinition.java` — ajouter Guard sur la transition `REQUIREMENTS_DEFINED::DEFINE_DESIGN` qui appelle `checkCoherence` et lève `IllegalStateException` si violations. **Note :** le guard nécessite l'injection de `WorkflowService` dans `SpecWorkflowDefinition` — adapter le constructeur ou injecter via méthode `setWorkflowService`.

**Commande de vérification :** `./gradlew.bat test` → tous les tests passent

**Dépend de :** TASK-005.1

---

## TASK-006 — mahi_save_context + écriture context.md

**Implémente :** DES-006
**Satisfait :** REQ-005

**Dépend de :** TASK-001.2

### TASK-006.1 [x] [RED] — Tests de persistance de session

**Fichier :** `src/test/java/ia/mahi/engine/SessionContextTest.java`

Tests (dérivés du contrat de test DES-006) :
- `saveContext` → `getWorkflow` retourne `sessionContext` avec tous les champs
- Double appel `saveContext` → le second écrase le premier
- `getWorkflow` sans contexte sauvegardé → `sessionContext` absent du JSON (`@JsonInclude(NON_NULL)`)
- Fichier `context.md` créé dans le chemin spec (si `metadata.specPath` présent)

**Commande de vérification :** `./gradlew.bat test --tests "*SessionContextTest"` → ÉCHOUE

**Dépend de :** TASK-001.2

---

### TASK-006.2 [x] [GREEN] — Implémenter saveContext dans WorkflowService + outil

**Fichiers à modifier :**
- `WorkflowService.java` — ajouter `saveContext(String flowId, SessionContext ctx)` :
  1. Charger `WorkflowContext` via `store.load(flowId)`
  2. Affecter `ctx.setSavedAt(Instant.now())` puis `workflowContext.setSessionContext(ctx)`
  3. Écrire `context.md` via `artifactService` si `metadata.specPath` présent
  4. `store.save(workflowContext)`
- `WorkflowTools.java` — ajouter `@Tool mahi_save_context(String flowId, SessionContext context)`

**Commande de vérification :** `./gradlew.bat test` → tous les tests passent

**Dépend de :** TASK-006.1

---

## TASK-007 — Métriques phaseDurations dans mahi_get_workflow

**Implémente :** DES-007
**Satisfait :** REQ-006

**Dépend de :** TASK-001.2

### TASK-007.1 [x] [RED] — Tests des métriques de durée

**Fichier :** `src/test/java/ia/mahi/engine/PhaseDurationsTest.java`

Tests (dérivés du contrat de test DES-007) :
- Workflow spec avec 2 transitions (requirements → design après 47 min) → `phaseDurations.requirements = 2820000`
- Phase en cours → durée partielle depuis l'entrée (REQ-006.AC-2)
- Phase non encore atteinte → absente du map
- Re-entrée dans une phase (REANALYZING → DEFINE_REQUIREMENTS) → durées additionnées
- Workflow non-spec (`adr`) → `phaseDurations` vide (mapping vide)

**Commande de vérification :** `./gradlew.bat test --tests "*PhaseDurationsTest"` → ÉCHOUE

**Dépend de :** TASK-001.2

---

### TASK-007.2 [x] [GREEN] — Implémenter le calcul phaseDurations dans WorkflowService.get()

**Fichiers à modifier :**
- `WorkflowDefinition.java` — ajouter méthode default `Map<String, String> getStateToPhaseMapping()` → retourne `Map.of()` par défaut
- `SpecWorkflowDefinition.java` — override `getStateToPhaseMapping()` avec le mapping DES-007 (6 états → phases)
- `WorkflowService.java` — modifier `get(String flowId)` : après `engine.get(flowId)`, calculer `phaseDurations` via algorithme DES-007, mettre le résultat dans `context.getMetadata().put("phaseDurations", phaseDurations)`

**Commande de vérification :** `./gradlew.bat test` → tous les tests passent

**Dépend de :** TASK-007.1

---

## TASK-008 — Test de convention de nommage WorkflowToolsNamingTest

**Implémente :** DES-008
**Satisfait :** REQ-NF-001

**Dépend de :** TASK-002.2, TASK-003.2, TASK-005.2, TASK-006.2

### TASK-008.1 [x] [RED] — Créer WorkflowToolsNamingTest (auto-validant)

**Fichier :** `src/test/java/ia/mahi/mcp/WorkflowToolsNamingTest.java`

Test par réflexion : tous les `@Tool(name=...)` de `WorkflowTools` matchent `^mahi_[a-z]+(_[a-z]+)*$`

**Commande de vérification :** `./gradlew.bat test --tests "*WorkflowToolsNamingTest"` → doit PASSER dès la GREEN si les outils sont déjà conformes, ou ÉCHOUER si un nom viole la convention

**Note :** ici RED et GREEN sont concomitants — la classe de test est à la fois le RED (avant que tous les outils soient créés) et le GREEN (après). Pas de subtask REFACTOR needed.

**Dépend de :** TASK-002.2, TASK-003.2, TASK-005.2, TASK-006.2

---

## Graphe de dépendances

```
TASK-001.1
    └── TASK-001.2
            └── TASK-001.2b
                    ├── TASK-001.3
                    ├── TASK-002.1 ──→ TASK-002.2
                    │                       └──────────────────────────┐
                    ├── TASK-003.1 ──→ TASK-003.2                      │
                    │                       └──────────────────────────┤
                    ├── TASK-006.1 ──→ TASK-006.2                      │
                    └── TASK-007.1 ──→ TASK-007.2             TASK-004.1 ──→ TASK-004.2
                                                              TASK-005.1 ──→ TASK-005.2
                                                                   └── TASK-008.1
```

**Lot 1 (séquentiel) :** TASK-001.1 → TASK-001.2 → TASK-001.2b → TASK-001.3

**Lot 2 (4 en parallèle, après TASK-001.2b) :**
TASK-002.1 // TASK-003.1 // TASK-006.1 // TASK-007.1

**Lot 3 (4 en parallèle) :**
TASK-002.2 // TASK-003.2 // TASK-006.2 // TASK-007.2

**Lot 4 (2 en parallèle, après TASK-002.2 + TASK-003.2) :**
TASK-004.1 // TASK-005.1

**Lot 5 (2 en parallèle) :**
TASK-004.2 // TASK-005.2

**Lot 6 :** TASK-008.1 (après TASK-002.2, TASK-003.2, TASK-005.2, TASK-006.2)

---

## Résumé

**8 tâches, 18 sous-tâches** (1 correction pass 2 appliquée : TASK-001.2 splitté en 001.2 + 001.2b)
- Lot 1 (séquentiel) : TASK-001 (4 sous-tâches)
- Lot 2 (4 en parallèle) : TASK-002.1, TASK-003.1, TASK-006.1, TASK-007.1
- Lot 3 (4 en parallèle) : TASK-002.2, TASK-003.2, TASK-006.2, TASK-007.2
- Lot 4 (2 en parallèle) : TASK-004.1, TASK-005.1
- Lot 5 (2 en parallèle) : TASK-004.2, TASK-005.2
- Lot 6 (1) : TASK-008.1
