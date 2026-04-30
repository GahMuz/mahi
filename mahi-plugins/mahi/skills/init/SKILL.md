---
name: init
description: "Ce skill est utilisé quand l'utilisateur invoque '/init' pour initialiser un projet pour le développement spec-driven avec Mahi : créer '.mahi/', configurer les langages, les modèles d'agents, et scaffolding des règles projet."
argument-hint: ""
allowed-tools: ["Read", "Write", "Edit", "Bash", "Glob", "Grep"]
---

# Initialisation projet Mahi

Tout output en français.

## Step 1 : Vérifier la config existante

Vérifier si `.mahi/config.json` existe :
- Existe → afficher la config, demander : "Reconfigurer ou garder la configuration existante ?"
- Absent → continuer

## Step 2 : Sélectionner les langages

Détecter automatiquement depuis les fichiers projet :
- `php` — PHP (détecter : `composer.json`)
- `node-typescript` — Node.js / TypeScript (détecter : `package.json`)
- `java` — Java (détecter : `pom.xml`, `build.gradle`)

"Quels langages utilise ce projet ? (sélectionner un ou plusieurs)"
Afficher les langages détectés comme suggestions.

## Step 3 : Configurer l'exécution

- "Limite de sous-tâches en parallèle ? (0 = illimité)" → défaut 0
- "Activer le pipeline de revues ? (revoir le lot N pendant l'implémentation du lot N+1)" → défaut oui

## Step 4 : Configurer les modèles

Présenter les valeurs par défaut :

| Agent | Rôle | Modèle par défaut |
|-------|------|-------------------|
| spec-orchestrator | Coordination des vagues d'implémentation | sonnet |
| spec-task-implementer | Écriture de code TDD | sonnet |
| spec-code-reviewer | Quality gate par lot | opus |
| spec-deep-dive | Investigation architecturale | opus |
| spec-planner | Planification des tâches | haiku |
| spec-design-validator | Validation SOLID + contrats | sonnet |
| spec-requirements | Rédaction des exigences | sonnet |
| spec-design | Conception technique | sonnet |
| spec-reviewer | Revue finale cohérence spec | sonnet |
| doc-generator | Génération documentation | haiku |
| analyse-quality | Analyse qualité du code | sonnet |
| analyse-architecture | Analyse architecturale | opus |
| analyse-compliance | Analyse conformité RGPD/DORA | sonnet |

"Garder les valeurs par défaut ? (oui/non)"
Si non, laisser l'utilisateur personnaliser chaque modèle.

## Step 5 : Créer la structure .mahi/

```bash
mkdir -p .mahi/work .mahi/docs .mahi/.local
```

Écrire `.mahi/config.json` :
```json
{
  "schemaVersion": "1.0.0",
  "languages": ["<selected>"],
  "pipelineReviews": true,
  "parallelTaskLimit": 0,
  "models": {
    "spec-orchestrator": "sonnet",
    "spec-task-implementer": "sonnet",
    "spec-code-reviewer": "opus",
    "spec-deep-dive": "opus",
    "spec-planner": "haiku",
    "spec-design-validator": "sonnet",
    "spec-requirements": "sonnet",
    "spec-design": "sonnet",
    "spec-reviewer": "sonnet",
    "doc-generator": "haiku",
    "analyse-quality": "sonnet",
    "analyse-architecture": "opus",
    "analyse-compliance": "sonnet",
    "graph-builder-java": "haiku",
    "graph-query": "haiku"
  },
  "graph": {
    "enabled": false,
    "stacks": [],
    "sourcePaths": {},
    "stalenessThresholdDays": 7
  },
  "createdAt": "<ISO-8601>"
}
```

Note : `graph.enabled` reste `false` jusqu'à ce que le plugin sdd-graph soit installé et configuré (voir Step 9).

## Step 6 : Mettre à jour .gitignore

Ajouter dans `.gitignore` si absent :
```
.worktrees/
.mahi/.local/
```

Ne pas ignorer `.mahi/` lui-même — seul `.mahi/.local/` est exclu des commits.

## Step 7 : Scaffolding des règles projet

Créer le squelette dans `.claude/skills/rules-references/` :

Écrire `.claude/skills/rules-references/SKILL.md` :
```markdown
---
name: rules-references
description: Ce skill doit être utilisé quand on vérifie les "règles projet", "conventions de code", "standards projet", ou lors de la validation en phase de design, implémentation, ou revue de code. Activement vérifié à chaque étape du workflow spec.
---

# Règles et conventions du projet

Activement vérifié à chaque étape du workflow spec :
- **Planification** : les règles sont intégrées dans le plan
- **Implémentation** : les agents chargent uniquement les fichiers pertinents pour la sous-tâche
- **Revue de code** : le réviseur vérifie chaque règle — violations = CRITIQUE

## Index des références (index vivant — maintenir à jour)

| Fichier | Domaine | Charger quand |
|---------|---------|---------------|
| `references/rules.md` | Transversal | Toujours (règles de base) |

Enrichi automatiquement via la rétrospective à la fin de chaque spec.
Exemples de fichiers ajoutés au fil du temps :
- `rules-controller.md` — Règles contrôleurs/routes → charger pour sous-tâches contrôleur
- `rules-service.md` — Règles services → charger pour sous-tâches service
- `rules-entity.md` — Règles entités/modèles → charger pour sous-tâches entité
- `rules-test.md` — Règles tests → charger pour sous-tâches test
- `rules-security.md` — Règles sécurité → charger pour sous-tâches auth/sécurité
```

Écrire `.claude/skills/rules-references/references/rules.md` :
```markdown
# Règles transversales (vérifiables)

Chaque règle est vérifiable par grep, glob ou revue de code.

## Règles non-négociables

- [ ] Pas de secrets en dur (mots de passe, clés API, tokens)
- [ ] Pas de console.log / var_dump / System.out.println oubliés
- [ ] Imports suivent les conventions du projet
- [ ] Gestion d'erreurs explicite (pas de catch vide)
- [ ] Pas de modification de fichiers générés automatiquement
- [ ] Texte UI dans la langue du projet
- [ ] Pas de dépendances ajoutées sans justification

## Portes de qualité

- [ ] Tests passent
- [ ] Linter passe
- [ ] Typecheck passe (si applicable)
- [ ] Revue de code approuvée par lot
- [ ] Pas de vulnérabilités de sécurité connues

## Contraintes d'architecture

- [ ] Placement des fichiers suit les conventions
- [ ] Séparation des couches respectée
- [ ] Appels API via la couche service

À personnaliser par l'équipe.
```

Pas de fichiers `rules-*.md` domaine créés à l'init — ils émergent organiquement via la rétrospective à chaque spec.

## Step 8 : Suggérer les guard skills

Expliquer le pattern guard skills :

"Les 'guard skills' sont des skills de validation dédiés aux invariants critiques du projet. Exemples :
- `guard-security` — audit de sécurité (authentification, autorisation, injection)
- `guard-data-isolation` — isolation des données (multi-tenant, RGPD)
- `guard-api-contract` — conformité des contrats API

Créez-les dans `.claude/skills/guard-<nom>/SKILL.md` avec `allowed-tools: [Read, Grep, Glob]` (lecture seule).
Le réviseur de code les invoquera lors des revues."

Demander : "Voulez-vous créer un guard skill maintenant ? (non par défaut)"

## Step 9 : Proposer sdd-graph (si Java détecté)

Si `java` est dans les langages sélectionnés ET si le plugin `sdd-graph` est installé
(vérifier avec `Glob("**/.claude-plugin/plugin.json")` → chercher `"name": "sdd-graph"`) :

```
Le plugin sdd-graph est disponible.
Il pré-calcule les graphes de dépendances Java (endpoints, entités, services, modules)
pour réduire la consommation de tokens et activer l'analyse d'impact dans les specs.

Configurer maintenant ? (recommandé pour les projets Spring Boot)
```

Si oui :
- Détecter automatiquement `src/main/java` si présent, sinon demander le chemin
- Mettre à jour `config.json` :
  ```json
  "graph": {
    "enabled": true,
    "stacks": ["java"],
    "sourcePaths": { "java": "<chemin détecté>" },
    "stalenessThresholdDays": 7
  }
  ```
- Proposer de construire les graphes immédiatement :
  "Lancer /graph-build --java maintenant ? (recommandé avant le premier /spec new)"

Si non ou si plugin absent : laisser `graph.enabled: false`, ne pas insister.

## Step 10 : Rapport

```
Projet initialisé pour le développement spec-driven (Mahi) :
- Langages : <liste>
- Modèles : orchestrateur=<model>, implémenteur=<model>, réviseur=<model>, investigation=<model>
- Graphe sdd-graph : <activé avec sourcePath | non configuré>
- Configuration : .mahi/config.json
- Règles projet : .claude/skills/rules-references/

Prochaines étapes :
1. Personnaliser les règles dans .claude/skills/rules-references/references/rules.md
2. Lancer /spec new <titre> pour démarrer un spec
3. Utiliser /evolve add <description> pour ajouter des règles domaine-spécifiques au fil du temps
```
