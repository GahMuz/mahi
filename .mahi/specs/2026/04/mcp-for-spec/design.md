# Design : mcp-for-spec — Extension du serveur Mahi MCP

## Contexte

Specs liés chargés comme contexte : **mahi** (completed), **mahi-workflow** (completed).

Le serveur Mahi MCP expose actuellement des outils coarse-grained (`mahi_write_artifact` stocke un
Markdown brut, `mahi_add_requirement_info` / `mahi_add_design_info` enrichissent des métadonnées
libres). Ce spec étend la couche `WorkflowTools` + `WorkflowService` avec :
- Des entités structurées REQ / DES / TASK avec propagation STALE fine-grained
- Un outil de vérification de cohérence (`mahi_check_coherence`)
- La persistance du contexte de session dans `WorkflowContext`
- Le calcul de métriques de durée dérivé des `TransitionRecord` existants

**Principe directeur :** le moteur FSM (`WorkflowEngine`) n'est pas modifié. Les classes du core
(`Artifact`, `ArtifactDefinition`, `WorkflowContext`, `WorkflowDefinition`) sont étendues via héritage
pour accueillir le contenu structuré des artifacts spec, sans altérer la logique de transition.

---

## DES-001 : Modèle de données — hiérarchie Artifact avec contenu structuré

**Problème :**
`WorkflowContext` stocke les artefacts comme `Map<String, ArtifactState>` où `ArtifactState` ne contient
que le cycle de vie (MISSING/DRAFT/VALID/STALE) et le chemin fichier. Les nouvelles entités structurées
(REQ, DES) ont un contenu riche (critères d'acceptation, liens croisés) qui doit coexister avec leur
cycle de vie. D'autres workflows (ADR) auront le même besoin. Il faut un modèle extensible qui unifie
cycle de vie et contenu sans toucher au moteur FSM.

**Options considérées :**

Option A — Champs supplémentaires dans `WorkflowContext` (`Map<String, RequirementItem>`, etc.)
- Inconvénients : cycle de vie et contenu séparés dans deux maps parallèles — incohérence conceptuelle,
  duplication à chaque nouveau workflow structuré.

**Option B retenue — Hiérarchie `Artifact` : cycle de vie + contenu dans le même objet**
- Avantages :
  - Un artifact est UNE entité : son cycle de vie + son contenu
  - `StructuredArtifact<T>` est réutilisable pour ADR (options), debug, etc.
  - Le moteur FSM opère sur `Artifact` (base) — aucune modification nécessaire
  - Pas de maps parallèles dans `WorkflowContext`

**Hiérarchie :**

```
Artifact (abstract)                    ← remplace ArtifactState, mêmes méthodes lifecycle
  ├── FileArtifact                     ← artifact fichier simple (plan.md, worktree, etc.)
  └── StructuredArtifact<T> (abstract) ← artifact avec contenu indexé par ID
        ├── RequirementsArtifact       ← items: Map<String, RequirementItem>
        └── DesignArtifact             ← items: Map<String, DesignItem>
```

**Classe `Artifact` (abstract) dans `ia.mahi.workflow.core` :**

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "artifactType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FileArtifact.class,         name = "file"),
    @JsonSubTypes.Type(value = RequirementsArtifact.class, name = "requirements"),
    @JsonSubTypes.Type(value = DesignArtifact.class,       name = "design")
})
public abstract class Artifact {
    private String name;
    private ArtifactStatus status;   // MISSING | DRAFT | VALID | STALE
    private int version;
    private String path;             // chemin du fichier backing (requirements.md, design.md, etc.)
    private Instant updatedAt;

    // Mêmes méthodes que l'actuel ArtifactState
    public void markDraft(String path) { ... }
    public void markValid() { ... }
    public void markStale() { ... }
    public boolean isValid() { ... }
}
```

**`FileArtifact extends Artifact`** — aucun champ supplémentaire, équivalent fonctionnel de l'actuel `ArtifactState`.

**`StructuredArtifact<T> extends Artifact` (abstract) :**

```java
public abstract class StructuredArtifact<T> extends Artifact {
    private Map<String, T> items = new LinkedHashMap<>();  // "REQ-001" → RequirementItem

    public Map<String, T> getItems() { return items; }
    public void setItems(Map<String, T> items) { this.items = items; }
}
```

**`RequirementsArtifact extends StructuredArtifact<RequirementItem>`** et
**`DesignArtifact extends StructuredArtifact<DesignItem>`** — aucun champ supplémentaire.

**`WorkflowContext` :**

```java
// Avant : Map<String, ArtifactState> artifacts
// Après :
private Map<String, Artifact> artifacts = new HashMap<>();  // polymorphique

// Inchangé
private SessionContext sessionContext;  // voir DES-006
```

**`ArtifactDefinition` :** le champ `name` est complété d'une factory pour instancier le bon type :

```java
public record ArtifactDefinition(String name, Supplier<Artifact> factory) {
    public static ArtifactDefinition file(String name) {
        return new ArtifactDefinition(name, () -> new FileArtifact(name));
    }
}
```

Le moteur utilise alors `v.factory().get()` au lieu de `new ArtifactState(k)` — seul changement dans `WorkflowEngine.create()`.

**Déclaration dans `SpecWorkflowDefinition` :**

```java
@Override
public Map<String, ArtifactDefinition> getArtifacts() {
    return Map.of(
        "requirements", new ArtifactDefinition("requirements", () -> new RequirementsArtifact("requirements")),
        "design",       new ArtifactDefinition("design",       () -> new DesignArtifact("design")),
        "plan",         ArtifactDefinition.file("plan")
    );
}
```

Les workflows non-spec (`adr`, `debug`, `findbug`) utilisent uniquement `ArtifactDefinition.file(...)` — aucune régression.

**Nouvelles classes item dans `ia.mahi.workflow.core` :**

`RequirementItem` :
```java
public class RequirementItem {
    private String id;            // "REQ-001"
    private String title;
    private String priority;      // "must" | "should" | "could"
    private ItemStatus status;    // VALID | STALE | MISSING
    private List<AcceptanceCriterion> acceptanceCriteria;
    private String content;       // Markdown libre — stocké tel quel, non parsé
}
```

`AcceptanceCriterion` :
```java
public record AcceptanceCriterion(String id, String description) {}
// id exemple : "REQ-001.AC-1"
```

`DesignItem` :
```java
public class DesignItem {
    private String id;                    // "DES-001"
    private String title;
    private ItemStatus status;            // VALID | STALE | MISSING
    private List<String> coversAC;        // ["REQ-001.AC-1", "REQ-002.AC-3"]
    private List<String> implementedBy;   // ["TASK-001"] — enrichi à la phase planning
    private String content;
}
```

`ItemStatus` :
```java
public enum ItemStatus { VALID, STALE, MISSING }
```

`SessionContext` :
```java
public class SessionContext {
    private Instant savedAt;
    private String lastAction;
    private List<String> keyDecisions;
    private List<String> openQuestions;
    private String nextStep;
}
```

**Justification SOLID :**
- **S** : `Artifact` = cycle de vie, `StructuredArtifact` = cycle de vie + contenu — responsabilités bien séparées
- **O** : ajout d'un nouveau workflow structuré = nouvelle sous-classe + `ArtifactDefinition` factory — aucune modif du moteur
- **L** : `WorkflowEngine` manipule `Artifact` (base) — tout `FileArtifact` ou `StructuredArtifact` est substituable
- **D** : `WorkflowContext` ne dépend d'aucun service — reste un POJO sérialisable

**Implémente :** REQ-001, REQ-002, REQ-005, REQ-006

**Contrat de test :**
- `WorkflowContext` avec `RequirementsArtifact` sérialisé/désérialisé : `artifactType = "requirements"`, items présents
- `WorkflowContext` avec `FileArtifact` sérialisé/désérialisé : `artifactType = "file"`, rétro-compatible
- `WorkflowContext` sans `sessionContext` dans JSON : `getSessionContext()` retourne `null` (REQ-005.AC-4)
- Workflow non-spec (`adr`) : `context.getArtifacts()` ne contient que des `FileArtifact`

---

## DES-002 : Opérations granulaires sur les exigences — nouveaux outils MCP

**Problème :**
Exposer quatre outils MCP pour manipuler les `RequirementItem` individuellement, en respectant le
pattern `@Tool` / `@ToolParam` déjà en place dans `WorkflowTools`.

**Approche retenue :** Suivre exactement le pattern `WorkflowTools` existant — quatre méthodes
publiques annotées `@Tool` dans `WorkflowTools`, déléguant à `WorkflowService`.

**Signatures dans `WorkflowTools` :**

```java
@Tool(name = "mahi_add_requirement", description = "Add a structured requirement to a spec workflow. ...")
public WorkflowContext addRequirement(
    @ToolParam(required = true) String flowId,
    @ToolParam(required = true) RequirementItem req);

@Tool(name = "mahi_update_requirement", description = "Update an existing requirement and propagate STALE ...")
public WorkflowContext updateRequirement(
    @ToolParam(required = true) String flowId,
    @ToolParam(required = true) String reqId,
    @ToolParam(required = true) RequirementItem req);

@Tool(name = "mahi_list_requirements", description = "List all requirements with IDs, titles and statuses ...")
public List<RequirementSummary> listRequirements(
    @ToolParam(required = true) String flowId);

@Tool(name = "mahi_get_requirement", description = "Get a complete requirement including content and ACs.")
public RequirementItem getRequirement(
    @ToolParam(required = true) String flowId,
    @ToolParam(required = true) String reqId);
```

**`RequirementSummary`** (projection légère pour `mahi_list_requirements`) :
```java
public record RequirementSummary(String id, String title, ItemStatus status) {}
```

**Délégation dans `WorkflowService` :**
Quatre méthodes correspondantes — la logique de validation (ID unique, propagation STALE) est dans
`WorkflowService`, pas dans `WorkflowTools`. L'accès aux items se fait via :
```java
RequirementsArtifact reqs = (RequirementsArtifact) context.getArtifacts().get("requirements");
reqs.getItems().put(req.getId(), req);
```

**Justification SOLID :**
- **S** : `WorkflowTools` orchestre, `WorkflowService` implémente la logique métier
- **I** : `mahi_list_requirements` retourne `RequirementSummary` (pas `RequirementItem`) — REQ-001.AC-3
- **D** : `WorkflowTools` ne dépend que de `WorkflowService` (interface de service)

**Implémente :** REQ-001

**Contrat de test :**
- `mahi_add_requirement` avec ID existant → `IllegalArgumentException("Requirement already exists: REQ-001")`
- `mahi_list_requirements` → retourne IDs, titres, statuts uniquement (pas `content` ni `acceptanceCriteria`)
- `mahi_get_requirement` avec ID inexistant → `IllegalArgumentException("Requirement not found: REQ-999")`
- `mahi_add_requirement` avec IDs d'AC au format incorrect → `IllegalArgumentException` (validation format `<reqId>.AC-<n>`)

---

## DES-003 : Opérations granulaires sur les éléments de design — nouveaux outils MCP

**Problème :**
Exposer quatre outils MCP pour manipuler les `DesignItem`, avec validation que les `coversAC`
référencent des AC existantes.

**Approche retenue :** Même pattern que DES-002.

**Signatures dans `WorkflowTools` :**

```java
@Tool(name = "mahi_add_design_element", description = "Add a structured design element. Requires at least one coversAC ...")
public WorkflowContext addDesignElement(
    @ToolParam(required = true) String flowId,
    @ToolParam(required = true) DesignItem des);

@Tool(name = "mahi_update_design_element", description = "Update a design element and propagate STALE to dependent tasks ...")
public WorkflowContext updateDesignElement(
    @ToolParam(required = true) String flowId,
    @ToolParam(required = true) String desId,
    @ToolParam(required = true) DesignItem des);

@Tool(name = "mahi_list_design_elements", description = "List design elements with IDs, titles, statuses and coversAC ...")
public List<DesignSummary> listDesignElements(
    @ToolParam(required = true) String flowId);

@Tool(name = "mahi_get_design_element", description = "Get a complete design element including content.")
public DesignItem getDesignElement(
    @ToolParam(required = true) String flowId,
    @ToolParam(required = true) String desId);
```

**`DesignSummary`** (projection pour `mahi_list_design_elements`) :
```java
public record DesignSummary(String id, String title, ItemStatus status, List<String> coversAC) {}
```

**Validation dans `WorkflowService.addDesignElement` :**
- Au moins un `coversAC` fourni (REQ-002.AC-1)
- Chaque AC référencé existe dans `((RequirementsArtifact) context.getArtifacts().get("requirements")).getItems().get(reqId).getAcceptanceCriteria()` (REQ-004.AC-1 second cas)
- Accès au `DesignArtifact` : `(DesignArtifact) context.getArtifacts().get("design")`

**Justification SOLID :**
- **O** : `DesignSummary` est une projection dédiée — `DesignItem` n'est pas modifié pour réduire la sortie
- **S** : la validation des `coversAC` est dans `WorkflowService`, pas dans `WorkflowTools`

**Implémente :** REQ-002

**Contrat de test :**
- `mahi_add_design_element` sans `coversAC` → `IllegalArgumentException("At least one coversAC required")`
- `mahi_add_design_element` avec AC inexistant → `IllegalArgumentException("AC not found: REQ-001.AC-9")`
- `mahi_list_design_elements` → retourne IDs, titres, statuts, `coversAC` — pas `content`
- `mahi_update_design_element` → retourne `WorkflowContext` avec `stalePropagated` dans métadonnées (voir DES-004)

---

## DES-004 : Propagation STALE fine-grained entre entités REQ → DES → TASK

**Problème :**
La propagation STALE existante (`WorkflowEngine.propagateInvalidation`) opère sur les artefacts
Markdown coarse-grained (`requirements`, `design`, `plan`). Les nouvelles entités structurées
ont une granularité plus fine : modifier REQ-001 doit invalider uniquement les DES qui couvrent
les AC de REQ-001, pas tous les DES.

**Options considérées :**

Option A — Réutiliser `WorkflowEngine.propagateInvalidation`
- Avantages : code existant réutilisé
- Inconvénients :
  - `propagateInvalidation` opère sur les noms d'artefacts (strings statiques) — pas sur des IDs dynamiques
  - Le graphe d'invalidation est statique dans `WorkflowDefinition` — il ne peut pas représenter les liens REQ→DES dynamiques

**Option B retenue — Nouvelle méthode `WorkflowService.propagateRequirementStale(flowId, reqId)`**
- Avantages :
  - Logique isolée dans `WorkflowService` — le moteur FSM n'est pas touché
  - Calcul dynamique basé sur les liens `coversAC` dans `WorkflowContext`
  - Retourne `stalePropagated` pour la réponse outil (REQ-003.AC-3)
- Inconvénients : code nouveau — mais logique simple et bien délimitée

**Algorithme de propagation REQ → DES :**
```
DesignArtifact designArtifact = (DesignArtifact) context.getArtifacts().get("design")
Pour chaque DES dans designArtifact.getItems().values() :
    Si DES.coversAC contient au moins un AC commençant par reqId + "." :
        Si DES.status != MISSING et DES.status != STALE :
            DES.status = STALE
            Ajouter DES.id à stalePropagated
```

**Algorithme de propagation DES → TASK :**
Les TASK sont stockés dans l'artefact Markdown `plan` (pas dans une map structurée à ce stade).
Pour ce spec, `implementedBy` dans `DesignItem` stocke les IDs de TASK. La propagation DES→TASK
marque `DesignItem.status = STALE` uniquement — les TASK dans `plan.md` sont invalidés via
l'artefact coarse-grained `plan` (markStale sur `ArtifactState`), conformément à l'invalidation existante.

**Réponse enrichie :** les méthodes `updateRequirement` et `updateDesignElement` retournent un
`WorkflowContext` dans lequel `metadata.stalePropagated` est une `List<String>` des IDs passés STALE
(REQ-003.AC-3). Ce champ est réinitialisé à chaque appel (pas cumulatif).

**Justification SOLID :**
- **S** : `WorkflowService` possède la logique de propagation, `WorkflowEngine` garde le moteur FSM pur
- **O** : ajout d'une méthode dans `WorkflowService` sans modifier le moteur

**Implémente :** REQ-003

**Dépend de :** DES-001, DES-002, DES-003

**Contrat de test :**
- `mahi_update_requirement("REQ-001")` → DES qui ont un AC `REQ-001.AC-*` passent STALE
- DES sans lien avec REQ-001 restent inchangés (REQ-003.AC-2)
- DES déjà STALE → non inclus dans `stalePropagated` (REQ-003.AC-4)
- `stalePropagated` dans la réponse `metadata` contient exactement les IDs invalidés (REQ-003.AC-3)
- Propagation synchrone avant retour de l'outil (REQ-003.AC-1) — garantie par nature séquentielle Java

---

## DES-005 : Vérification de cohérence — `mahi_check_coherence`

**Problème :**
Exposer un outil qui détecte quatre types de violations de cohérence entre REQ et DES, sans
nouvelle structure de données (toutes les informations sont déjà dans `WorkflowContext`).

**Approche retenue :** Outil en lecture seule — pas de modification du contexte. Retourne une
liste de `CoherenceViolation`.

**Nouvelle classe `CoherenceViolation` :**
```java
public record CoherenceViolation(
    String type,      // "AC_ORPHAN" | "DES_NO_AC" | "REQ_NO_AC" | "AC_NOT_FOUND"
    String itemId,    // ID de l'élément concerné
    String message    // Message lisible en français
) {}
```

**Signature dans `WorkflowTools` :**
```java
@Tool(name = "mahi_check_coherence",
      description = "Check coherence of requirements and design elements. Returns all violations.")
public List<CoherenceViolation> checkCoherence(
    @ToolParam(required = true) String flowId);
```

**Algorithme dans `WorkflowService.checkCoherence(flowId)` :**

```
violations = []

// REQ sans AC
Pour chaque REQ : si REQ.acceptanceCriteria est vide → violation("REQ_NO_AC", REQ.id, "...")

// DES sans AC
Pour chaque DES : si DES.coversAC est vide → violation("DES_NO_AC", DES.id, "...")

// AC inexistante référencée par un DES
RequirementsArtifact reqs = (RequirementsArtifact) context.getArtifacts().get("requirements")
DesignArtifact des = (DesignArtifact) context.getArtifacts().get("design")
Pour chaque DES, pour chaque acId dans DES.coversAC :
    Parser acId → extraire reqId (partie avant ".AC-")
    Si reqs.getItems().get(reqId) absent OU acId absent dans ses acceptanceCriteria :
        violation("AC_NOT_FOUND", DES.id, "...")

// AC orpheline (couverte par aucun DES)
Construire acsCouvertes = union de tous les DES.coversAC de des.getItems().values()
Pour chaque REQ, pour chaque AC dans REQ.acceptanceCriteria :
    Si AC.id ∉ acsCouvertes → violation("AC_ORPHAN", AC.id, "...")
```

**Intégration avec `mahi_fire_event(flowId, "approve")` en phase design (REQ-004.AC-3) :**
Cette contrainte est implémentée via un `Guard` dans `SpecWorkflowDefinition` sur la transition
`REQUIREMENTS_DEFINED::DEFINE_DESIGN`. Le guard appelle `checkCoherence` et lève une exception si
des violations existent.

**Option considérée :** appel automatique depuis `WorkflowEngine.fire()` pour toute transition.
**Rejetée :** trop intrusif — le moteur deviendrait dépendant d'une logique métier spécifique au workflow spec.
**Approche retenue :** Guard dans `SpecWorkflowDefinition` — cohérent avec le pattern existant des guards
de validation d'artefacts (`requireValid("requirements")`).

**Justification SOLID :**
- **S** : `checkCoherence` est en lecture seule, séparé des mutations
- **O** : le guard est dans `SpecWorkflowDefinition` — le moteur n'est pas modifié
- **D** : le guard reçoit `WorkflowService` par injection dans `SpecWorkflowDefinition`

**Implémente :** REQ-004

**Dépend de :** DES-001, DES-002, DES-003

**Contrat de test :**
- Aucune violation → retourne `[]` (REQ-004.AC-1)
- REQ sans AC → violation type `"REQ_NO_AC"` avec message en français (REQ-004.AC-2)
- DES sans AC → violation type `"DES_NO_AC"`
- AC inexistante dans DES.coversAC → violation type `"AC_NOT_FOUND"`
- AC orpheline → violation type `"AC_ORPHAN"`
- `mahi_fire_event(flowId, "approve")` avec violations présentes → `IllegalStateException` avec message

---

## DES-006 : Persistance du contexte de session — `mahi_save_context`

**Problème :**
Persister le contexte de session (`lastAction`, `keyDecisions`, `openQuestions`, `nextStep`) dans
`WorkflowContext` ET dans un fichier `.sdd/specs/YYYY/MM/<id>/context.md` lisible par un humain.
La réponse de `mahi_get_workflow` doit inclure ce contexte si présent.

**Approche retenue :**
Le `SessionContext` est stocké dans le champ `sessionContext` de `WorkflowContext` (DES-001) —
sérialisé dans `.mahi/flows/<flowId>.json` comme les autres données. En parallèle, `WorkflowService`
écrit un fichier Markdown humainement lisible.

**Signature dans `WorkflowTools` :**
```java
@Tool(name = "mahi_save_context",
      description = "Persist session context for a workflow. Overwrites any previous context.")
public WorkflowContext saveContext(
    @ToolParam(required = true) String flowId,
    @ToolParam(required = true) SessionContext context);
```

**Dans `WorkflowService.saveContext(flowId, ctx)` :**
1. Charger `WorkflowContext` via `store.load(flowId)`
2. Affecter `workflowContext.setSessionContext(ctx)` avec `ctx.setSavedAt(Instant.now())`
3. Écrire le fichier Markdown via `ArtifactService` dans le chemin `.sdd/specs/<path>/context.md`
   (le chemin spec est obtenu depuis `metadata.specPath` — enrichi lors de `/spec new`)
4. `store.save(workflowContext)` — persistance atomique

**Comportement de `mahi_get_workflow` :**
`workflowService.get(flowId)` retourne le `WorkflowContext` complet — Jackson sérialise `sessionContext`
si non null. Si null, le champ est absent de la réponse JSON (`@JsonInclude(NON_NULL)` sur le champ).
Cela satisfait REQ-005.AC-4 sans logique supplémentaire.

**Chemin du fichier `context.md` :**
Le skill `/spec new` stockera le chemin relatif du spec dans `metadata.specPath` lors de la création
du workflow (`mahi_create_workflow`). `WorkflowService` utilise ce chemin pour construire le path
du fichier `context.md`.

**Justification SOLID :**
- **S** : `ArtifactService` écrit les fichiers, `WorkflowService` orchestre la logique
- **O** : `WorkflowContext` reçoit un champ optionnel — aucune interface existante modifiée

**Implémente :** REQ-005

**Dépend de :** DES-001

**Contrat de test :**
- `mahi_save_context` → `mahi_get_workflow` retourne `sessionContext` avec tous les champs (REQ-005.AC-2)
- Double appel `mahi_save_context` → le second écrase le premier (REQ-005.AC-1)
- `mahi_get_workflow` sans contexte sauvegardé → `sessionContext` absent du JSON (REQ-005.AC-4)
- Fichier `context.md` créé dans le chemin spec (REQ-005.AC-3)

---

## DES-007 : Métriques de durée par phase — calcul depuis TransitionRecord

**Problème :**
Calculer les durées de phase à partir des `TransitionRecord` existants dans `WorkflowContext.history`,
sans nouveau tool call ni nouveau champ de stockage. La valeur calculée est retournée dans la réponse
de `mahi_get_workflow`.

**Approche retenue :**
Calcul à la volée dans `WorkflowService.get(flowId)` — après chargement du contexte, enrichissement
du champ `phaseDurations` dans `metadata` avant retour. Pas de persistance du calcul (toujours frais).

**Algorithme dans `WorkflowService` :**

```
phaseDurations = LinkedHashMap<String, Long>  // phase → durée en ms

Pour chaque TransitionRecord tr dans history (ordre chronologique) :
    // Fin d'une phase = transition sortante de l'état qui représente cette phase
    // Le mapping "état FSM → nom de phase" est défini dans SpecWorkflowDefinition
    phaseQuittée = stateToPhase(tr.fromState())
    phaseEntrée  = stateToPhase(tr.toState())

    Si phaseQuittée != null :
        duréeAjoutée = tr.occurredAt() - débutPhase[phaseQuittée]
        phaseDurations[phaseQuittée] += duréeAjoutée  // additionne si plusieurs passages (REQ-006.AC-4)

    Si phaseEntrée != null :
        débutPhase[phaseEntrée] = tr.occurredAt()

// Phase en cours (durée partielle, REQ-006.AC-2)
phaseEnCours = stateToPhase(context.state)
Si phaseEnCours != null et débutPhase[phaseEnCours] != null :
    phaseDurations[phaseEnCours] = now() - débutPhase[phaseEnCours]
```

**Mapping `stateToPhase` dans `SpecWorkflowDefinition` :**
```
REQUIREMENTS_DEFINED → "requirements"
DESIGN_DEFINED       → "design"
IMPLEMENTATION_PLAN_DEFINED → "planning"
IMPLEMENTING         → "implementation"
FINALIZING           → "finishing"
RETROSPECTIVE_DONE   → "retrospective"
```
(Autres états FSM → `null` — états transitoires sans phase nommée.)

Ce mapping est exposé via une nouvelle méthode `Map<String, String> getStateToPhaseMapping()` dans
`WorkflowDefinition` (valeur par défaut = `Map.of()` pour les workflows non-spec).

**Justification SOLID :**
- **O** : `WorkflowDefinition` reçoit une méthode default — les implémentations existantes ne changent pas
- **S** : le calcul est dans `WorkflowService.get()`, pas dans `WorkflowEngine` ni dans `WorkflowTools`

**Implémente :** REQ-006

**Dépend de :** DES-001

**Contrat de test :**
- Workflow spec avec 2 transitions (requirements → design après 47 min) → `phaseDurations.requirements = 2820000`
- Phase en cours → durée partielle depuis l'entrée (REQ-006.AC-2)
- Phase non encore atteinte → absente du map (REQ-006.AC-3)
- Re-entrée dans une phase (REANALYZING → DEFINE_REQUIREMENTS) → durées additionnées (REQ-006.AC-4)
- Workflow non-spec (`adr`) → `phaseDurations` vide (mapping vide)

---

## DES-008 : Convention de nommage — validation à l'enregistrement des outils

**Problème :**
REQ-NF-001 impose le pattern `mahi_<verbe>_<entité>` en snake_case pour tous les nouveaux outils.
Cette convention est vérifiable structurellement mais il n'y a pas de mécanisme automatique en place.

**Approche retenue :**
La convention est documentée et respectée par construction dans `WorkflowTools`. Un test unitaire
`WorkflowToolsNamingTest` vérifie par réflexion que tous les `@Tool(name=...)` respectent le pattern
`^mahi_[a-z]+(_[a-z]+)*$`.

**Nouveaux outils conformes :**
- `mahi_add_requirement` ✅
- `mahi_update_requirement` ✅
- `mahi_list_requirements` ✅
- `mahi_get_requirement` ✅
- `mahi_add_design_element` ✅
- `mahi_update_design_element` ✅
- `mahi_list_design_elements` ✅
- `mahi_get_design_element` ✅
- `mahi_check_coherence` ✅
- `mahi_save_context` ✅

**Implémente :** REQ-NF-001

**Contrat de test :**
- `WorkflowToolsNamingTest` : tous les `@Tool` de `WorkflowTools` matchent `^mahi_[a-z]+(_[a-z]+)*$`

---

## Couverture des exigences

| Exigence | DES couvrant | Statut |
|----------|-------------|--------|
| REQ-001 — Opérations granulaires sur les exigences | DES-001, DES-002 | ✅ |
| REQ-002 — Opérations granulaires sur les éléments de design | DES-001, DES-003 | ✅ |
| REQ-003 — Cohérence — propagation STALE automatique | DES-004 | ✅ |
| REQ-004 — Cohérence — vérification de présence et couverture des AC | DES-005 | ✅ |
| REQ-005 — Contexte de session persisté | DES-001, DES-006 | ✅ |
| REQ-006 — Métriques de durée par phase | DES-001, DES-007 | ✅ |
| REQ-NF-001 — Convention de nommage des outils MCP | DES-008 | ✅ |
