# Phase : Worktree Setup

> Le worktree est déjà créé et activé depuis `/spec new` ou `/spec open`.
> Cette phase se concentre sur le setup du projet et la capture de la baseline de tests.

Automatic phase — no user interaction needed. Report progress in French.

## Process

### Step 1: Project Setup + Baseline (en parallèle si indépendants)

Lancer en parallèle les deux blocs suivants, puis attendre que les deux soient terminés avant de continuer.

**Bloc A — Setup du projet** (dans le répertoire worktree) :
- Détecter le type de projet depuis les fichiers présents :
  - `package.json` → `npm install` ou `yarn install`
  - `composer.json` → `composer install`
  - `pom.xml` → `mvn install -DskipTests`
  - `build.gradle` → `./gradlew build -x test`
- Si aucun fichier de build détecté : passer (projet sans dépendances à installer)

**Bloc B — Pré-chargement du contexte de planning** :
- Lire `requirement.md` et `design.md` depuis `.mahi/specs/<spec-path>/`
- Garder ces contenus en mémoire pour l'injection dans la phase de planning qui suit immédiatement

Une fois le setup terminé :

### Step 2: Capture Test Baseline
Run project test suite and save results:
- Execute the project's test command (detect from package.json/pom.xml/composer.json)
- All tests must pass for baseline to be valid
- Save to `.mahi/specs/<spec-path>/baseline-tests.json`: total, passed, failed, skipped
- If tests fail: report in French, do NOT proceed to planning
- Append log.md entry: "Worktree créé. Baseline capturée : X tests passent."

### Step 3: Fire Event and Report
Appeler :
```
mcp__plugin_mahi_mahi__fire_event(
  workflowId: <lire depuis active.json>,
  event: "APPROVE_WORKTREE"
)
```

> Note mahi : il n'y a pas de mise à jour locale de state.json.
> La transition de phase est gérée par la FSM du serveur Mahi via `mcp__plugin_mahi_mahi__fire_event`.

### Step 4: Report and Transition
"Worktree actif dans `.worktrees/<spec-id>` sur la branche `spec/<username>/<spec-id>`. Baseline : X tests passent. Passage à la phase de planification."

Auto-transition to planning phase.

## Error Handling

| Erreur | Action |
|--------|--------|
| Working tree non propre | "Attention : des modifications non commitées existent sur la branche de base." |
| Tests échouent | Afficher les échecs, rester en phase worktree |
