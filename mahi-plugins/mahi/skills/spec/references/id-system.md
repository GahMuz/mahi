# Système d'identifiants et références croisées

## Format des IDs

| Type | Format | Exemple |
|------|--------|---------|
| Exigence | `REQ-` + 3 chiffres | REQ-001, REQ-042 |
| Conception | `DES-` + 3 chiffres | DES-001, DES-015 |
| Tâche parent | `TASK-` + 3 chiffres | TASK-001, TASK-020 |
| Sous-tâche | `TASK-` + 3 chiffres + `.` + chiffre | TASK-001.1, TASK-020.3 |

## Règles

1. Les IDs parents sont séquentiels dans leur document, complétés à 3 chiffres (zero-padded)
2. Les IDs de sous-tâches sont séquentiels dans leur parent, un seul chiffre (1–9)
3. Une fois assigné, un ID est permanent — ne jamais le réutiliser
4. Éléments supprimés : retirer l'ID, sauter ce numéro pour les prochaines assignations
5. Les références croisées utilisent la notation entre crochets : `[REQ-001]`, `[TASK-001.2]`

## Chaînes de référence

```
REQ-001
  ├── DES-001 (implements: [REQ-001])
  │     └── TASK-001 (implements: [DES-001], fulfills: [REQ-001])
  │           ├── TASK-001.1 (créer l'entité)
  │           ├── TASK-001.2 (créer le repository)
  │           └── TASK-001.3 (créer le service)
  └── DES-002 (implements: [REQ-001])
        └── TASK-002 (implements: [DES-002], fulfills: [REQ-001])
              ├── TASK-002.1 (créer le middleware)
              └── TASK-002.2 (ajouter les gardes de route)
```

- Les tâches parents portent les références `implements` et `fulfills`
- Les sous-tâches héritent des références du parent sauf si elles les surchargent
- Les dépendances peuvent exister au sein d'un même parent ou entre parents différents

## Relation tâche / sous-tâche

- **Tâche parent** = regroupement logique (ex. "Créer le CRUD Utilisateur")
- **Sous-tâche** = unité atomique de 2–5 min, dispatchée à un agent
- Types de dépendances :
  - Au sein d'un parent : TASK-001.2 dépend de TASK-001.1
  - Entre parents : TASK-002.1 dépend de TASK-001.3
  - Niveau parent : TASK-002 dépend de TASK-001 (toutes les sous-tâches doivent être `[x]`)

## Icônes de statut

Préfixer chaque ligne tâche/sous-tâche dans plan.md :
- `[ ]` en attente
- `[~]` en cours
- `[x]` terminée
- `[!]` échouée ou bloquée

Mettre à jour les icônes en place au fur et à mesure de l'avancement.

## Workflow d'amendement

1. **Identifier les IDs concernés**
2. **Modifier en place** dans le document
3. **Mettre à jour le statut** à "modifié"
4. **Logger dans changelog** : `{"date": "...", "ids": ["REQ-003"], "reason": "..."}`
5. **Propager** : REQ → DES → TASK → sous-tâches
6. **Marquer `[!]`** les sous-tâches incomplètes qui référencent les éléments modifiés

## Détection des orphelins

Avant de finaliser plan.md :

| Vérification | Détecte |
|--------------|---------|
| Chaque REQ → >= 1 DES | Exigences sans conception |
| Chaque DES → >= 1 TASK | Conceptions sans tâches |
| Chaque TASK → >= 1 sous-tâche | Tâches parents sans étapes |
| Chaque TASK parent → >= 1 DES + >= 1 REQ | Tâches sans traçabilité |
| Pas de références pendantes | Typos ou IDs supprimés |

Les REQ orphelins bloquent l'approbation du plan.
