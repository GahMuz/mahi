---
name: status
description: "Ce skill est utilisé quand l'utilisateur invoque '/status', demande 'liste des specs', 'état des specs', 'quels specs sont en cours', 'voir tous les workflows', ou veut une vue d'ensemble de tous les workflows actifs et en cours. Affiche l'item actif, les workflows en cours (tous types), et les éléments terminés."
argument-hint: "[--all]"
allowed-tools: ["Read", "Glob", "mcp__plugin_mahi_mahi__get_active", "mcp__plugin_mahi_mahi__get_workflow"]
---

# Vue d'ensemble des workflows

Tout output en français.

`--all` : inclure les items abandonnés dans l'affichage.

## Step 1 : Item actif

Appeler `mcp__plugin_mahi_mahi__get_active()`.
- Retourne `{ type, id, path, activatedAt, workflowId }` ou null.
- Si non null : appeler `mcp__plugin_mahi_mahi__get_workflow(workflowId)` pour récupérer `currentPhase` et le bloc `progress` (si phase = `implementation`).

## Step 2 : Lire le registre

Lire `.mahi/registry.json`.
Si absent : "Aucun workflow enregistré — lancez `/init` puis `/spec new <titre>`."

Parser toutes les entrées. Chaque entrée contient au minimum : `id`, `type`, `title`, `phase`, `workflowId`, `updatedAt`.

Catégoriser :
- **actif** : id correspond à l'item retourné par `get_active()`
- **en cours** : phase ≠ `completed` et ≠ `discarded` et ≠ actif
- **terminé** : phase = `completed`
- **abandonné** : phase = `discarded`

## Step 3 : Afficher l'item actif

Si item actif :
```
★ Actif : <titre> [<type>]
  Phase       : <currentPhase>
  Progression : <completedSubtasks>/<totalSubtasks> sous-tâches   ← si phase = implementation
  Activé le   : <activatedAt>
```

Si null : "Aucun item actif."

## Step 4 : Afficher les items en cours

Si un seul `type` présent dans "en cours" → pas de header de type, liste directe.
Si plusieurs types → grouper par `type`, un header par groupe.

Trier par `updatedAt` décroissant dans chaque groupe.

```
En cours (N) :

  [si plusieurs types]
  <Type> :
    <titre> — <phase> — MAJ <date>
    ...

  [si un seul type]
  <titre> — <phase> — MAJ <date>
  ...
```

Si aucun : omettre la section.

## Step 5 : Afficher les items terminés

Grouper par `type` si plusieurs types, sinon liste directe.
Trier par `updatedAt` décroissant. Maximum 10 par type.

```
Terminés (N) :
  <titre> — <date>
  ...
  (+ X autres non affichés)   ← si > 10
```

Si aucun : omettre la section.

## Step 6 : Afficher les items abandonnés

Afficher uniquement si `--all` est passé ET qu'il existe des items abandonnés.
Maximum 5 par type.

```
Abandonnés :
  <titre> — <date>
  ...
```

## Step 7 : Suggestions contextuelles

Afficher en bas selon l'état observé :

| Situation | Suggestion |
|-----------|------------|
| Item actif en requirements/design/planning | `/spec recap` — briefing complet de l'état actuel |
| Item actif en implementation | `/spec` — voir la progression des sous-tâches |
| Item actif en finishing/retrospective | `/spec approve` — valider la phase |
| Aucun item actif + items en cours | `/spec open <titre>` — reprendre un workflow |
| Aucun item actif + aucun en cours | `/spec new <titre>` — démarrer un nouveau workflow |
| Items en cours > 3 | Rappeler : un seul item actif à la fois — `/spec open <titre>` pour choisir |

Toujours afficher au maximum 2 suggestions — ne pas surcharger.
