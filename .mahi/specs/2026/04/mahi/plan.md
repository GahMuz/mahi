# Plan d'implémentation : mahi — Serveur MCP Java pour la machine d'état SDD

## Règles

- [ ] Chaque nouvelle classe métier a une responsabilité unique (SOLID-S)
- [ ] Les types d'états et d'événements sont des enums, jamais des String bruts
- [ ] Toute logique métier est testée avant d'être implémentée (TDD RED/GREEN)
- [ ] La persistance n'est appelée qu'en cas de succès total de la transition
- [ ] Le fat jar est produit par `./gradlew bootJar` sans erreur
- [ ] `./gradlew test` passe sans erreur avant tout commit

---

## Graphe de dépendances

```
TASK-001 (Gradle)
    └─► TASK-002 (core domain)
            ├─► TASK-003 (WorkflowEngine)
            │       ├─► TASK-004 (WorkflowService + WorkflowStore)
            │       │       ├─► TASK-005 (SpecWorkflowDefinition)
            │       │       ├─► TASK-006 (AdrWorkflowDefinition)
            │       │       ├─► TASK-007 (DebugWorkflowDefinition)
            │       │       └─► TASK-008 (FindBugWorkflowDefinition)
            │       └─► TASK-009 (ArtifactService + GitWorktreeService)
            │               └─► TASK-010 (WorkflowTools @McpTool)
            │                       └─► TASK-011 (ArtifactResources @McpResource)
            └─► TASK-012 (Application bootstrap + application.properties)
```

Parallélisable en premier lot (après TASK-004) : TASK-005, TASK-006, TASK-007, TASK-008 (indépendants).

---

## TASK-001 : Migration Gradle + structure projet

**Implémente :** DES-001, DES-008
**Satisfait :** REQ-008

### TASK-001.1 [CONFIG] — Créer build.gradle.kts et settings.gradle.kts

- Créer `mahi-mcp/settings.gradle.kts` : `rootProject.name = "mahi-mcp-server"`
- Créer `mahi-mcp/build.gradle.kts` avec plugins Spring Boot 3.4.4, Spring AI BOM 1.0.0, Java 21
- Dépendances : `spring-ai-starter-mcp-server`, `spring-boot-starter`, `spring-boot-starter-validation`, `jackson-databind`
- Dépendances test : `spring-boot-starter-test`
- Tâche `bootJar` : archiveFileName = `"mahi-mcp-server.jar"`
- JaCoCo plugin pour la couverture
- Vérification : `./gradlew dependencies` sans erreur de résolution

### TASK-001.2 [CONFIG] — Supprimer pom.xml et créer l'arborescence source

- Supprimer `mahi-mcp/pom.xml`
- Créer les répertoires Java vides :
  - `src/main/java/ia/mahi/mcp/`
  - `src/main/java/ia/mahi/workflow/core/`
  - `src/main/java/ia/mahi/workflow/engine/`
  - `src/main/java/ia/mahi/workflow/definitions/spec/`
  - `src/main/java/ia/mahi/workflow/definitions/adr/`
  - `src/main/java/ia/mahi/workflow/definitions/debug/`
  - `src/main/java/ia/mahi/workflow/definitions/findbug/`
  - `src/main/java/ia/mahi/store/`
  - `src/main/java/ia/mahi/service/`
  - `src/main/resources/`
  - `src/test/java/ia/mahi/engine/`
  - `src/test/java/ia/mahi/definitions/`
- Créer `src/main/resources/application.properties` (STDIO, no-web, server name=mahi)
- Vérification : `./gradlew compileJava` (vide — pas d'erreur)

---

## TASK-002 : Core domain — interfaces et types de base

**Implémente :** DES-002
**Satisfait :** REQ-001, REQ-005

### TASK-002.1 [GREEN] — Interfaces marqueurs et types de base

Fichiers à créer dans `src/main/java/ia/mahi/workflow/core/` :
- `WorkflowState.java` — interface marqueur (`String name()`)
- `WorkflowEvent.java` — interface marqueur (`String name()`)
- `Guard.java` — `@FunctionalInterface void check(WorkflowContext ctx)`
- `Action.java` — `@FunctionalInterface void execute(WorkflowContext ctx)`
- `ArtifactDefinition.java` — record `(String name)`
- `TransitionDefinition.java` — record `(String from, String event, String to, List<Guard> guards, List<Action> actions)`
- `TransitionRecord.java` — record `(String fromState, String event, String toState, Instant occurredAt)`
- `WorkflowDefinition.java` — interface avec `getType()`, `getInitialState()`, `getArtifacts()`, `getTransitions()`, `getInvalidationGraph()`
- Vérification : `./gradlew compileJava` sans erreur

### TASK-002.2 [GREEN] — ArtifactState et WorkflowContext

Fichiers à créer dans `src/main/java/ia/mahi/workflow/core/` :
- `ArtifactState.java` — classe avec `name`, `status` (`MISSING`/`DRAFT`/`VALID`/`STALE`), `version`, `path`, `updatedAt` + méthodes `markDraft`, `markValid`, `markStale`
- `WorkflowContext.java` — classe avec `flowId`, `workflowType`, `state`, `Map<String, ArtifactState> artifacts`, `Map<String, Object> metadata`, `List<TransitionRecord> history`, `updatedAt` + méthode `addTransition(fromState, event, toState)`
- `WorkflowRegistry.java` — `Map<String, WorkflowDefinition>` avec `register()` et `get()` (lève `IllegalArgumentException` si type inconnu)
- Vérification : `./gradlew compileJava` sans erreur

---

## TASK-003 : WorkflowEngine

**Implémente :** DES-003
**Satisfait :** REQ-001, REQ-002, REQ-004

### TASK-003.1 [RED] — Tests du WorkflowEngine

Fichier : `src/test/java/ia/mahi/engine/WorkflowEngineTest.java`

Tests à écrire (tous en échec) :
- `shouldCreateWorkflow()` — flow créé avec état initial correct
- `shouldFireValidTransition()` — état mis à jour, historique contient 1 entrée
- `shouldRejectTransitionFromWrongState()` — `IllegalStateException`
- `shouldRejectUnknownEvent()` — `IllegalStateException("Transition invalide : ...")`
- `shouldFailOnGuardViolation_stateUnchanged()` — état et fichier JSON inchangés
- `shouldRecordMultipleTransitionsInHistory()` — historique ordonné
- `shouldPropagateInvalidationDownstream()` — artefacts downstream passent à STALE
- `shouldRejectDuplicateFlowId()` — `IllegalStateException("Workflow already exists: ...")`

Dépend de : `WorkflowEngine` (non encore implémenté — utiliser un workflow de test minimal en mémoire)
Vérification : `./gradlew test --tests "ia.mahi.engine.WorkflowEngineTest"` → tous FAILED (RED)

### TASK-003.2 [GREEN] — Implémenter WorkflowEngine

Fichier : `src/main/java/ia/mahi/workflow/engine/WorkflowEngine.java`

Logique (séquence garantie) :
1. `store.load(flowId)` — charger
2. Résoudre `"<state>::<event>"` dans `definition.getTransitions()` — `IllegalStateException` si absent
3. Exécuter tous les `guards` — `IllegalStateException` si l'un échoue (état inchangé)
4. Exécuter toutes les `actions`
5. `context.setState(transition.getTo())`
6. `context.addTransition(fromState, event, toState)`
7. `store.save(context)` — unique point de persistance

Vérification : `./gradlew test --tests "ia.mahi.engine.WorkflowEngineTest"` → tous PASSED (GREEN)

### TASK-003.3 [REFACTOR] — Extraire la résolution de transition

- Extraire la résolution de la clé de transition dans une méthode privée `resolveTransition(context, event)`
- Améliorer les messages d'erreur pour inclure l'état et l'événement
- Vérification : `./gradlew test --tests "ia.mahi.engine.WorkflowEngineTest"` → toujours PASSED

---

## TASK-004 : WorkflowStore et WorkflowService

**Implémente :** DES-003
**Satisfait :** REQ-NF-002

### TASK-004.1 [GREEN] — WorkflowStore (persistance JSON)

Fichier : `src/main/java/ia/mahi/store/WorkflowStore.java`

- Persistance dans `.mahi/flows/<flowId>.json`
- `ObjectMapper` avec `JavaTimeModule` et `INDENT_OUTPUT`
- Méthodes : `load(flowId)`, `save(context)`, `exists(flowId)`
- `load()` lève `IllegalArgumentException("Workflow not found: <id>")` si absent
- Vérification : `./gradlew compileJava` sans erreur

### TASK-004.2 [GREEN] — WorkflowService (façade Spring @Service)

Fichier : `src/main/java/ia/mahi/workflow/engine/WorkflowService.java`

Méthodes publiques : `create`, `get`, `fire`, `writeArtifact`, `addRequirementInfo`, `addDesignInfo`, `createWorktree`, `removeWorktree`
- `writeArtifact` : écrit via `ArtifactService`, met à jour `ArtifactState` (DRAFT → VALID)
- `addRequirementInfo` : enrichit metadata, déclenche `engine.invalidateFrom("requirements")`
- `addDesignInfo` : enrichit metadata, déclenche `engine.invalidateFrom("design")`

Vérification : `./gradlew compileJava` sans erreur

---

## TASK-005 : SpecWorkflowDefinition

**Implémente :** DES-005
**Satisfait :** REQ-005

### TASK-005.1 [RED] — Tests du chemin nominal spec

Fichier : `src/test/java/ia/mahi/definitions/SpecWorkflowDefinitionTest.java`

- `shouldCompleteFullSpecWorkflow()` — DRAFT → SCENARIO_DEFINED → PROJECT_RULES_LOADED → REQUIREMENTS_DEFINED → DESIGN_DEFINED → IMPLEMENTATION_PLAN_DEFINED → WORKTREE_CREATED → IMPLEMENTING → VALIDATING → FINALIZING → RETROSPECTIVE_DONE → DONE
- `shouldRejectTransitionFromWrongState()` — `DRAFT` + event `LOAD_RULES` → `IllegalStateException`
- `shouldFailWhenScenarioNotValidForLoadRules()` — guard "scenario VALID" échoue
- `shouldMarkDesignStaleWhenRequirementsInvalidated()`

Vérification : `./gradlew test --tests "ia.mahi.definitions.SpecWorkflowDefinitionTest"` → FAILED (RED)

### TASK-005.2 [GREEN] — Implémenter SpecState, SpecEvent, SpecWorkflowDefinition

Fichiers dans `src/main/java/ia/mahi/workflow/definitions/spec/` :
- `SpecState.java` — enum avec 13 valeurs
- `SpecEvent.java` — enum avec 11 valeurs
- `SpecWorkflowDefinition.java` — toutes les transitions avec guards et graphe d'invalidation

Vérification : `./gradlew test --tests "ia.mahi.definitions.SpecWorkflowDefinitionTest"` → PASSED

---

## TASK-006 : AdrWorkflowDefinition

**Implémente :** DES-005
**Satisfait :** REQ-005

### TASK-006.1 [RED] — Tests du chemin nominal ADR

Fichier : `src/test/java/ia/mahi/definitions/AdrWorkflowDefinitionTest.java`

- `shouldCompleteFullAdrWorkflow()` — FRAMING → EXPLORING → DISCUSSING → DECIDING → RETROSPECTIVE → DONE
- `shouldRejectDirectTransitionToDeciding()`
- `shouldMarkOptionsStaleWhenFramingInvalidated()`

Vérification : `./gradlew test --tests "ia.mahi.definitions.AdrWorkflowDefinitionTest"` → FAILED

### TASK-006.2 [GREEN] — Implémenter AdrState, AdrEvent, AdrWorkflowDefinition

Fichiers dans `src/main/java/ia/mahi/workflow/definitions/adr/`
Vérification : `./gradlew test --tests "ia.mahi.definitions.AdrWorkflowDefinitionTest"` → PASSED

---

## TASK-007 : DebugWorkflowDefinition

**Implémente :** DES-005
**Satisfait :** REQ-005

### TASK-007.1 [RED] — Tests du chemin nominal debug

Fichier : `src/test/java/ia/mahi/definitions/DebugWorkflowDefinitionTest.java`

- `shouldCompleteFullDebugWorkflow()` — REPORTED → REPRODUCING → ANALYZING → FIXING → VALIDATING → DONE
- `shouldRejectFixWithoutAnalysis()`

Vérification : `./gradlew test --tests "ia.mahi.definitions.DebugWorkflowDefinitionTest"` → FAILED

### TASK-007.2 [GREEN] — Implémenter DebugState, DebugEvent, DebugWorkflowDefinition

Fichiers dans `src/main/java/ia/mahi/workflow/definitions/debug/`
Vérification : `./gradlew test --tests "ia.mahi.definitions.DebugWorkflowDefinitionTest"` → PASSED

---

## TASK-008 : FindBugWorkflowDefinition

**Implémente :** DES-005
**Satisfait :** REQ-005

### TASK-008.1 [RED] — Tests du chemin nominal find-bug

Fichier : `src/test/java/ia/mahi/definitions/FindBugWorkflowDefinitionTest.java`

- `shouldCompleteFullFindBugWorkflow()` — SCANNING → TRIAGING → REPORTING → DONE
- `shouldHaveNoBugListStaleOnNoInvalidation()`

Vérification : `./gradlew test --tests "ia.mahi.definitions.FindBugWorkflowDefinitionTest"` → FAILED

### TASK-008.2 [GREEN] — Implémenter FindBugState, FindBugEvent, FindBugWorkflowDefinition

Fichiers dans `src/main/java/ia/mahi/workflow/definitions/findbug/`
Vérification : `./gradlew test --tests "ia.mahi.definitions.FindBugWorkflowDefinitionTest"` → PASSED

---

## TASK-009 : ArtifactService et GitWorktreeService

**Implémente :** DES-004
**Satisfait :** REQ-003

### TASK-009.1 [GREEN] — ArtifactService

Fichier : `src/main/java/ia/mahi/service/ArtifactService.java`

- `writeArtifact(flowId, artifactName, content)` → écrit dans `.mahi/artifacts/<flowId>/<artifactName>.md`
- `readArtifact(flowId, artifactName)` → lit le fichier, lève `IllegalArgumentException` si absent
- Vérification : `./gradlew compileJava` sans erreur

### TASK-009.2 [GREEN] — GitWorktreeService

Fichier : `src/main/java/ia/mahi/service/GitWorktreeService.java`

- `createWorktree(flowId)` → `git worktree add -b mahi/<flowId> .worktrees/<flowId>`
- `removeWorktree(path)` → `git worktree remove <path> --force`
- Lève `IllegalStateException` si la commande git échoue (code != 0)
- Vérification : `./gradlew compileJava` sans erreur

---

## TASK-010 : WorkflowTools @McpTool

**Implémente :** DES-006
**Satisfait :** REQ-006

### TASK-010.1 [RED] — Tests des outils MCP (intégration)

Fichier : `src/test/java/ia/mahi/mcp/WorkflowToolsTest.java`

- `shouldCreateAndGetWorkflow()`
- `shouldFireEventAndUpdateState()`
- `shouldReturnErrorOnDuplicateFlowId()`
- `shouldReturnErrorOnUnknownFlowId()`
- `shouldWriteArtifactAndMarkValid()`

Vérification : `./gradlew test --tests "ia.mahi.mcp.WorkflowToolsTest"` → FAILED

### TASK-010.2 [GREEN] — Implémenter WorkflowTools

Fichier : `src/main/java/ia/mahi/mcp/WorkflowTools.java`

8 outils `@McpTool` : `mahi_create_workflow`, `mahi_get_workflow`, `mahi_fire_event`, `mahi_write_artifact`, `mahi_add_requirement_info`, `mahi_add_design_info`, `mahi_create_worktree`, `mahi_remove_worktree`

Vérification : `./gradlew test --tests "ia.mahi.mcp.WorkflowToolsTest"` → PASSED

---

## TASK-011 : ArtifactResources @McpResource

**Implémente :** DES-007
**Satisfait :** REQ-007

### TASK-011.1 [GREEN] — Implémenter ArtifactResources

Fichier : `src/main/java/ia/mahi/mcp/ArtifactResources.java`

- `@McpResource` avec URI `mahi://artifacts/{flowId}/{artifactName}`
- Retourne le contenu Markdown brut du fichier
- Lève `IllegalArgumentException` si fichier absent (pas de NPE)

Vérification : `./gradlew compileJava` sans erreur

---

## TASK-012 : Bootstrap application et configuration

**Implémente :** DES-001
**Satisfait :** REQ-008, REQ-NF-001

### TASK-012.1 [GREEN] — MahiMcpApplication et application.properties

Fichier : `src/main/java/ia/mahi/MahiMcpApplication.java`

- `@SpringBootApplication`
- `@Bean WorkflowRegistry` : enregistre spec, adr, debug, find-bug
- Fichier `src/main/resources/application.properties` :
  ```
  spring.main.web-application-type=none
  spring.ai.mcp.server.name=mahi
  spring.ai.mcp.server.version=0.1.0
  spring.ai.mcp.server.stdio=true
  spring.ai.mcp.server.type=SYNC
  ```
- Vérification : `./gradlew bootJar` → `build/libs/mahi-mcp-server.jar` produit

### TASK-012.2 [VERIFY] — Vérification finale

- `./gradlew test` → BUILD SUCCESS (tous les tests)
- `./gradlew bootJar` → fat jar produit
- `java -jar build/libs/mahi-mcp-server.jar` → démarre sans erreur en moins de 5 secondes

---

## Résumé

- **12 tâches parentes**, **26 sous-tâches**
- Premier lot parallélisable (après TASK-004) : TASK-005, TASK-006, TASK-007, TASK-008
- TDD appliqué sur : TASK-003, TASK-005, TASK-006, TASK-007, TASK-008, TASK-010
- Couverture : tous les DES couverts, tous les REQ satisfaits
