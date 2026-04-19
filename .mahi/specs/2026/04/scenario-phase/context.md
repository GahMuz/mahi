# Contexte : scenario-phase

> Phase : requirements
> Mis à jour : 2026-04-19T12:00:00Z

## Objectif

Ajouter une phase `scenario` légère commune à tous les workflows Mahi (spec, ADR, bug-finding, etc.) qui capture l'intention initiale dans un artefact `scenario.md` géré par le MCP, et sert de routeur quand le type n'est pas encore connu.

## Décisions clés

- Phase `SCENARIO` comme état initial universel : tous les workflows démarrent en `SCENARIO`, pas en `REQUIREMENTS`/`ANALYSIS`
- `scenario.md` immuable après `APPROVE_SCENARIO` : géré exclusivement via MCP (pas d'écriture directe LLM)
- `/scenario new` sans type prédéfini : dialogue → proposition de type → confirmation → création du workflow typé
- Compatibilité ascendante : `/spec new` et `/adr new` continuent de fonctionner, passent simplement par la phase scénario d'abord
- Outil MCP dédié `mahi_append_scenario(workflowId, role, content)` avec vérification de phase avant append

## Fichiers identifiés

- `.sdd/specs/2026/04/scenario-phase/requirement.md` — 6 REQ avec AC complets, phase requirements terminée
- `.sdd/specs/2026/04/scenario-phase/log.md` — journal de création
- Côté serveur Mahi : machine d'état à modifier pour ajouter l'état `SCENARIO` en tête de chaque type de workflow
- Côté plugin : `skills/spec/references/phase-requirements.md` et équivalents ADR à adapter pour démarrer après la phase scénario

## Questions ouvertes

- [ ] Le serveur Mahi doit-il exposer un outil `mahi_create_scenario_workflow` distinct ou réutiliser `mahi_create_workflow` avec un flag ?
- [ ] Format exact de `scenario.md` (normalisé pour traitement automatisé futur — REQ-005-AC-2) à définir dans le design
- [ ] Mécanisme de "rejeu" d'un scénario existant (REQ-005-AC-3) : passage en paramètre à `mahi_create_workflow` ou commande `/scenario replay <id>` ?

## Dernières actions

- Spec créée, intention initiale documentée dans `log.md`
- Phase requirements complétée : 6 REQ rédigés (REQ-001 à REQ-006) avec acceptance criteria
- Aucun `design.md` encore — approbation requirements non effectuée
