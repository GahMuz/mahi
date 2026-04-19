# ADR-001 : mahi — Serveur MCP Java pour la machine d'état SDD

## Statut
Décidé — 2026-04-18

## Contexte

Les workflows SDD (spec, ADR, debug, find-bug...) reposent sur une machine d'état pilotée par un LLM qui interprète les règles depuis du Markdown. Cette approche est non-déterministe : le LLM peut appliquer des transitions illégales silencieusement, perdre le contexte entre sessions et rendre impossible l'audit ou la rejouabilité d'un workflow. L'instabilité s'aggrave à mesure que le nombre de workflows et de règles augmente. Mahi est un serveur MCP Java compilé qui externalise l'exécution de la machine d'état, garantissant des transitions strictement déterministes et auditables.

## Contraintes

- Java 21, Spring Boot 3.4.4, Spring AI MCP Server (STDIO, SYNC)
- Build Gradle, fat jar intégrable dans le plugin SDD
- Dossier source : `mahi-mcp/` à la racine du projet
- Support multi-workflow : spec, ADR, debug, find-bug
- Types stricts (enums) pour états et événements
- Historique des transitions persisté en JSON
- `@McpResource` pour l'exposition des artefacts en lecture
- Tests de régression et vérification d'incompatibilités obligatoires

## Options considérées

| Option | Pour | Contre | Complexité |
|--------|------|--------|------------|
| A — Java/Spring AI fat jar | Déterministe, fat jar, types stricts, Spring AI natif | Runtime JVM requis | MEDIUM |
| B — Python/TypeScript MCP | Démarrage rapide | Pas de fat jar, runtime externe, incohérence techno | LOW |
| C — LLM + Markdown renforcé | Aucun composant supplémentaire | Ne résout pas le non-déterminisme | LOW |

## Décision

**Option retenue : Option A — Serveur MCP Java/Spring AI (fat jar, Gradle)**

Spring AI MCP Server est la seule option compatible avec toutes les contraintes : fat jar Java 21, intégration plugin SDD, et types stricts compilés garantissant le déterminisme. Les options B et C ont été éliminées : B viole la contrainte fat jar Java et introduit une incohérence technologique ; C ne résout pas le problème fondamental de non-déterminisme. L'architecture `WorkflowDefinition` interface + registre de workflows permet d'ajouter facilement de nouveaux workflows (ADR, debug, find-bug) sans modifier le cœur du moteur.

## Conséquences

**Positives :**
- Transitions de phase garanties déterministes et vérifiées à la compilation
- Historique complet des transitions : auditabilité et rejouabilité
- `@McpResource` expose les artefacts (scenario.md, requirements.md, design.md, plan.md) directement au LLM
- Architecture extensible : chaque workflow est une `WorkflowDefinition` indépendante
- Tests unitaires et d'intégration possibles sur la machine d'état (pas de LLM requis pour tester)

**Négatives / Risques :**
- Runtime JVM requis (mitigation : toujours présent dans l'environnement de développement Java)
- Configuration `.mcp.json` à maintenir dans le plugin
- Temps de démarrage JVM au premier appel STDIO (mitigation : acceptable, Spring Boot se lance en < 3s)
- Versionnement du fat jar à synchroniser avec le plugin SDD

## Règles impactées

Aucune — décision conforme aux conventions existantes.

## Prochaines étapes

- [x] ADR-001 décidé
- [ ] `/spec new mahi` — implémenter le serveur MCP Mahi (Gradle, Java 21, fat jar)
- [ ] Implémenter workflows : spec, ADR, debug, find-bug
- [ ] Exposer `@McpResource` pour les artefacts
- [ ] Types stricts (enums) pour états et événements
- [ ] Historique des transitions persisté
- [ ] Tests de régression complets
- [ ] Configurer `.mcp.json` dans le plugin SDD
