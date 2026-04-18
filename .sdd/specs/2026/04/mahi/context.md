# Contexte : mahi

> Phase : completed
> Mis à jour : 2026-04-18T12:00:00Z

## Objectif

Implémenter le serveur MCP Java (fat jar Gradle, Java 21, Spring AI MCP Server) pour la machine d'état SDD, exposant des outils et ressources MCP permettant à un LLM de piloter les workflows spec, ADR, debug et find-bug.

## Décisions clés

- DES-001 : Architecture hexagonale légère — séparation moteur / définitions workflow / exposition MCP ; structure `mahi-mcp/` avec packages `core`, `engine`, `definitions`, `store`, `service`, `mcp`
- DES-002 : Types stricts via interfaces marqueurs `WorkflowState`/`WorkflowEvent` implémentées par des enums spécifiques à chaque workflow
- DES-003 : Moteur `WorkflowEngine` garantissant l'ordre guards → actions → changement d'état → persistance ; historique via `List<TransitionRecord>` dans `WorkflowContext`
- DES-004 : Graphe d'invalidation configurable par `WorkflowDefinition` ; propagation récursive des statuts `STALE` sur les artefacts en aval
- DES-005 : Quatre `WorkflowDefinition` autonomes (spec, ADR, debug, find-bug) enregistrées dans `WorkflowRegistry` via `@Bean`
- DES-006 : `WorkflowTools` annoté `@McpTool` exposant 8 outils MCP (`mahi_create_workflow`, `mahi_fire_event`, etc.)
- DES-007 : `ArtifactResources` annoté `@McpResource` — URI pattern `mahi://artifacts/{flowId}/{artifactName}`
- DES-008 : Build Gradle Kotlin DSL, `bootJar` → `mahi-mcp-server.jar`, Java 21, Spring Boot 3.4.4, Spring AI BOM 1.0.0
- DES-009 : Tests JUnit 5 + AssertJ — `WorkflowEngineTest` + 4 tests d'intégration par workflow

## Fichiers identifiés

- `mahi-mcp/build.gradle.kts` — configuration build Gradle Kotlin DSL, fat jar `mahi-mcp-server.jar`
- `mahi-mcp/settings.gradle.kts` — `rootProject.name = "mahi-mcp-server"`
- `mahi-mcp/src/main/java/ia/mahi/MahiMcpApplication.java` — bootstrap Spring Boot
- `mahi-mcp/src/main/java/ia/mahi/workflow/core/` — interfaces marqueurs, `WorkflowContext`, `TransitionRecord`, `WorkflowRegistry`
- `mahi-mcp/src/main/java/ia/mahi/workflow/engine/WorkflowEngine.java` — moteur principal
- `mahi-mcp/src/main/java/ia/mahi/workflow/definitions/` — 4 workflows (spec, adr, debug, findbug)
- `mahi-mcp/src/main/java/ia/mahi/mcp/WorkflowTools.java` — outils MCP `@McpTool`
- `mahi-mcp/src/main/java/ia/mahi/mcp/ArtifactResources.java` — ressources MCP `@McpResource`
- `mahi-mcp/src/main/java/ia/mahi/store/WorkflowStore.java` — persistance JSON `.mahi/flows/`
- `mahi-mcp/src/main/java/ia/mahi/service/ArtifactService.java` — lecture/écriture `.mahi/artifacts/`
- `mahi-mcp/src/main/java/ia/mahi/service/GitWorktreeService.java` — git worktree add/remove
- `mahi-mcp/src/test/java/ia/mahi/` — 22 tests (0 échec)

## Questions ouvertes

- [ ] Push sur le remote en attente : credentials interactifs requis (non bloquant — spec complété, code commité sur main)

## Dernières actions

- Implémentation terminée : 22 tests (0 échec), fat jar produit `mahi-mcp-server.jar` (24 Mo)
- Phase finishing approuvée (option 1 : validation complète)
- Tout commité sur main (push en attente — credentials interactifs)
- Rétrospective terminée : aucune règle candidate à intégrer
- Spec marqué `completed` dans `state.json` et `registry.md`
