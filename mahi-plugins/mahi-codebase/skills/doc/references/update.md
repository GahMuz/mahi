# Mise à jour de la documentation

Procédure pour la sous-commande UPDATE.

## Step 1 : Lire le manifest

- Lire `.mahi/docs/manifest.json`
- Si absent : "Aucune documentation existante. Lancez `/doc --all` d'abord."

## Step 2 : Évaluer la fraîcheur

Pour chaque module et chaque feature dans le manifest, appliquer les règles de fraîcheur :

1. **Version différente** : `skill_version` du doc != `schemaVersion` dans `.mahi/config.json`
2. **Âge > 30 jours** : `generated_at` date de plus de 30 jours
3. **Code modifié** : `git log <last_commit>..HEAD -- <path>` retourne des commits
4. **Commit introuvable** : `last_commit` absent du git (rebase) → obsolète

Les analyses de code (qualité, architecture, conformité) sont gérées séparément par `/analyse <module>` — hors du périmètre de `/doc update`.

## Step 3 : Présenter le rapport

Afficher un tableau trié par priorité :

```
## Documentation obsolète

| Priorité | Module | Feature | Raison | Dernière génération |
|----------|--------|---------|--------|---------------------|
| HAUTE | domain-base | role | Version différente (0.3.0 → 0.4.0) | 2026-03-01 |
| HAUTE | domain-user | auth | Code modifié (5 commits) | 2026-03-15 |
| MOYENNE | domain-tox | — | > 30 jours | 2026-02-28 |
| MOYENNE | domain-base | earning | > 30 jours | 2026-03-05 |

Total : X documents obsolètes sur Y.
```

Priorité :
- **HAUTE** : version différente ou commit introuvable
- **MOYENNE** : > 30 jours ou code modifié

## Step 4 : Demander confirmation

"Regénérer X documents obsolètes ? (oui / non / sélection manuelle)"

- **oui** → regénérer tous les obsolètes
- **non** → terminer
- **sélection manuelle** → laisser l'utilisateur choisir lesquels

## Step 5 : Regénérer

Dispatcher les agents en parallèle pour les items approuvés.
Suivre la même logique de dispatch que `references/generate-module.md` Step 5.

Seuls les items obsolètes sont regénérés — les items frais sont ignorés.

## Step 6 : Mettre à jour manifest et index

- Mettre à jour le manifest avec les nouvelles métadonnées
- Regénérer `index.md`

## Step 7 : Reporter

```
Mise à jour terminée :
- Regénérés : X modules, Y features
- Ignorés (frais) : Z
- Manifest et index mis à jour.
```
