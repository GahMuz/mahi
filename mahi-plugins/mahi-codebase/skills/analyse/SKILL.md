---
name: analyse
description: "Ce skill est utilisé quand l'utilisateur invoque '/analyse' pour effectuer une analyse multi-dimensionnelle du code (qualité, architecture, RGPD, DORA) sur un ou tous les modules. Produit des rapports structurés avec scores par dimension dans .mahi/analyses/."
argument-hint: "<module> | --all"
allowed-tools: ["Read", "Write", "Glob", "Grep", "Bash", "Agent"]
---

# Analyse multi-dimensionnelle

Analyser un module ou tous les modules selon trois dimensions : qualité du code, architecture, conformité (RGPD/DORA). Toute sortie en **français**.

## Répertoire de sortie

```
.mahi/analyses/
├── manifest.json
└── <module>/
    ├── summary.md
    ├── quality.md
    ├── architecture.md
    ├── rgpd.md          (si données personnelles détectées)
    ├── dora.md          (si contexte financier/résilience détecté)
    └── candidates.md    (si nouvelles règles identifiées)
```

## Step 0 : Parser les arguments

Extraire la sous-commande :
- `<nom-module>` → RUN_MODULE
- `--all` → RUN_ALL
- aucun argument → demander : "Quel module analyser ? Ex : `<module>` ou `--all`"

## Détection des modules

Scanner le codebase pour identifier les modules :
- **PHP/Symfony** : répertoires namespace sous `src/`, bundles Symfony
- **Node/TS** : répertoires top-level sous `src/`, membres `packages/` workspace
- **Java** : modules Maven (`pom.xml`), sous-projets Gradle, répertoires package

## Manifest

Fichier : `.mahi/analyses/manifest.json`

Lire le manifest existant ou initialiser un nouveau. Mettre à jour après chaque analyse.

```json
{
  "schemaVersion": "<valeur de .mahi/config.json>",
  "updatedAt": "<ISO timestamp>",
  "modules": {
    "<module>": {
      "analysedAt": "<ISO timestamp>",
      "lastCommit": "<hash>",
      "scores": {
        "quality": 85,
        "architecture": 72,
        "rgpd": 90,
        "dora": null,
        "global": 82
      }
    }
  }
}
```

`null` = dimension non applicable au module.
`global` = moyenne des scores non-null.

## Routage

### RUN_MODULE
Lire et suivre `references/run-module.md`.

### RUN_ALL
Lire et suivre `references/run-all.md`.

## Commandes disponibles

| Commande | Description |
|----------|-------------|
| `/analyse <module>` | Analyser un module (qualité + architecture + conformité) |
| `/analyse --all` | Analyser tous les modules en parallèle |
