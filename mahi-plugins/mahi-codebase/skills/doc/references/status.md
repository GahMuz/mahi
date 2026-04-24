# Statut de la documentation

Procédure pour la sous-commande STATUS.

## Step 1 : Lire le manifest

- Lire `.mahi/docs/manifest.json`
- Si absent : "Aucune documentation existante. Lancez `/doc --all` pour démarrer."

## Step 2 : Évaluer la fraîcheur

Pour chaque module et feature, appliquer les règles de fraîcheur de SKILL.md.
Marquer chaque item comme **frais** ou **obsolète** avec la raison.

## Step 3 : Afficher le rapport

```
# Statut de la documentation

> Version skill : <schemaVersion depuis .mahi/config.json>
> Manifest : .mahi/docs/manifest.json

## Modules

| Module | Features | Fichiers | Générée le | Version | État |
|--------|----------|----------|------------|---------|------|
| <nom> | <count> | <count> | <date> | <version> | Frais / Obsolète (<raison>) |

## Résumé

- Modules documentés : X
- Features documentées : Y
- Documents obsolètes : Z

## Action suggérée

<Si obsolètes : "Lancez `/doc update` pour regénérer les X documents obsolètes.">
<Si analyses absentes : "Lancez `/analyse <module>` pour une analyse qualité/architecture/conformité.">
<Si tout est frais : "Documentation à jour.">
```
