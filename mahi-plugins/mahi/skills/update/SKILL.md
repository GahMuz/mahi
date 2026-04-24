---
name: update
description: "Ce skill est utilisé quand l'utilisateur invoque '/update' pour migrer un projet existant depuis une version antérieure du schéma Mahi vers la version courante. Lit schemaVersion depuis .mahi/config.json, résout les migrations en attente depuis migrations.md dans l'ordre, et applique chacune. Supporte --dry-run pour prévisualisation."
argument-hint: "[--dry-run]"
allowed-tools: ["Read", "Write", "Bash", "Glob", "Grep", "Edit"]
---

# Migrator Mahi

Applique les migrations de schéma en séquence depuis la version installée jusqu'à la version courante.
Tout output en français.

## Step 0 : Localiser le projet

Chercher `.mahi/config.json`.

Si absent : "Aucun projet Mahi détecté. Lancez `/init` d'abord."

Lire `schemaVersion` dans config.json.
Si le champ est absent → supposer `"1.0.0"` (version initiale).

## Step 1 : Déterminer le plan de migration

Lire `migrations/migrations.md` dans le répertoire de ce skill.
Parser le tableau : chaque ligne est `| vX.Y.Z | <fichier ou -> |`.

- La première entrée de données = **version cible courante** (ordre DESC).
- Filtrer : garder uniquement les lignes dont la version est **strictement supérieure** à `schemaVersion`.
- Trier les lignes filtrées par version sémantique **croissante** (ordre d'application).

Si aucune ligne à appliquer : "Schéma déjà à jour (v`<schemaVersion>`). Aucune migration nécessaire."

## Step 2 : Afficher le plan

```
## Plan de migration

Version installée : <schemaVersion>
Version cible     : <version cible>

Migrations à appliquer :
1. v1.1.0 — <titre de la migration ou "Aucune étape requise">

Mode : SIMULATION      ← si --dry-run
       MIGRATION RÉELLE ← sinon
```

"Procéder ? (oui / annuler)"
Si `--dry-run` : afficher le plan uniquement, ne rien modifier, terminer.

## Step 3 : Appliquer les migrations en séquence

Pour chaque version dans l'ordre croissant :

1. Lire l'entrée dans migrations.md :
   - Si la colonne Migration vaut `-` : mettre à jour `schemaVersion` et passer à la suivante
   - Si la colonne Migration contient un lien : lire `migrations/vX.Y.Z.md`
2. Exécuter les étapes décrites dans le fichier de migration
3. Après succès : mettre à jour `schemaVersion` dans config.json avec cette version via Edit
4. Si une étape échoue : **s'arrêter immédiatement**, signaler l'erreur, ne pas continuer

Mettre à jour `schemaVersion` après **chaque** migration réussie — permet de reprendre si une migration intermédiaire échoue.

## Step 4 : Rapport final

```
Migration terminée ✓

Migrations appliquées :
- v1.1.0 : <titre>

schemaVersion : <ancienne> → <nouvelle>

Prochaine étape : commitez la migration, puis continuez avec `/spec` normalement.
```
