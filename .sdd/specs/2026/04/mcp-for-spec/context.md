# Contexte : mcp-for-spec

> Phase : requirements
> Mis à jour : 2026-04-18

## Objectif

Extension du serveur Mahi MCP existant (spec `mahi`, completed) pour gérer les artefacts
de spec de manière structurée (opérations granulaires sur REQ/DES/TASK avec critères d'acceptance),
valider la cohérence (propagation STALE, couverture des AC), persister le contexte de session,
et exposer les durées de phase.

## Décisions clés

- **Option B retenue** : évolution du serveur Mahi existant (pas un nouveau serveur)
- **Opérations granulaires** : objets structurés avec métadonnées (id, priority, status, coversAC) + contenu Markdown libre — le serveur ne parse pas le contenu
- **Critères d'acceptance structurés** : chaque REQ contient ses AC avec IDs (`REQ-001.AC-1`) ; chaque DES référence les AC qu'il couvre (`coversAC`) — un DES peut couvrir des AC de plusieurs REQ différents
- **Cohérence** : niveau 1 (présence/couverture AC) + niveau 2 (propagation STALE automatique)
- **Contexte de session** : `mahi_save_context` à la clôture, retourné dans `mahi_get_workflow` à l'ouverture
- **Métriques** : durées de phase uniquement, calculées automatiquement depuis les timestamps des TransitionRecord existants — pas de comptage de tokens

## Fichiers identifiés

- `.sdd/specs/2026/04/mcp-for-spec/requirement.md` — 6 exigences fonctionnelles + 1 NF (REQ-001 à REQ-006 + REQ-NF-001)
- `mahi-mcp/src/main/java/ia/mahi/` — serveur Mahi existant à étendre

## Questions ouvertes

- Aucune — requirements validés et complets

## Dernières actions

- 4 axes clarifiés en session : opérations granulaires (B), cohérence, contexte, métriques
- requirement.md rédigé avec 6 REQ fonctionnels et REQ-NF-001
- Phase requirements complète — prête pour `/spec approve`