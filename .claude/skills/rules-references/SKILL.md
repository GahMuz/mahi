---
name: Rules References
description: This skill should be used when checking "project rules", "coding conventions", "project standards", "règles projet", or when validating design, implementation, or code review against project-specific constraints. Actively enforced during planning, implementation, and review phases.
---

# Règles et conventions du projet

Ce skill est activement vérifié à chaque étape du workflow spec-driven :
- **Planification** : les règles sont intégrées dans le plan
- **Implémentation** : les agents ne chargent que les fichiers pertinents pour la sous-tâche
- **Revue de code** : le réviseur vérifie chaque règle, violations = CRITIQUE

## Index des références (index vivant — maintenir à jour)

Cet index permet le chargement paresseux : les agents lisent cette liste pour déterminer quel fichier charger selon le contexte de la sous-tâche.

| Fichier | Domaine | Charger quand |
|---------|---------|---------------|
| `references/rules.md` | Transversal | Toujours (règles de base) |

Cet index s'enrichit automatiquement via la rétrospective à la fin de chaque spec.
Exemples de fichiers ajoutés au fil du temps :
- `rules-controller.md` — Règles contrôleurs/routes → charger pour sous-tâches contrôleur
- `rules-service.md` — Règles services → charger pour sous-tâches service
- `rules-entity.md` — Règles entités/modèles → charger pour sous-tâches entité
- `rules-test.md` — Règles tests → charger pour sous-tâches test
- `rules-security.md` — Règles sécurité → charger pour sous-tâches auth/sécurité