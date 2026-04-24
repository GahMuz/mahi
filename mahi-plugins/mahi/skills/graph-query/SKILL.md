---
name: graph-query
description: "This skill should be used when the user invokes '/graph-query' to ask structural questions about the codebase (impact analysis, endpoint flows, entity definitions, service dependencies). Reads prebuilt graph artifacts from .mahi/graph/ without scanning source code."
argument-hint: "<question>"
allowed-tools: ["Read", "Glob", "Agent"]
---

# Requête du graphe structurel

Répondre à des questions structurelles depuis les artifacts JSON pré-calculés. **Aucun scan de code source.** Toute sortie en **français**.

## Pré-requis

Les graphes doivent avoir été construits via `/graph-build` (plugin `mahi-codebase`). Vérifier d'abord le manifest.

## Step 0 : Vérifier la disponibilité

Lire `.mahi/graph/manifest.json`.

Si absent : "Aucun graphe disponible. Lancer `/graph-build --java` d'abord (nécessite le plugin `mahi-codebase`)."

Pour chaque graphe stale pertinent à la question → avertir : "⚠ Le graphe `<nom>` est obsolète (modifié depuis la dernière construction). Résultats potentiellement incomplets."

## Step 1 : Dispatcher graph-query

```
Agent({
  description: "Requête graphe : <question>",
  subagent_type: "graph-query",
  model: "haiku",
  prompt: "
    Question : <question de l'utilisateur>
    Chemin des graphes : .mahi/graph/
    Manifest : <contenu du manifest.json>
  "
})
```

## Step 2 : Afficher la réponse

Transmettre la réponse de l'agent directement à l'utilisateur.

## Exemples de questions

```
/graph-query qui dépend de UserService ?
/graph-query chemin complet de POST /api/v1/orders
/graph-query quelles entités touche le module payment ?
/graph-query quel est le blast radius si je modifie OrderRepository ?
/graph-query quels modules dépendent de shared ?
/graph-query liste tous les endpoints sécurisés ROLE_ADMIN
```
