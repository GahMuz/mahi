---
name: graph-status
description: "This skill should be used when the user invokes '/graph-status' to see which graphs are built, their freshness state, and coverage statistics. Reads .mahi/graph/manifest.json and checks git log for staleness."
argument-hint: ""
allowed-tools: ["Read", "Bash"]
---

# État des graphes

Afficher l'état du manifest et la fraîcheur des graphes. Toute sortie en **français**.

## Step 1 : Lire le manifest

Lire `.mahi/graph/manifest.json`.

Si absent : "Aucun graphe construit. Lancer `/graph-build --java` pour démarrer (nécessite le plugin `mahi-codebase`)."

## Step 2 : Vérifier la fraîcheur en temps réel

Pour chaque graphe dont `status == "fresh"` dans le manifest :

```bash
git log <lastCommit>..HEAD -- <sourcePaths.java> --oneline
```

Si output non vide → marquer comme stale dans l'affichage (sans modifier le manifest).

## Step 3 : Afficher le tableau

```
Graphes structurels — .mahi/graph/

Version schema : <schemaVersion>
Dernière mise à jour : <updatedAt>
Stack : <stacks>

┌─────────────────┬──────────┬────────────────────┬──────────────┐
│ Graphe          │ Entrées  │ Construit le        │ État         │
├─────────────────┼──────────┼────────────────────┼──────────────┤
│ endpoint-flow   │ 42       │ 2026-04-16 10:00    │ ✅ frais     │
│ entity-model    │ 18       │ 2026-04-16 10:00    │ ✅ frais     │
│ service-call    │ 12n/23e  │ 2026-04-16 10:00    │ ⚠ obsolète  │
│ module-dep      │ 6        │ 2026-04-16 10:00    │ ✅ frais     │
└─────────────────┴──────────┴────────────────────┴──────────────┘

(n = nœuds, e = edges pour service-call)
```

Légende :
- `✅ frais` : aucun changement git depuis la dernière construction
- `⚠ obsolète` : changements détectés dans les sources depuis la dernière construction
- `❌ absent` : jamais construit
- `⚡ partiel` : construit mais avec des warnings (détection incomplète)

## Step 4 : Afficher les recommandations

Si des graphes sont obsolètes ou absents :

```
Recommandations :
- Reconstruire les graphes obsolètes : /graph-build --incremental
- Tout reconstruire : /graph-build --java
```

Si tous les graphes sont frais :

```
Tous les graphes sont à jour. Utiliser /graph-query <question> pour les interroger.
```
