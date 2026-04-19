# Design : mahi — Serveur MCP Java pour la machine d'état SDD

## Contexte

Projet nouveau : aucun module existant à préserver. Migration de Maven vers Gradle. ADR-001 décide : Java 21, Spring Boot 3.4.4, Spring AI MCP Server, fat jar Gradle, types stricts enum, historique des transitions, `@McpResource` pour les artefacts.

---

## DES-001 : Architecture générale et structure du projet

**Problème :** Définir la structure du module `mahi-mcp/` pour respecter les principes SOLID et permettre l'extension future (nouveaux workflows sans modifier le moteur).

**Approche retenue :** Architecture en couches hexagonales légères avec séparation stricte moteur / définitions workflow / exposition MCP.

```
mahi-mcp/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/ia/mahi/
│   │   │   ├── MahiMcpApplication.java           # Bootstrap Spring Boot
│   │   │   ├── mcp/
│   │   │   │   ├── WorkflowTools.java             # @McpTool — outils MCP
│   │   │   │   └── ArtifactResources.java         # @McpResource — ressources artefacts
│   │   │   ├── workflow/
│   │   │   │   ├── core/
│   │   │   │   │   ├── WorkflowState.java         # Interface marqueur pour enums d'états
│   │   │   │   │   ├── WorkflowEvent.java         # Interface marqueur pour enums d'événements
│   │   │   │   │   ├── Guard.java                 # @FunctionalInterface
│   │   │   │   │   ├── Action.java                # @FunctionalInterface
│   │   │   │   │   ├── TransitionDefinition.java  # (from, event, to, guards, actions)
│   │   │   │   │   ├── ArtifactDefinition.java    # Déclaration d'artefact
│   │   │   │   │   ├── WorkflowDefinition.java    # Interface du contrat workflow
│   │   │   │   │   ├── WorkflowContext.java       # État runtime d'un flow
│   │   │   │   │   ├── ArtifactState.java         # État d'un artefact (MISSING/DRAFT/VALID/STALE)
│   │   │   │   │   ├── TransitionRecord.java      # Entrée d'historique
│   │   │   │   │   └── WorkflowRegistry.java      # Registre des WorkflowDefinition
│   │   │   │   ├── engine/
│   │   │   │   │   ├── WorkflowEngine.java        # Moteur principal (create/fire/invalidate)
│   │   │   │   │   └── WorkflowService.java       # Façade Spring (@Service)
│   │   │   │   └── definitions/
│   │   │   │       ├── spec/
│   │   │   │       │   ├── SpecState.java         # enum des états spec
│   │   │   │       │   ├── SpecEvent.java         # enum des événements spec
│   │   │   │       │   └── SpecWorkflowDefinition.java
│   │   │   │       ├── adr/
│   │   │   │       │   ├── AdrState.java
│   │   │   │       │   ├── AdrEvent.java
│   │   │   │       │   └── AdrWorkflowDefinition.java
│   │   │   │       ├── debug/
│   │   │   │       │   ├── DebugState.java
│   │   │   │       │   ├── DebugEvent.java
│   │   │   │       │   └── DebugWorkflowDefinition.java
│   │   │   │       └── findbug/
│   │   │   │           ├── FindBugState.java
│   │   │   │           ├── FindBugEvent.java
│   │   │   │           └── FindBugWorkflowDefinition.java
│   │   │   ├── store/
│   │   │   │   └── WorkflowStore.java             # Persistance JSON (.mahi/flows/)
│   │   │   └── service/
│   │   │       ├── ArtifactService.java           # Lecture/écriture .mahi/artifacts/
│   │   │       └── GitWorktreeService.java        # git worktree add/remove
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/ia/mahi/
│           ├── engine/
│           │   └── WorkflowEngineTest.java
│           └── definitions/
│               ├── SpecWorkflowDefinitionTest.java
│               ├── AdrWorkflowDefinitionTest.java
│               ├── DebugWorkflowDefinitionTest.java
│               └── FindBugWorkflowDefinitionTest.java
```

**Justification SOLID :**
- **S** : chaque classe a une responsabilité unique (moteur / persistance / exposition MCP / définitions)
- **O** : `WorkflowDefinition` est une interface — ajouter un workflow = ajouter une classe sans modifier le moteur
- **L** : toutes les `WorkflowDefinition` sont interchangeables via le registry
- **I** : `WorkflowTools` et `ArtifactResources` sont séparés (ne pas forcer les outils MCP à dépendre de la logique de ressources)
- **D** : `WorkflowEngine` dépend de `WorkflowRegistry` (interface) et `WorkflowStore` (interface), pas des implémentations concrètes

**Implémente :** REQ-005, REQ-008

**Contrat de test :**
- Le projet compile sans erreur avec `./gradlew build`
- Le fat jar est produit dans `build/libs/`
- `java -jar mahi-mcp-server-*.jar` démarre et imprime les outils MCP disponibles sur STDOUT

---

## DES-002 : Types stricts — enums d'états et d'événements

**Problème :** Les String bruts pour les états et événements ne sont pas vérifiés à la compilation et permettent les fautes de frappe silencieuses.

**Approche retenue :** Interfaces marqueurs `WorkflowState` et `WorkflowEvent` implémentées par des enums spécifiques à chaque workflow. `WorkflowContext` stocke l'état courant comme `String` (pour la sérialisation JSON), mais le moteur valide via les enums à l'exécution.

```java
// Interfaces marqueurs
public interface WorkflowState { String name(); }
public interface WorkflowEvent { String name(); }

// Enum spec
public enum SpecState implements WorkflowState {
    DRAFT, SCENARIO_DEFINED, PROJECT_RULES_LOADED, REQUIREMENTS_DEFINED,
    DESIGN_DEFINED, IMPLEMENTATION_PLAN_DEFINED, WORKTREE_CREATED,
    IMPLEMENTING, REANALYZING, VALIDATING, FINALIZING, RETROSPECTIVE_DONE, DONE
}

public enum SpecEvent implements WorkflowEvent {
    DEFINE_SCENARIO, LOAD_RULES, DEFINE_REQUIREMENTS, DEFINE_DESIGN,
    DEFINE_PLAN, CREATE_WORKTREE, START_IMPLEMENTATION, VALIDATE,
    FINALIZE, WRITE_RETROSPECTIVE, COMPLETE
}
```

**Clé de transition** : `"<STATE_NAME>::<EVENT_NAME>"` — validée par la `WorkflowDefinition` lors de l'enregistrement.

**Justification SOLID :**
- **O** : les enums s'étendent par ajout de valeurs, le moteur ne change pas
- **D** : le moteur reçoit des `String` (sérialisables) et délègue la validation aux définitions

**Implémente :** REQ-001, REQ-005

**Contrat de test :**
- `WorkflowEngine.fire()` avec un événement inconnu lève `IllegalStateException("Transition invalide : DRAFT::UNKNOWN_EVENT")`
- `WorkflowEngine.fire()` avec un état source incorrect lève `IllegalStateException`
- Les enums de chaque workflow couvrent tous les états définis dans REQ-005

---

## DES-003 : Moteur de machine d'état et historique des transitions

**Problème :** Le moteur doit garantir l'ordre d'exécution (guards → actions → changement d'état → persistance) et l'atomicité (pas de persistance si un guard échoue).

**Approche retenue :** `WorkflowEngine` centralise la logique ; `WorkflowService` est la façade Spring. `WorkflowContext` embarque une `List<TransitionRecord>` pour l'historique.

```java
// TransitionRecord — entrée d'historique immuable
public record TransitionRecord(
    String fromState,
    String event,
    String toState,
    Instant occurredAt
) {}

// WorkflowContext — ajout du champ history
private List<TransitionRecord> history = new ArrayList<>();

// WorkflowEngine.fire() — séquence garantie
public WorkflowContext fire(String flowId, String event) {
    WorkflowContext context = store.load(flowId);        // 1. charger
    TransitionDefinition transition = resolve(context, event); // 2. résoudre
    transition.getGuards().forEach(g -> g.check(context)); // 3. guards (fail-fast)
    transition.getActions().forEach(a -> a.execute(context)); // 4. actions
    context.setState(transition.getTo());                // 5. changer état
    context.addTransition(fromState, event, transition.getTo()); // 6. historique
    return store.save(context);                         // 7. persister (atomique)
}
```

**Atomicité :** si un guard lève une exception, les étapes 4-7 ne s'exécutent pas. La persistance n'est appelée qu'en cas de succès total.

**Implémente :** REQ-001, REQ-002

**Contrat de test :**
- Guard échoué : état inchangé, fichier JSON inchangé
- Transition réussie : historique contient 1 entrée avec `fromState`, `event`, `toState`, `occurredAt`
- Transitions multiples : historique contient N entrées dans l'ordre chronologique
- `store.load()` après `fire()` retourne le contexte avec l'historique à jour

---

## DES-004 : Gestion des artefacts et graphe d'invalidation

**Problème :** Les artefacts ont un cycle de vie (MISSING → DRAFT → VALID → STALE) et des dépendances entre eux (modifier requirements doit invalider design et plan).

**Approche retenue :**

`ArtifactState` conserve son cycle de vie actuel. Le graphe d'invalidation est défini par `WorkflowDefinition.getInvalidationGraph()` et propagé récursivement par le moteur.

```java
// Propagation récursive du graphe d'invalidation
private void propagateInvalidation(WorkflowContext context,
                                   WorkflowDefinition definition,
                                   String artifactName) {
    List<String> impacted = definition.getInvalidationGraph()
                                      .getOrDefault(artifactName, List.of());
    for (String downstream : impacted) {
        ArtifactState state = context.getArtifacts().get(downstream);
        if (state != null && !"MISSING".equals(state.getStatus())) {
            state.markStale();
            propagateInvalidation(context, definition, downstream); // récursif
        }
    }
}
```

`ArtifactService` écrit les fichiers dans `.mahi/artifacts/<flowId>/<artifactName>.md`.

**Implémente :** REQ-003, REQ-004

**Contrat de test :**
- Écrire "requirements" → statut `VALID`
- Appeler `addRequirementInfo` → "design" passe à `STALE`, "plan" passe à `STALE`
- Appeler `addDesignInfo` → "plan" passe à `STALE`, "requirements" reste inchangé
- Artefact inconnu dans `writeArtifact` → `IllegalArgumentException("Unknown artifact: <name>")`

---

## DES-005 : Quatre WorkflowDefinition — spec, ADR, debug, find-bug

**Problème :** Chaque workflow a ses propres états, événements et graphe d'invalidation.

**Approche retenue :** Chaque `WorkflowDefinition` est une classe autonome enregistrée dans `WorkflowRegistry` au démarrage via `@Bean`.

**SpecWorkflowDefinition :** états/événements REQ-005 (déjà esquisssés dans le code fourni), enrichis avec enums.

**AdrWorkflowDefinition :**
- États : `FRAMING` → `EXPLORING` → `DISCUSSING` → `DECIDING` → `RETROSPECTIVE` → `DONE`
- Événements : `START_EXPLORATION`, `START_DISCUSSION`, `FORMALIZE_DECISION`, `START_RETROSPECTIVE`, `COMPLETE`
- Artefacts : `framing`, `options`, `adr`, `retrospective`
- Invalidation : `framing` invalide `options`, `adr` ; `options` invalide `adr`

**DebugWorkflowDefinition :**
- États : `REPORTED` → `REPRODUCING` → `ANALYZING` → `FIXING` → `VALIDATING` → `DONE`
- Événements : `REPRODUCE`, `ANALYZE`, `FIX`, `VALIDATE`, `CLOSE`
- Artefacts : `bug-report`, `reproduction`, `root-cause`, `fix`, `test-report`
- Invalidation : `root-cause` invalide `fix`

**FindBugWorkflowDefinition :**
- États : `SCANNING` → `TRIAGING` → `REPORTING` → `DONE`
- Événements : `TRIAGE`, `REPORT`, `COMPLETE`
- Artefacts : `scan-report`, `triage`, `bug-list`
- Invalidation : aucune (workflow linéaire)

**Implémente :** REQ-005

**Contrat de test :**
- Chaque `WorkflowDefinition` : chemin nominal complet sans exception
- Registre : `workflowRegistry.get("unknown-type")` lève `IllegalArgumentException`
- Chaque workflow a au moins 1 artefact et 1 transition

---

## DES-006 : Exposition MCP — outils `@McpTool`

**Problème :** Le LLM doit pouvoir appeler les opérations Mahi via des outils MCP nommés et documentés.

**Approche retenue :** `WorkflowTools` est un `@Service` Spring AI annoté `@McpTool`. Chaque méthode publique avec `@McpTool` est exposée comme outil MCP. Les paramètres sont annotés `@McpToolParam`.

Les outils retournent `WorkflowContext` (sérialisé en JSON par Spring AI MCP Server).

```java
@McpTool(name = "mahi_fire_event",
         description = "Apply an event transition to a workflow. Returns the updated context with new state and history.")
public WorkflowContext fireEvent(
    @McpToolParam(description = "Workflow identifier", required = true) String flowId,
    @McpToolParam(description = "Event name (use enum name, e.g. DEFINE_SCENARIO)", required = true) String event) {
    return workflowService.fire(flowId, event);
}
```

**Gestion des erreurs :** les exceptions Java sont propagées au client MCP sous forme de messages d'erreur lisibles (comportement par défaut de Spring AI MCP Server).

**Implémente :** REQ-006

**Contrat de test :**
- `mahi_create_workflow` avec `flowId` existant → erreur `"Workflow already exists: <id>"`
- `mahi_get_workflow` avec `flowId` inexistant → erreur `"Workflow not found: <id>"`
- `mahi_fire_event` valide → retourne `WorkflowContext` avec `state` mis à jour

---

## DES-007 : Exposition MCP — ressources `@McpResource`

**Problème :** Le LLM doit pouvoir lire le contenu des artefacts sans appeler un outil (lecture seule, protocole MCP Resource).

**Approche retenue :** `ArtifactResources` est un `@Service` Spring AI avec des méthodes annotées `@McpResource`. Pattern URI : `mahi://artifacts/{flowId}/{artifactName}`.

```java
@McpResource(
    uri = "mahi://artifacts/{flowId}/{artifactName}",
    name = "Workflow Artifact",
    description = "Read a workflow artifact markdown file",
    mimeType = "text/markdown"
)
public String readArtifact(String flowId, String artifactName) {
    Path file = Path.of(".mahi", "artifacts", flowId, artifactName + ".md");
    if (!Files.exists(file)) {
        throw new IllegalArgumentException(
            "Artifact not found: " + artifactName + " for flow: " + flowId);
    }
    return Files.readString(file);
}
```

**Implémente :** REQ-007

**Contrat de test :**
- Lecture d'un artefact existant → retourne le contenu Markdown brut
- Lecture d'un artefact absent → exception `IllegalArgumentException` (pas de NPE)

---

## DES-008 : Build Gradle Kotlin DSL + fat jar

**Problème :** Migrer de Maven vers Gradle, produire un fat jar exécutable.

**Approche retenue :** Gradle Kotlin DSL (`build.gradle.kts`), plugin `org.springframework.boot` pour `bootJar`.

```kotlin
// build.gradle.kts (extrait)
plugins {
    java
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0"))
    implementation("org.springframework.ai:spring-ai-starter-mcp-server")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.bootJar {
    archiveFileName = "mahi-mcp-server.jar"
}
```

`settings.gradle.kts` : `rootProject.name = "mahi-mcp-server"`.

**Suppression de pom.xml :** le fichier Maven existant est supprimé.

**Implémente :** REQ-008

**Contrat de test :**
- `./gradlew bootJar` → `build/libs/mahi-mcp-server.jar` produit
- `./gradlew test` → tous les tests passent

---

## DES-009 : Tests de régression — WorkflowEngine + WorkflowDefinitions

**Problème :** Garantir que les transitions, guards, historique et invalidations fonctionnent correctement sur chaque workflow.

**Approche retenue :** Tests JUnit 5 + AssertJ. `WorkflowEngineTest` teste le moteur avec un `WorkflowDefinition` de test minimal en mémoire (pas de fichiers). Les tests par définition (`SpecWorkflowDefinitionTest` etc.) utilisent un `WorkflowStore` temporaire (dossier temp JUnit).

Structure des tests :

```java
// WorkflowEngineTest — moteur avec workflow de test en mémoire
@Test void shouldFireValidTransition();
@Test void shouldRejectInvalidTransition();
@Test void shouldFailOnGuardViolation();
@Test void shouldNotPersistOnGuardFailure();
@Test void shouldRecordTransitionInHistory();
@Test void shouldPropagateInvalidation();

// SpecWorkflowDefinitionTest — chemin nominal complet
@Test void shouldCompleteFullSpecWorkflow();
@Test void shouldRejectTransitionFromWrongState();

// AdrWorkflowDefinitionTest
@Test void shouldCompleteFullAdrWorkflow();

// DebugWorkflowDefinitionTest
@Test void shouldCompleteFullDebugWorkflow();

// FindBugWorkflowDefinitionTest
@Test void shouldCompleteFullFindBugWorkflow();
```

**Implémente :** REQ-009

**Contrat de test :** les tests sont leur propre contrat — `./gradlew test` doit produire BUILD SUCCESS.

---

## Couverture des exigences

| Exigence | DES couvrant | Statut |
|----------|-------------|--------|
| REQ-001 — Moteur machine d'état déterministe | DES-002, DES-003 | ✅ |
| REQ-002 — Historique des transitions | DES-003 | ✅ |
| REQ-003 — Gestion des artefacts Markdown | DES-004 | ✅ |
| REQ-004 — Graphe d'invalidation | DES-004 | ✅ |
| REQ-005 — Multi-workflow (spec, ADR, debug, find-bug) | DES-001, DES-002, DES-005 | ✅ |
| REQ-006 — Exposition MCP outils | DES-006 | ✅ |
| REQ-007 — Exposition MCP ressources | DES-007 | ✅ |
| REQ-008 — Build Gradle fat jar | DES-001, DES-008 | ✅ |
| REQ-009 — Tests de régression | DES-009 | ✅ |
| REQ-NF-001 — Démarrage rapide | DES-001 (STDIO, no-web) | ✅ |
| REQ-NF-002 — Persistance atomique | DES-003 | ✅ |
