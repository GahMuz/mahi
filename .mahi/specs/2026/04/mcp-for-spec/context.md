# Contexte : mcp-for-spec

> Phase : finishing
> Mis à jour : 2026-04-18T23:00:00Z

## Objectif

Extension du serveur Mahi MCP existant (spec `mahi`, completed) pour gérer les artefacts
de spec de manière structurée : opérations granulaires sur REQ/DES avec critères d'acceptance,
validation de cohérence (propagation STALE fine-grained, couverture des AC), persistance du
contexte de session, et exposition des durées de phase.

## Décisions clés

- **Hiérarchie Artifact (DES-001, Option B retenue)** : `Artifact` abstrait → `FileArtifact` (rétro-compatible) + `StructuredArtifact<T>` → `RequirementsArtifact` / `DesignArtifact`. `ArtifactState` supprimé. `ArtifactDefinition` reçoit une `Supplier<Artifact> factory`. `WorkflowEngine` inchangé. `@JsonIgnore` sur `isValid()` pour éviter sérialisation du champ "valid".
- **Propagation STALE (DES-004, Option B retenue)** : nouvelle méthode `WorkflowService.propagateRequirementStale` — logique dans WorkflowService, moteur FSM intact. Les IDs propagés sont retournés dans `metadata.stalePropagated`.
- **Guard cohérence (DES-005)** : guard dans `SpecWorkflowDefinition` sur la transition `REQUIREMENTS_DEFINED::DEFINE_DESIGN` — appelle `checkCoherence` et lève exception si violations. WorkflowService injecté dans SpecWorkflowDefinition.
- **SessionContext** : champ `sessionContext` dans `WorkflowContext` avec `@JsonInclude(NON_NULL)` — absent si null (REQ-005.AC-4). Fichier `context.md` humainement lisible écrit dans `metadata.specPath`.
- **PhaseDurations** : calcul à la volée depuis `TransitionRecord.history` dans `WorkflowService.get()`, placé dans `metadata.phaseDurations`. `WorkflowDefinition.getStateToPhaseMapping()` default = `Map.of()`.
- **Convention nommage** : test par réflexion `WorkflowToolsNamingTest` vérifie pattern `^mahi_[a-z]+(_[a-z]+)*$`.

## Fichiers identifiés

- `mahi-mcp/src/main/java/ia/mahi/workflow/core/` — nouvelles classes : `Artifact`, `FileArtifact`, `StructuredArtifact`, `RequirementsArtifact`, `DesignArtifact`, `RequirementItem`, `DesignItem`, `ItemStatus`, `AcceptanceCriterion`, `SessionContext`, `RequirementSummary`, `DesignSummary`, `CoherenceViolation`
- `mahi-mcp/src/main/java/ia/mahi/engine/WorkflowService.java` — 10+ méthodes ajoutées
- `mahi-mcp/src/main/java/ia/mahi/mcp/WorkflowTools.java` — 10 nouveaux outils MCP annotés `@Tool`
- `mahi-mcp/src/main/java/ia/mahi/workflow/spec/SpecWorkflowDefinition.java` — factory Artifact, override `getStateToPhaseMapping()`, guard cohérence
- `mahi-mcp/src/main/java/ia/mahi/workflow/core/WorkflowContext.java` — migration `ArtifactState` → `Artifact`, ajout `sessionContext`
- `mahi-mcp/src/main/java/ia/mahi/workflow/core/ArtifactDefinition.java` — ajout factory `Supplier<Artifact>`
- `mahi-mcp/src/main/java/ia/mahi/workflow/core/WorkflowEngine.java` — adaptation `Artifact`
- `mahi-mcp/src/test/java/ia/mahi/` — 6+ nouvelles classes de test (ArtifactSerializationTest, WorkflowServiceRequirementTest, WorkflowServiceDesignTest, StalePropagationTest, CoherenceCheckTest, SessionContextTest, PhaseDurationsTest, WorkflowToolsNamingTest)
- `.sdd/specs/2026/04/mcp-for-spec/` — requirement.md, design.md, plan.md

## Questions ouvertes

- Aucune — implémentation complète, 55/55 tests passent, phase finishing complétée

## Dernières actions

- Implémentation complète en 6 lots : TASK-001 à TASK-008 — 18 sous-tâches toutes validées [x]
- 55 tests passent (22 baseline + 33 nouveaux)
- Phase finishing complétée : build propre, pas de changements non commités
- Phase retrospective démarrée (state.json mis à jour)
- Spec fermée pour ouverture de sdd-spec-worktree-mcp
