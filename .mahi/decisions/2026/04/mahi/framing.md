# Framing : mahi

## Problème

Les LLM pilotant les machines d'état des workflows SDD (spec, ADR, debug, find-bug...) interprètent les règles de transition depuis du Markdown de façon non-déterministe. Aucune garantie que les transitions respectent strictement la machine d'état définie, ce qui fragilise la fiabilité et la reproductibilité des workflows.

## Impact si non traité

- Transitions de phase illégales silencieuses (ex. : passer de DRAFT à IMPLEMENTING sans validation intermédiaire)
- État incohérent entre sessions LLM (contexte perdu, phase mal relue)
- Impossibilité d'auditer ou de rejouer un workflow de façon déterministe
- Instabilité croissante à mesure que le nombre de workflows et de règles augmente
- Confiance réduite dans les outputs du plugin SDD

## Contraintes

- Java 21, Spring Boot 3.4.4, Spring AI MCP Server (STDIO, SYNC)
- Build Gradle (fat jar exécutable, intégrable dans le plugin SDD)
- Dossier source : `mahi-mcp/` à la racine du projet
- Doit supporter plusieurs types de workflows (spec, ADR, debug, find-bug...)
- Doit exposer les outils MCP et les ressources (`@McpResource`) pour les artefacts
- Doit être testé avec couverture de régression et vérification d'incompatibilités
- Types stricts pour les états et événements (pas de String bruts)
- Historique des transitions persisté

## Non-objectifs (hors scope)

- UI graphique ou dashboard web
- Persistence en base de données relationnelle (JSON fichier suffisant)
- Orchestration multi-agents (le serveur MCP est appelé par le LLM, pas l'inverse)
- Exécution des tâches d'implémentation (Mahi orchestre l'état, pas le code)
- Support de workflows non-SDD dans cette première version

## ADRs liés

Aucun

## Specs liées

Aucune (le spec "mahi" sera créé après cet ADR)
