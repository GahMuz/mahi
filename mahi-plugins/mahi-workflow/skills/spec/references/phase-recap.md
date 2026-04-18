# Récapitulatif de la spec active

Présente un briefing complet de la spec active en français.

## Step 1: Lire l'état via Mahi MCP

Appeler le serveur Mahi pour obtenir l'état courant :
```
mahi_get_workflow(workflowId: <lire depuis active.json>)
```

> Note mahi-workflow : il n'y a pas de lecture de state.json pour obtenir la phase.
> La source de vérité est toujours `mahi_get_workflow`.

- Extraire `phase` (phase courante) et `updatedAt` de la réponse
- Si implémentation : lire `plan.md`, compter `[x]`, `[~]`, `[ ]`, `[!]` sous-tâches
- Sinon : lire le document de la phase courante (requirement.md / design.md / plan.md), compter les items

## Step 2: Charger le contexte

Lire `context.md` dans le répertoire de la spec (si présent).
Si absent : noter "Aucun contexte sauvegardé — `/spec close` créera un context.md."

## Step 3: Présenter le briefing

```
## Récap — <titre>

**Phase :** <phase>  **Progression :** <X/Y sous-tâches> (si implémentation)

### Objectif
<1-2 phrases depuis context.md ou requirement.md>

### Où on en est
<résumé de la phase — ce qui a été fait, ce qui reste>

### Décisions clés
- <DES-xxx> : <décision et justification courte>
- ...

### Questions ouvertes
- [ ] <question bloquante ou importante>
- ...

### Commandes disponibles
<liste selon la phase courante — voir Step 3b>
```

## Step 3b: Commandes disponibles par phase

Insérer dans "Commandes disponibles" les lignes correspondant à la phase courante :

**requirements :**
- `/spec approve` — valider les exigences et passer à la conception
- `/spec clarify` — modifier ou affiner une exigence
- `/spec discard` — abandonner ce spec

**design :**
- `/spec approve` — valider la conception et passer à la planification
- `/spec clarify` — modifier une décision de design
- `/spec-review` — audit de cohérence spec/code
- `/spec discard` — abandonner ce spec

**planning :**
- `/spec approve` — valider le plan et démarrer l'implémentation
- `/spec clarify` — ajuster une tâche ou sous-tâche
- `/spec discard` — abandonner ce spec

**execution (en cours) :**
- attendre la fin de l'orchestrateur
- `/spec clarify` — clarifier une sous-tâche en attente
- `/spec-review` — audit manuel de cohérence spec/code

**execution (100% `[x]`) :**
- `/spec approve` — passer à la phase finishing (push de la branche, pas de merge)
- `/spec-review` — relancer un audit avant de finaliser

**finishing :**
- répondre au choix affiché : **Valider** (pousser + rétrospective) | **Fermer** (reprendre plus tard) | **Abandonner**

**retrospective :**
- en cours — répondre aux propositions de règles une par une

**completed :**
- `/spec open <titre>` — ouvrir un autre spec

## Step 4: Vérifier le worktree

Exécuter `git status --short` dans le worktree (si `worktreePath` est défini dans la réponse de `mahi_get_workflow`).
Si des modifications non commitées : "Attention : modifications non commitées dans le worktree."
