# Spec Document Templates

All document content is in French. JSON keys remain in English.

## requirement.md

```markdown
# Exigences : <Titre du Spec>

> Spec ID : <spec-id>
> Auteur : <git user.name>
> Créé le : <ISO-8601>
> Statut : brouillon | approuvé | modifié

## Contexte

<2-3 phrases : problème métier, opportunité, ou besoin à couvrir.
Pas d'architecture ni de solution technique.>

## Glossaire

| Terme | Définition |
|-------|------------|
| <Terme> | <Définition courte dans le contexte de cette spec> |

## Périmètre

### Dans le périmètre

| Composant / Fonctionnalité | Description |
|----------------------------|-------------|
| <Élément> | <Ce qui est inclus et pourquoi> |

### Hors périmètre

| Composant / Fonctionnalité | Motif d'exclusion |
|----------------------------|-------------------|
| <Élément> | <Pourquoi exclu — différé, autre spec, hors domaine> |

## Contexte codebase

| Module | Rôle | Pertinence pour cette spec |
|--------|------|---------------------------|
| <module-name> | <description courte> | <pourquoi c'est pertinent> |

> Source : docs `.mahi/` | investigation `spec-deep-dive` | Aucun module existant concerné

## Exigences fonctionnelles

### REQ-001 : <Titre court>

**Récit utilisateur :**
En tant que <rôle>, je veux <capacité> afin de <bénéfice>.

**Critères d'acceptation :**

1. LE <Système/Composant> DOIT <action>
2. QUAND <condition> ALORS LE <Système> DOIT <action>
3. QUAND <condition> ALORS LE <Système> NE DOIT PAS <action>
4. LE <Système> DEVRAIT <action souhaitée>
5. LE <Système> PEUT <comportement optionnel permis>

**Priorité :** obligatoire | souhaitable | optionnel

**Statut :** brouillon | approuvé | modifié

---

### REQ-002 : <Titre court>

**Récit utilisateur :**
En tant que <rôle>, je veux <capacité> afin de <bénéfice>.

**Critères d'acceptation :**

1. LE <Système/Composant> DOIT <action>
2. QUAND <condition> ALORS LE <Système> DOIT <action>

**Priorité :** obligatoire | souhaitable | optionnel

**Statut :** brouillon | approuvé | modifié

---

## Exigences non fonctionnelles

### REQ-NF-001 : <Titre court — Performance | Sécurité | Scalabilité | …>

**Critères d'acceptation :**

1. LE <Système> DOIT <action mesurable avec seuil chiffré>
2. QUAND <condition de charge / contexte> ALORS LE <Système> DOIT <garantie>

**Priorité :** obligatoire | souhaitable | optionnel

**Statut :** brouillon | approuvé | modifié

...
```

## design.md

```markdown
# Conception : <Titre du Spec>

> Spec ID : <spec-id>
> Créé le : <ISO-8601>
> Statut : brouillon | approuvé | modifié

## Vue d'ensemble

<Approche globale et résumé des décisions clés>

## Sections de conception

### DES-001 : <Titre du composant/décision>

**Implémente :** [REQ-001], [REQ-002]

**Problème :** <Ce qui doit être résolu>

**Approche :** <Solution retenue avec détails>

**Justification :** <Pourquoi cette approche>

**Alternatives considérées :**
1. <Alternative A> — rejetée car <raison>
2. <Alternative B> — rejetée car <raison>

**Compromis :** <Compromis connus de l'approche choisie>

**Contrat de test :**
- Comportements à vérifier :
  - <comportement dérivé du critère d'acceptation REQ-xxx>
- Cas limites :
  - <cas limite identifié>
- Intégrations à tester :
  - <module ou service impliqué>

**Statut :** brouillon | approuvé | modifié

---

### DES-002 : <Titre>

**Implémente :** [REQ-003]

...

## Couverture des exigences

| REQ | DES | Couvert |
|-----|-----|---------|
| REQ-001 | DES-001 | ✅ |
| REQ-002 | DES-001 | ✅ |
| REQ-003 | DES-002 | ✅ |
| REQ-004 | — | ❌ À traiter |

Cette matrice permet de repérer les exigences non couvertes d'un coup d'œil.
```

## plan.md

```markdown
# Plan : <Titre du Spec>

> Spec ID : <spec-id>
> Créé le : <ISO-8601>
> Statut : brouillon | approuvé | modifié

## Vue d'ensemble

<Résumé de l'approche d'implémentation et organisation des tâches>

## Graphe de dépendances

  TASK-001 ──┐
  TASK-002 ──┼── TASK-004 ── TASK-006
  TASK-003 ──┘       │
                 TASK-005

## Tâches

### [ ] TASK-001 : Créer le CRUD pour l'entité Utilisateur

**Implémente :** [DES-001]
**Satisfait :** [REQ-001]
**Dépendances :** aucune

#### Sous-tâches

- [ ] **TASK-001.1 [RED] : Écrire les tests de l'entité Utilisateur**
  **Effort estimé :** ~2 min
  **Description :** Écrire les tests en échec pour le modèle Utilisateur (id, email, nom, timestamps) — depuis contrat de test DES-001.
  **Fichiers :**
  - `src/entities/User.test.ts` (créer)
  **Vérification :** tests doivent échouer (module non encore créé)

- [ ] **TASK-001.2 [GREEN] : Implémenter l'entité Utilisateur**
  **Effort estimé :** ~2 min
  **Dépendances :** [TASK-001.1]
  **Description :** Créer le modèle Utilisateur minimal pour faire passer les tests TASK-001.1.
  **Fichiers :**
  - `src/entities/User.ts` (créer)
  **Vérification :**
  ```bash
  npx jest src/entities/User.test.ts
  ```

- [ ] **TASK-001.3 [RED] : Écrire les tests du repository Utilisateur**
  **Effort estimé :** ~2 min
  **Dépendances :** [TASK-001.2]
  **Description :** Écrire les tests en échec pour findById, findByEmail, save, delete.
  **Fichiers :**
  - `src/repositories/UserRepository.test.ts` (créer)
  **Vérification :** tests doivent échouer

- [ ] **TASK-001.4 [GREEN] : Implémenter le repository Utilisateur**
  **Effort estimé :** ~3 min
  **Dépendances :** [TASK-001.3]
  **Description :** Créer le repository pour faire passer les tests TASK-001.3.
  **Fichiers :**
  - `src/repositories/UserRepository.ts` (créer)
  **Vérification :**
  ```bash
  npx jest src/repositories/UserRepository.test.ts
  ```

---

### [ ] TASK-002 : Ajouter le middleware d'authentification

**Implémente :** [DES-002]
**Satisfait :** [REQ-002]
**Dépendances :** [TASK-001]

#### Sous-tâches

- [ ] **TASK-002.1 : Créer le middleware auth**
  ...
```

## workflow.json Schema

Le workflow est géré par le serveur Mahi. Structure de référence :

```json
{
  "schemaVersion": "1.0.0",
  "specId": "feature-name",
  "title": "Titre lisible",
  "currentPhase": "requirements",
  "createdAt": "2026-04-10T12:00:00Z",
  "updatedAt": "2026-04-10T12:00:00Z",
  "baseBranch": "main",
  "branch": null,
  "worktreePath": null,
  "phases": {
    "requirements": { "status": "pending", "startedAt": null, "approvedAt": null },
    "design":       { "status": "pending", "startedAt": null, "approvedAt": null },
    "worktree":     { "status": "pending", "startedAt": null, "completedAt": null },
    "planning":     { "status": "pending", "startedAt": null, "approvedAt": null },
    "implementation": { "status": "pending", "startedAt": null, "completedAt": null },
    "finishing":    { "status": "pending", "startedAt": null, "completedAt": null }
  },
  "progress": {
    "totalTasks": 0,
    "totalSubtasks": 0,
    "completedSubtasks": 0,
    "failedSubtasks": [],
    "currentBatch": [],
    "completedBatches": []
  },
  "changelog": []
}
```

### config.json schema (project-level)

Fichier `.mahi/config.json` à la racine du projet :

```json
{
  "schemaVersion": "1.0.0",
  "languages": ["php", "node-typescript", "java"],
  "pipelineReviews": true,
  "parallelTaskLimit": 0,
  "models": {
    "spec-orchestrator": "opus",
    "spec-task-implementer": "sonnet",
    "spec-code-reviewer": "sonnet",
    "spec-deep-dive": "opus"
  },
  "createdAt": "2026-04-10T12:00:00Z"
}
```

- `parallelTaskLimit`: 0 = illimité, >0 = max agents concurrents par vague
- `pipelineReviews`: true = revue de la vague N pendant l'implémentation de N+1
- `models`: modèle par agent (opus, sonnet, haiku) — l'orchestrateur dispatche avec le modèle configuré

## log.md

```markdown
# Journal : <Titre du Spec>

> Spec ID : <spec-id>

## Entrées

### <ISO-8601> — <Phase>

**Actions :**
- <action effectuée>

**Décisions :**
- <décision prise et justification>

**Mises à jour spec :**
- <documents modifiés et IDs affectés>

**Bloquants :**
- <bloquant ou "Aucun">

**Prochaines étapes :**
- <ce qui vient ensuite>

---

### <ISO-8601> — <Phase>

...
```

## baseline-tests.json

Capturé avant le début de l'implémentation. Sert à détecter les régressions.

```json
{
  "capturedAt": "2026-04-10T13:00:00Z",
  "command": "npm test",
  "total": 142,
  "passed": 142,
  "failed": 0,
  "skipped": 0,
  "breakingChanges": []
}
```

Après implémentation, `breakingChanges` est rempli avec les tests nouvellement échoués :
```json
{
  "breakingChanges": [
    {
      "test": "UserService.should return user by id",
      "file": "tests/UserService.test.ts",
      "reason": "Forme de la réponse API modifiée — breaking change documenté",
      "taskId": "TASK-001.3"
    }
  ]
}
```

### Entrée changelog

```json
{ "date": "2026-04-10T14:30:00Z", "ids": ["REQ-003", "DES-001"], "reason": "Auth doit utiliser OAuth2" }
```

### Entrée completedBatches

```json
{
  "tasks": ["TASK-001"],
  "subtasks": ["TASK-001.1", "TASK-001.2", "TASK-001.3"],
  "reviewStatus": "passed",
  "reviewedAt": "2026-04-10T15:00:00Z"
}
```

## Review Document Template

### reviews/TASK-xxx-review.md

```markdown
# Revue : TASK-xxx — <Titre de la tâche>

> Révisé le : <ISO-8601>
> Réviseur : agent code-reviewer
> Sous-tâches révisées : TASK-xxx.1, TASK-xxx.2, ...

## Étape 1 : Conformité au spec

**Résultat :** conforme | non conforme

**Constats :**
- <constat ou "Aucun problème">

## Étape 2 : Qualité du code

**Résultat :** conforme | non conforme

**Constats :**
- <constat ou "Aucun problème">

## Étape 3 : Règles plugin et projet

**Résultat :** conforme | non conforme | ignoré (mahi:rules introuvable)

**Constats :**
- <constat ou "Aucun problème">

## Résumé

| Métrique | Nombre |
|----------|--------|
| Critique | 0 |
| Avertissement | 0 |
| Info | 0 |

**Recommandation :** continuer | continuer avec corrections | correction requise
```
