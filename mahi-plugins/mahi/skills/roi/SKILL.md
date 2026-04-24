---
name: roi
description: "This skill should be used when the user invokes '/roi' to measure time saved with Claude Code, generate a report of completed specs and ADRs, count tests added, documentation generated, and estimate cost efficiency of the spec-driven workflow over a period."
argument-hint: "[--from YYYY-MM-DD] [--to YYYY-MM-DD] [--type spec|adr|all]"
context: fork
allowed-tools: ["Read", "Write", "Glob", "Grep", "Bash"]
---

# Rapport ROI — Retour sur investissement du workflow spec-driven

Read-only reporting. All output in French. Focus on workflow efficiency, not people.

## Arguments

- No args → 30 derniers jours, type `spec`
- `--from YYYY-MM-DD` → depuis cette date
- `--to YYYY-MM-DD` → jusqu'à cette date
- `--type spec|adr|all` → type de workflow à analyser (défaut : `spec`)

## Process

### Step 0 : Parser les arguments

Extraire les valeurs de `--from`, `--to`, `--type` depuis les arguments fournis.

- `--from` absent → date d'aujourd'hui moins 30 jours
- `--to` absent → date d'aujourd'hui
- `--type` absent → `spec`
- `--type all` → inclure specs ET ADR dans l'analyse

### Step 1: Scanner les workflows terminés

Lire `.mahi/registry.json`. Structure : `{ "workflows": [ { "id", "type", "title", "period", "status", "path" }, ... ] }`.

Filtrer les entrées :
- `status == "completed"`
- `type` correspond au filtre `--type` (`spec`, `adr`, ou les deux si `all`)

Pour chaque entrée conservée, charger `<path>/state.json`.

Filtrer : conserver uniquement les workflows où `phases.finishing.approvedAt` tombe dans la période demandée.

**Robustesse :**
- Si `phases.finishing.approvedAt` est absent (spec glissé sans être complètement terminé) → skip silencieux + afficher un warning à la fin : "X workflow(s) ignoré(s) : `approvedAt` manquant."
- Si `phases.finishing.approvedAt` est identique à `createdAt` (timestamps placeholder) → conserver mais noter "durée non disponible (timestamps identiques)" à la place d'une durée calculée.

### Step 2: Extraire les métriques par workflow

#### Pour les SPECS (`type == "spec"`)

**Depuis state.json :**
- `title`, `id`
- `createdAt` → date de début
- `phases.finishing.approvedAt` → date de fin
- Durée = fin - début (si identiques : "durée non disponible (timestamps identiques)")
- `totalTasks`, `totalSubtasks`, `completedSubtasks` (champs top-level dans state.json)
- Sous-tâches échouées = `totalSubtasks - completedSubtasks`
- `branch` → nom de branche pour git diff

**Depuis requirement.md :**
- Compter les items REQ (`nbREQ`)

**Depuis design.md :**
- Compter les items DES (`nbDES`)

**Depuis plan.md :**
- Compter les TASK parents (`nbTASK`) et toutes les sous-tâches (`nbSubtask`)
- Lire chaque définition de sous-tâche (description, fichiers, complexité)

**Depuis baseline-tests.json :**
- `total` → nombre de tests avant implémentation
- `breakingChanges` → tableau, count = longueur
- Si absent : "N/D"

**Tests ajoutés** (via git) :
```bash
git log --oneline <baseBranch>..<branch> --name-only -- "*test*" "*spec*" "*Test*" "*__tests__*" | grep -c "^\."
```
Si la branche n'existe plus (déjà supprimée) → "N/D".

**Depuis reviews/ :**
- Compter les fichiers de rapport (`reviews/*.md`)

**Règles projet ajoutées** (depuis log.md) :
- Lire `<path>/log.md`
- Chercher dans l'entrée de phase retrospective la mention "X règles ajoutées"
- Si absent : "N/D"

**Depuis git** (champ `branch` de state.json) :
```bash
git diff --stat <baseBranch>...<branch> | tail -1
```
→ Fichiers modifiés, insertions, suppressions. Si branche absente : "N/D".

#### Pour les ADR (`type == "adr"`)

**Depuis state.json :**
- `title`, `id`
- `createdAt` → date de début
- `phases.finishing.approvedAt` → date de fin
- Durée = fin - début (si identiques : "durée non disponible (timestamps identiques)")

**Depuis framing.md :**
- Présence du problème, contraintes listées → noter "oui/non"

**Depuis options.md :**
- Compter le nombre d'options analysées (`nbOptions`)

**Depuis adr.md :**
- Présence de la décision finale → "oui/non"

Les ADR n'ont pas de `plan.md`, pas de `baseline-tests.json`, pas de git diff par branche → indiquer "N/D" pour ces métriques.

### Step 3: Estimer le temps sans Claude par phase

Estimate how long each phase would take for an **efficient developer working manually** (optimistic baseline — no onboarding, familiar with the codebase).

#### Specs

**Phase Requirements** : `15 + (nbREQ × 8)` minutes
- Gathering, writing acceptance criteria, stakeholder validation

**Phase Design** : `10 + (nbDES × 10)` minutes
- Architecture decisions, component breakdown, API contracts

**Phase Planning** : `10 + (nbTASK × 5) + (nbSubtask × 2)` minutes
- Task breakdown, sequencing, estimating

**Phase Implementation** — estimate per completed subtask from plan.md:
- **Scope**: number of files to create/modify
- **Complexity**: business logic, API integration, simple CRUD
- **Tests**: writing tests from scratch adds significant time
- **Domain knowledge**: codebase exploration time

  Calibration (optimiste — développeur expérimenté, familier du codebase) :
  - Simple entity/model: ~10 min
  - Service with business logic + tests: ~30-45 min
  - Controller/route with validation: ~20 min
  - Config change or rename: ~5 min
  - Complex integration with multiple deps: ~45-60 min
  - Comprehensive test suite: ~25-40 min

  > **Note :** Ces estimations sont spéculatives — elles représentent un cas favorable et varient fortement selon la complexité du domaine, la dette technique, et la familiarité avec le codebase. Le `tempsEstiméSansClaude` est une borne basse, pas une valeur absolue.

**Phase Finishing/Review** : `20 + (nbSubtask × 3)` minutes
- Manual testing, PR description, code review

**Phase Rétrospective** : 15 minutes (fixe)
- Post-mortem, documenting learnings (often skipped manually — conservative estimate)

#### ADR

**Phase Framing** : 30 minutes (fixe)
- Formaliser le problème, contraintes, non-objectifs

**Phase Exploration** : `20 + (nbOptions × 15)` minutes
- Recherche et analyse de chaque option

**Phase Discussion** : 45 minutes (fixe)
- Comparaison, pondération des critères

**Phase Décision** : 20 minutes (fixe)
- Rédaction formelle de la décision et du contexte

### Step 4: Calculer l'efficacité et la rentabilité

#### Par workflow

- `tempsEstiméSansClaude` = somme des estimations de phases
- `tempsRéel` = durée calculée depuis state.json (ou "N/D" si timestamps identiques)
- `gainTemps` = tempsEstiméSansClaude - tempsRéel (ou "N/D")
- `gainPourcentage` = gainTemps / tempsEstiméSansClaude × 100 (ou "N/D")

#### Rentabilité globale (proratisée sur la période analysée)

**Lire les coûts depuis `.mahi/config.json` section `roi` :**
```json
"roi": {
  "claudeMonthlyCost": 125,
  "devHourlyCost": 21.63
}
```
Si la section est absente → utiliser les défauts : 125€/mois, 21.63€/h.

Calcul :
- Coût Claude Code = claudeMonthlyCost × (nbJoursPériode / 30)
- Coût horaire développeur = devHourlyCost (basé sur 45k€/an, 1607h/an)
- Temps économisé total = somme des gainTemps de tous les workflows (en heures)
- Valeur du temps économisé = tempsÉconomiséHeures × devHourlyCost
- ROI = (valeur temps économisé - coût Claude proraté) / coût Claude proraté × 100

### Step 5: Générer le rapport

```
# Rapport ROI — Workflow spec-driven
> Période : <from> au <to>
> Type : <spec | adr | spec + ADR>
> Généré le : <date>

## Résumé

| Métrique | Valeur |
|----------|--------|
| Workflows complétés | X |
| Specs | S |
| ADR | A |
| Tâches réalisées | T |
| Sous-tâches réalisées | Y |
| Tests ajoutés | +Z |
| Revues de code effectuées | W |
| Fichiers modifiés | F |
| Lignes ajoutées | L |
| Changements cassants documentés | K |

(Masquer les lignes "Specs" et "ADR" si --type n'est pas "all".)

## Économies estimées par phase

> Baseline : dev efficace, familier avec le codebase (estimation optimiste)

| Phase | Estimation sans Claude | Durée avec Claude | Gain |
|-------|----------------------|-------------------|------|
| Requirements (X REQ) | ~XX min | inclus durée totale | ~XX min |
| Design (Y DES) | ~XX min | inclus durée totale | ~XX min |
| Planning (Z tâches) | ~XX min | inclus durée totale | ~XX min |
| Implémentation (N sous-tâches) | ~XX min | inclus durée totale | ~XX min |
| Finishing/Review | ~XX min | inclus durée totale | ~XX min |
| Rétrospective | ~15 min | inclus durée totale | ~XX min |
| **Total** | **~Xh XXmin** | **~Yh YYmin** | **~ZZ%** |

(Adapter les lignes de phases selon le type de workflow — ADR = Framing/Exploration/Discussion/Décision.)

## Efficacité globale

| Métrique | Valeur |
|----------|--------|
| Temps estimé sans Claude (toutes phases) | Xh XXmin |
| Temps réel avec Claude | Yh YYmin |
| Gain de temps estimé | ~ZZ% |

## Rentabilité

| Métrique | Valeur |
|----------|--------|
| Coût Claude Code (proraté période) | XX.XX€ |
| Coût horaire développeur | <devHourlyCost>€/h |
| Temps économisé | Xh XXmin |
| Valeur du temps économisé | XXX.XX€ |
| **ROI** | **+XXX%** |

## Détail par workflow

### <titre> [spec | adr]

(Afficher le tag [spec] ou [adr] uniquement si --type all.)

- **Durée** : <durée> (ou "durée non disponible (timestamps identiques)")
- **Tâches** : X terminées (spec uniquement)
- **Sous-tâches** : X terminées, Y échouées (spec uniquement)
- **Tests ajoutés** : +Z (spec) / N/D (adr)
- **Fichiers modifiés** : N (L lignes) (spec) / N/D (adr)
- **Revues** : W rapports
- **Changements cassants** : K documentés (spec) / N/D (adr)

**Estimation par phase :**

| Phase | Estimation sans Claude |
|-------|----------------------|
| Requirements (X REQ) | ~XX min |
| ... | ... |
| **Total estimé sans Claude** | **~XXX min** |
| **Temps réel avec Claude** | **~XX min** (ou "N/D") |
| **Gain** | **~XX%** (ou "N/D") |

**Détail implémentation (spec uniquement) :**

| Sous-tâche | Estimation sans Claude | Complexité |
|------------|----------------------|------------|
| TASK-001.1 : Créer l'entité | ~10 min | Simple |
| TASK-001.2 : Service + tests | ~35 min | Moyen |

## Qualité produite

| Métrique | Total période |
|----------|---------------|
| Tests ajoutés | +Z |
| Revues de code | W |
| Changements cassants documentés | K |
| Règles projet ajoutées (rétrospectives) | R |

## Observations

- <types de tâches les plus accélérées par le workflow>
- <phases du workflow les plus efficaces>
- <suggestions d'optimisation>
- Documentation générée : X modules (si `.mahi/docs/manifest.json` présent)
- Graphes disponibles : X graphes (si `.mahi/graph/manifest.json` présent)
```

**Vérifier la disponibilité doc/graph** (pour la section Observations) :
- Si `.mahi/docs/manifest.json` existe → lire et compter les modules, ajouter "Documentation générée : X modules"
- Si `.mahi/graph/manifest.json` existe → lire et compter les graphes, ajouter "Graphes disponibles : X graphes"
- Si absent → omettre la ligne

### Step 6: Proposer la sauvegarde

"Sauvegarder le rapport dans `.mahi/reports/roi-<date>.md` ?"

Si oui, créer le répertoire `.mahi/reports/` si absent, puis sauvegarder.
