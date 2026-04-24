---
description: "Release Mahi : bump version, build, commit/tag/push"
allowed-tools: ["Read", "Bash", "Edit", "AskUserQuestion", "TodoWrite"]
---

# Commande /release

Orchestre le processus complet de release du plugin Mahi : bump de version,
build du jar, commit + tag git, push remote.

**Réservé au mainteneur.** Doit être exécuté depuis la branche `main`.

---

## Constantes

- **Fichiers de version** (tous alignés sur la même valeur) :
  - `mahi-mcp/build.gradle.kts`
  - `mahi-mcp/src/main/resources/application.properties`
  - `mahi-plugins/mahi/.claude-plugin/plugin.json`
  - `mahi-plugins/mahi-codebase/.claude-plugin/plugin.json`
  - `mahi-plugins/mahi-roi/.claude-plugin/plugin.json`
  - `.claude-plugin/marketplace.json`

- **Jar produit** : `mahi-plugins/mahi/mahi-mcp-server.jar`

- **Placeholders** : `${V_CURRENT}` (version courante stable, ex. `0.1.1`),
  `${V_RELEASE}` (version cible, ex. `0.1.2`).

- **Note** : pas de suffixe `-SNAPSHOT` dans ce projet. Entre deux releases,
  les 6 fichiers contiennent la même version stable (celle de la dernière
  release publiée). Les plugins `mahi`, `mahi-codebase` et `mahi-roi` partagent
  toujours la même version.

---

## Étape 1 : Pré-conditions

Appeler `TodoWrite(todos=[
  {content: "Vérifier les pré-conditions", status: "in_progress", activeForm: "Vérification des pré-conditions"},
  {content: "Saisir et confirmer la version cible", status: "pending", activeForm: "Saisie de la version cible"},
  {content: "Mettre à jour les 6 fichiers de version", status: "pending", activeForm: "Mise à jour des fichiers de version"},
  {content: "Builder le jar Gradle", status: "pending", activeForm: "Build Gradle"},
  {content: "Commit, tag et push vers le remote", status: "pending", activeForm: "Commit, tag et push"}
])`.

### 1.1 — Branche courante

Exécuter `git branch --show-current`.

Si différent de `main` → afficher `"Release impossible : branche courante '<nom>' (attendu : main)."` et stopper.

### 1.2 — Propreté du repo

Exécuter `git status --porcelain`.

Si la sortie est non vide :
1. Afficher la liste des fichiers.
2. Appeler `AskUserQuestion(question="Des fichiers non commités sont présents. Continuer la release quand même ?", header="Repo sale", multiSelect=false, options=[{label: "Annuler", description: "Stopper proprement, aucune modification"}, {label: "Continuer", description: "Procéder malgré les fichiers non commités"}])`.
3. Si "Annuler" → stopper. Si "Continuer" → poursuivre.

### 1.3 — Confirmation

Afficher `"Pré-conditions OK — prêt pour la release."`

---

## Étape 2 : Version cible

Appeler `TodoWrite(todos=[
  {content: "Vérifier les pré-conditions", status: "completed", activeForm: "Vérification des pré-conditions"},
  {content: "Saisir et confirmer la version cible", status: "in_progress", activeForm: "Saisie de la version cible"},
  {content: "Mettre à jour les 6 fichiers de version", status: "pending", activeForm: "Mise à jour des fichiers de version"},
  {content: "Builder le jar Gradle", status: "pending", activeForm: "Build Gradle"},
  {content: "Commit, tag et push vers le remote", status: "pending", activeForm: "Commit, tag et push"}
])`.

### 2.1 — Lire la version courante

Extraire `version = "..."` de `mahi-mcp/build.gradle.kts` → `${V_CURRENT}`.

### 2.2 — Calculer les variantes

Depuis `${V_CURRENT}` (format `X.Y.Z`) :

- `${V_PATCH}` = `X.Y.(Z+1)`
- `${V_MINOR}` = `X.(Y+1).0`
- `${V_MAJOR}` = `(X+1).0.0`

### 2.3 — Proposer le bump

Appeler `AskUserQuestion(question="Version courante : ${V_CURRENT}. Quel type de release ?", header="Bump", multiSelect=false, options=[{label: "Patch → ${V_PATCH} (Recommended)", description: "Correctifs uniquement (Z+1)"}, {label: "Minor → ${V_MINOR}", description: "Features rétrocompatibles (Y+1, Z=0)"}, {label: "Major → ${V_MAJOR}", description: "Breaking changes (X+1, Y=0, Z=0)"}])`.

**Note** : Claude Code ajoute automatiquement une option "Other" pour saisie libre.

### 2.4 — Résoudre `${V_RELEASE}`

Selon la réponse :

- Label "Patch…" → `${V_RELEASE}` = `${V_PATCH}`
- Label "Minor…" → `${V_RELEASE}` = `${V_MINOR}`
- Label "Major…" → `${V_RELEASE}` = `${V_MAJOR}`
- Texte libre (réponse "Other") → valider avec regex `^\d+\.\d+\.\d+$` :
  - Invalide → re-appeler `AskUserQuestion(question="Format invalide (attendu X.Y.Z). Version cible ?", header="Bump", multiSelect=false, options=[{label: "Patch → ${V_PATCH} (Recommended)", description: "Correctifs uniquement (Z+1)"}, {label: "Minor → ${V_MINOR}", description: "Features rétrocompatibles (Y+1, Z=0)"}, {label: "Major → ${V_MAJOR}", description: "Breaking changes (X+1, Y=0, Z=0)"}])`. Reboucler une seule fois ; deux échecs successifs → stopper avec `"Format invalide persistant, release annulée."`
  - Valide → `${V_RELEASE}` = la valeur saisie

### 2.5 — Confirmation

Appeler `AskUserQuestion(question="Release v${V_RELEASE} — confirmer ?", header="Release", multiSelect=false, options=[{label: "Annuler", description: "Stopper, aucun fichier modifié"}, {label: "Confirmer", description: "Procéder à la release v${V_RELEASE}"}])`.

Si "Annuler" → stopper.

---

## Étape 3 : Mise à jour atomique des fichiers

Appeler `TodoWrite(todos=[
  {content: "Vérifier les pré-conditions", status: "completed", activeForm: "Vérification des pré-conditions"},
  {content: "Saisir et confirmer la version cible", status: "completed", activeForm: "Saisie de la version cible"},
  {content: "Mettre à jour les 6 fichiers de version", status: "in_progress", activeForm: "Mise à jour des fichiers de version"},
  {content: "Builder le jar Gradle", status: "pending", activeForm: "Build Gradle"},
  {content: "Commit, tag et push vers le remote", status: "pending", activeForm: "Commit, tag et push"}
])`.

### 3.1 — Vérifier (lecture seule)

Lire les 6 fichiers en parallèle. Chacun doit contenir exactement `${V_CURRENT}` :

- `mahi-mcp/build.gradle.kts` : `version = "${V_CURRENT}"`
- `mahi-mcp/src/main/resources/application.properties` : `spring.ai.mcp.server.version=${V_CURRENT}`
- `mahi-plugins/mahi/.claude-plugin/plugin.json` : `"version": "${V_CURRENT}"`
- `mahi-plugins/mahi-codebase/.claude-plugin/plugin.json` : `"version": "${V_CURRENT}"`
- `mahi-plugins/mahi-roi/.claude-plugin/plugin.json` : `"version": "${V_CURRENT}"`
- `.claude-plugin/marketplace.json` : `"version": "${V_CURRENT}"` dans les entrées `mahi`, `mahi-codebase` ET `mahi-roi`

Si l'un diverge → `"Incohérence dans <fichier> : attendu ${V_CURRENT} trouvé <Y>. Release annulée, aucun fichier modifié."` et stopper.

### 3.2 — Écrire

Via `Edit` :

1. `build.gradle.kts` : `version = "${V_CURRENT}"` → `version = "${V_RELEASE}"`
2. `mahi-mcp/src/main/resources/application.properties` : `spring.ai.mcp.server.version=${V_CURRENT}` → `spring.ai.mcp.server.version=${V_RELEASE}`
3. `mahi-plugins/mahi/.claude-plugin/plugin.json` : `"version": "${V_CURRENT}"` → `"version": "${V_RELEASE}"`
4. `mahi-plugins/mahi-codebase/.claude-plugin/plugin.json` : `"version": "${V_CURRENT}"` → `"version": "${V_RELEASE}"`
5. `mahi-plugins/mahi-roi/.claude-plugin/plugin.json` : `"version": "${V_CURRENT}"` → `"version": "${V_RELEASE}"`
6. `marketplace.json` (entrées `mahi`, `mahi-codebase` ET `mahi-roi`) : `"version": "${V_CURRENT}"` → `"version": "${V_RELEASE}"`

**Note** : en cas d'erreur, `git restore .` annule tout.

### 3.3 — Confirmation

Afficher `"6 fichiers de version mis à jour → ${V_RELEASE}."`

---

## Étape 4 : Build Gradle

Appeler `TodoWrite(todos=[
  {content: "Vérifier les pré-conditions", status: "completed", activeForm: "Vérification des pré-conditions"},
  {content: "Saisir et confirmer la version cible", status: "completed", activeForm: "Saisie de la version cible"},
  {content: "Mettre à jour les 6 fichiers de version", status: "completed", activeForm: "Mise à jour des fichiers de version"},
  {content: "Builder le jar Gradle", status: "in_progress", activeForm: "Build Gradle"},
  {content: "Commit, tag et push vers le remote", status: "pending", activeForm: "Commit, tag et push"}
])`.

### 4.1 — Détecter l'OS

Exécuter `uname -s 2>/dev/null`.

- `MINGW*` / `CYGWIN*` ou commande échoue → `windows`
- `Linux*` → `linux`
- `Darwin*` → `macos`

### 4.2 — Builder

Selon l'OS :

- Windows : `powershell.exe -Command "Set-Location mahi-mcp; .\gradlew.bat bootJar"`
- Unix/macOS : `cd mahi-mcp && ./gradlew bootJar`

### 4.3 — Traiter le résultat

**Succès** (exit 0) → passer à 4.4.

**Échec** (exit ≠ 0) :
1. Afficher la sortie Gradle complète.
2. Appeler `AskUserQuestion(question="Build Gradle échoué. Que faire ?", header="Build KO", multiSelect=false, options=[{label: "Réessayer", description: "Relancer après correction"}, {label: "Annuler", description: "Stopper (git restore . pour annuler les modifs)"}])`.
3. Si "Réessayer" → relancer 4.2. Si "Annuler" → stopper en rappelant `git restore .`.

### 4.4 — Vérifier le jar

Exécuter `ls -la mahi-plugins/mahi/mahi-mcp-server.jar` → confirmer présence + mtime récent (< 5 min).

Si absent ou vieux → `"Build réussi mais jar absent/périmé. Release annulée."` et stopper.

### 4.5 — Confirmation

Afficher `"Build OK — jar à jour."`

---

## Étape 5 : Commit, tag, push

Appeler `TodoWrite(todos=[
  {content: "Vérifier les pré-conditions", status: "completed", activeForm: "Vérification des pré-conditions"},
  {content: "Saisir et confirmer la version cible", status: "completed", activeForm: "Saisie de la version cible"},
  {content: "Mettre à jour les 6 fichiers de version", status: "completed", activeForm: "Mise à jour des fichiers de version"},
  {content: "Builder le jar Gradle", status: "completed", activeForm: "Build Gradle"},
  {content: "Commit, tag et push vers le remote", status: "in_progress", activeForm: "Commit, tag et push"}
])`.

### 5.1 — Stager

```bash
git add mahi-mcp/build.gradle.kts \
        mahi-mcp/src/main/resources/application.properties \
        mahi-plugins/mahi/.claude-plugin/plugin.json \
        mahi-plugins/mahi-codebase/.claude-plugin/plugin.json \
        mahi-plugins/mahi-roi/.claude-plugin/plugin.json \
        mahi-plugins/mahi/mahi-mcp-server.jar \
        .claude-plugin/marketplace.json
```

### 5.2 — Committer

Exécuter `git commit -m "chore(release): release v${V_RELEASE}"`.

Si échec → afficher l'erreur + `"Modifications staged. git restore . + git reset pour annuler si besoin."` et stopper.

### 5.3 — Tagger

Exécuter `git tag -a "v${V_RELEASE}" -m "Release v${V_RELEASE}"`.

Si échec → afficher l'erreur + `"Commit créé, tag à créer manuellement : git tag -a v${V_RELEASE} -m 'Release v${V_RELEASE}'"` et stopper.

### 5.4 — Pousser

Exécuter `git push origin main --tags`.

Si échec → afficher l'erreur + `"Commit et tag locaux OK. Relancer manuellement : git push origin main --tags"` et stopper.

### 5.5 — Confirmation finale

Lire le hash : `git log --oneline -1`.

Afficher `"✅ Release v${V_RELEASE} publiée (commit <hash>, tag v${V_RELEASE})."`

Appeler `TodoWrite(todos=[
  {content: "Vérifier les pré-conditions", status: "completed", activeForm: "Vérification des pré-conditions"},
  {content: "Saisir et confirmer la version cible", status: "completed", activeForm: "Saisie de la version cible"},
  {content: "Mettre à jour les 6 fichiers de version", status: "completed", activeForm: "Mise à jour des fichiers de version"},
  {content: "Builder le jar Gradle", status: "completed", activeForm: "Build Gradle"},
  {content: "Commit, tag et push vers le remote", status: "completed", activeForm: "Commit, tag et push"}
])`.

Afficher : **"Release terminée."**