# Design : release — Script de release cross-plateforme pour la marketplace Mahi

## Contexte

**Correction des chemins (vs requirements) :** L'exploration du codebase révèle que le plugin Mahi est organisé ainsi :
- `mahi-plugins/mahi/` — répertoire du plugin (et non `mahi-plugins/mahi-workflow/` comme indiqué dans les requirements)
- `mahi-plugins/mahi/.claude-plugin/plugin.json` — métadonnées du plugin
- `mahi-plugins/mahi/mahi-mcp-server.jar` — fat jar (copié automatiquement par la tâche `bootJar`)
- `.claude-plugin/marketplace.json` — registre marketplace
- `mahi-mcp/build.gradle.kts` — version courante : `0.1.1-SNAPSHOT`

La tâche `bootJar` copie le jar dans `${projectDir}/../mahi-plugins/mahi` (confirmé ligne 46 de `build.gradle.kts`).

**Contrainte connue :** Sur Windows, Gradle doit être invoqué via PowerShell (`powershell.exe`) car bash échoue si `JAVA_HOME` contient des espaces.

---

## DES-001 : Structure de la slash command `/release`

**Problème :** Définir le fichier `.claude/commands/release.md` avec la structure standard des slash commands Claude Code, accessible uniquement dans ce repo.

**Approche retenue :** Slash command Markdown avec frontmatter `allowed-tools` restreint aux outils nécessaires. La commande est découpée en 6 étapes séquentielles avec points de contrôle explicites. Pas de worktree — la commande s'exécute sur `main` directement.

**Fichier cible :** `.claude/commands/release.md`

**Frontmatter :**

```yaml
---
description: "Script de release : bump version, build Gradle, commit+tag git, push, bump SNAPSHOT"
allowed-tools: ["Read", "Bash", "Edit"]
---
```

**Séquence des 6 étapes :**

1. Vérification des pré-conditions (DES-002)
2. Saisie et validation de la version cible (DES-003)
3. Vérification et mise à jour atomique des fichiers de version (DES-004)
4. Build Gradle cross-plateforme (DES-005)
5. Commit, tag git et push (DES-006)
6. Bump SNAPSHOT post-release (DES-007)

Chaque étape affiche son statut en début et en fin. En cas d'erreur, l'étape affiche l'état courant et les actions éventuellement déjà réalisées.

**SOLID :**
- S : chaque étape a une responsabilité unique — aucune étape ne mélange vérification, build et git
- O : l'ajout d'une étape future ne modifie pas les étapes existantes

**Implémente :** REQ-NF-001

**Contrat de test :**
- Le fichier `.claude/commands/release.md` existe à la racine du repo
- Il n'est pas référencé dans `plugin.json` ni `marketplace.json`
- `/release` est disponible dans Claude Code à la racine du repo

---

## DES-002 : Vérification des pré-conditions

**Problème :** Garantir que la release est lancée depuis un état cohérent du repo avant toute modification.

**Approche retenue :** Trois vérifications séquentielles avec arrêt immédiat sur échec, suivies d'une confirmation optionnelle si le repo contient des fichiers non commités.

**Séquence :**

1. Lire la branche courante via `git branch --show-current`
   - Si ≠ `main` → afficher "Release impossible : vous n'êtes pas sur la branche `main` (branche courante : <nom>)." et stopper
2. Tenter de lire `.sdd/local/active.json`
   - Si le fichier existe → afficher "Release impossible : un spec ou ADR est actuellement actif. Fermez-le d'abord." et stopper
3. Lire `git status --porcelain`
   - Si résultat non vide → afficher la liste des fichiers modifiés et demander confirmation :
     "Des fichiers non commités sont présents. Continuer quand même ? (oui / annuler)"
   - Si l'utilisateur répond "annuler" → stopper proprement
4. Afficher "Pré-conditions vérifiées — prêt pour la release."

**Justification :** La vérification des fichiers sales est non bloquante (l'utilisateur peut avoir des fichiers non liés à la release). La confirmation explicite évite toute surprise.

**SOLID :**
- S : cette étape ne fait que vérifier — aucune modification de fichier ou d'état git

**Implémente :** REQ-001, REQ-NF-003 (AC-3)

**Contrat de test :**
- Branche ≠ main → message de refus, aucun fichier modifié
- `active.json` présent → message de refus, aucun fichier modifié
- Repo sale + confirmation "annuler" → arrêt propre, aucune modification
- Repo propre sur main sans active.json → message de succès, passage à l'étape suivante

---

## DES-003 : Saisie et validation de la version cible

**Problème :** Calculer la version par défaut, proposer des variantes (major/minor/patch), valider le format et obtenir une confirmation explicite avant toute modification.

**Approche retenue :** Lecture de `build.gradle.kts`, calcul de la version par défaut, boucle de saisie avec validation de format, confirmation finale.

**Algorithme de calcul des variantes :**

```
version lue dans build.gradle.kts : "0.1.1-SNAPSHOT"
→ strip "-SNAPSHOT"         → "0.1.1"
→ PATCH + 1                 → "0.1.2"   ← version patch (défaut)
→ MINOR + 1, PATCH = 0     → "0.2.0"   ← si l'utilisateur saisit "minor"
→ MAJOR + 1, MINOR = PATCH = 0 → "1.0.0" ← si l'utilisateur saisit "major"
```

**Séquence :**

1. Lire `mahi-mcp/build.gradle.kts` — extraire la ligne `version = "X.Y.Z-SNAPSHOT"`
2. Calculer les 3 variantes (patch, minor, major)
3. Demander via `AskUserQuestion` :
   ```
   Version courante : 0.1.1-SNAPSHOT
   Version pour cette release [0.1.2] ?
   (Entrée = patch par défaut | major | minor | patch | ou saisir X.Y.Z directement)
   ```
4. Si réponse vide → utiliser la version patch par défaut
5. Si réponse est "major", "minor" ou "patch" → calculer la version correspondante et redemander confirmation
6. Si réponse est une chaîne → valider le format `^\d+\.\d+\.\d+$`
   - Format invalide → message d'erreur et retour à l'étape 3
7. Afficher récapitulatif et demander confirmation :
   "Release `v<version>` — confirmer ? (oui / annuler)"
   - Si "annuler" → stopper proprement

**SOLID :**
- S : cette étape ne fait que recueillir et valider la version — aucune écriture de fichier

**Implémente :** REQ-002

**Contrat de test :**
- Entrée vide → version patch+1 utilisée
- Saisie "minor" → version minor calculée, confirmation demandée à nouveau
- Saisie "1.2.3" → acceptée si format valide
- Saisie "1.2" ou "abc" → rejetée, message d'erreur, redemande
- Réponse "annuler" à la confirmation → arrêt propre, aucun fichier modifié

---

## DES-004 : Mise à jour atomique des fichiers de version

**Problème :** Mettre à jour les 3 fichiers de version de façon sûre sans laisser le repo dans un état partiellement modifié en cas d'anomalie.

**Approche retenue — Option A (vérification stricte en amont) :**
Avant toute écriture, lire les 3 fichiers et vérifier que chacun contient la version attendue. Si l'un échoue → arrêt immédiat, aucune modification.

**Fichiers concernés (chemins corrigés vs requirements) :**

| Fichier | Champ | Contenu attendu avant release |
|---------|-------|-------------------------------|
| `mahi-mcp/build.gradle.kts` | ligne `version = "..."` | `version = "<courante>-SNAPSHOT"` |
| `mahi-plugins/mahi/.claude-plugin/plugin.json` | champ `"version"` | `"<courante sans SNAPSHOT>"` |
| `.claude-plugin/marketplace.json` | champ `"version"` de l'entrée `"mahi"` | `"<courante sans SNAPSHOT>"` |

**Séquence :**

1. **Phase de vérification** (lectures en parallèle via Read) :
   - Vérifier que `build.gradle.kts` contient `version = "<courante>-SNAPSHOT"`
   - Vérifier que `plugin.json` a `"version": "<courante sans SNAPSHOT>"`
   - Vérifier que `marketplace.json` a `"version": "<courante sans SNAPSHOT>"` pour l'entrée `"mahi"`
   - Si l'un échoue → afficher "Incohérence dans <fichier> : version attendue <X> mais trouvée <Y>. Release annulée." et stopper

2. **Phase de mise à jour** (séquentielle via Edit) :
   - `build.gradle.kts` : remplacer `version = "<courante>-SNAPSHOT"` par `version = "<cible>"`
   - `plugin.json` : remplacer `"version": "<courante>"` par `"version": "<cible>"`
   - `marketplace.json` : remplacer `"version": "<courante>"` dans l'entrée `mahi` par `"version": "<cible>"`

3. Afficher "Versions mises à jour dans les 3 fichiers."

**Note sécurité :** Si une erreur survient pendant l'écriture, `git restore .` permet de restaurer l'état initial (REQ-NF-003.AC-1).

**SOLID :**
- S : cette étape ne fait que les mises à jour de version — pas de build, pas de git
- O : ajouter un 4e fichier de version = ajouter une vérification et une mise à jour sans modifier les existantes

**Implémente :** REQ-003, REQ-NF-003 (AC-1, AC-3)

**Contrat de test :**
- Version manquante dans un fichier → arrêt avant toute écriture, message explicite
- Fichier absent → arrêt avant toute écriture, message explicite
- Les 3 fichiers cohérents → tous mis à jour avec la version cible, aucun autre fichier touché

---

## DES-005 : Build Gradle cross-plateforme

**Problème :** Déclencher le build `bootJar` en détectant automatiquement l'OS pour choisir la bonne commande, en gérant la contrainte JAVA_HOME sur Windows.

**Approche retenue — Détection OS via `uname` + PowerShell sur Windows :**

La détection se fait via `uname -s 2>/dev/null`. Sur Windows avec Git Bash, `uname -s` renvoie une valeur contenant "MINGW" ou "CYGWIN". Sur Linux, elle renvoie "Linux". Sur macOS, "Darwin".

Sur Windows, la commande utilise PowerShell pour éviter les problèmes de JAVA_HOME avec espaces (retour d'expérience du projet).

**Commandes par OS :**

```
Windows (MINGW/CYGWIN détecté) :
  powershell.exe -Command "Set-Location mahi-mcp; .\gradlew.bat bootJar"

Unix/macOS (Linux/Darwin) :
  cd mahi-mcp && ./gradlew bootJar
```

**Séquence :**

1. Exécuter `uname -s 2>/dev/null` pour détecter l'OS
2. Choisir la commande appropriée (Windows → PowerShell, Unix → bash)
3. Exécuter via `Bash`
4. Si exit code ≠ 0 → afficher la sortie d'erreur Gradle et demander :
   "Le build a échoué. Corriger l'erreur et réessayer, ou annuler la release ? (réessayer / annuler)"
5. Si succès → vérifier que `mahi-plugins/mahi/mahi-mcp-server.jar` existe et a été mis à jour
6. Afficher "Build réussi — jar mis à jour dans `mahi-plugins/mahi/`."

**Justification PowerShell :** Plus fiable que bash sur Windows pour les chemins avec espaces, sans configuration supplémentaire de l'utilisateur.

**SOLID :**
- S : cette étape ne fait que le build — pas de modification de fichiers de version, pas d'opération git
- O : ajouter le support d'une nouvelle plateforme = ajouter un cas de détection sans modifier les existants

**Implémente :** REQ-004, REQ-NF-002

**Contrat de test :**
- Sur Windows (MINGW) : commande PowerShell utilisée avec `gradlew.bat`
- Sur Linux/macOS : `./gradlew bootJar` utilisé
- Build échoué → message d'erreur + confirmation pour réessayer ou annuler
- Build réussi → jar vérifié dans `mahi-plugins/mahi/`

---

## DES-006 : Commit, tag git et push

**Problème :** Créer le commit de release, le tag annoté, et pousser vers le remote de façon traçable, avec une gestion claire des erreurs à chaque sous-étape.

**Approche retenue :** Staging explicite des 4 fichiers par nom, commit avec message conventionnel, tag annoté, push avec tags. Les erreurs sont reportées sans bloquer les informations sur les étapes déjà réalisées.

**Séquence :**

1. Stager les 4 fichiers explicitement (pas `git add .`) :
   - `git add mahi-mcp/build.gradle.kts`
   - `git add mahi-plugins/mahi/.claude-plugin/plugin.json`
   - `git add mahi-plugins/mahi/mahi-mcp-server.jar`
   - `git add .claude-plugin/marketplace.json`
2. Créer le commit : `git commit -m "chore(release): release v<version>"`
3. Créer le tag annoté : `git tag -a "v<version>" -m "Release v<version>"`
4. Pousser : `git push origin main --tags`
5. Lire le hash court : `git log --oneline -1`
6. Afficher : "Release `v<version>` publiée (commit `<hash>`, tag `v<version>`)."

**Gestion des erreurs :**
- Échec commit ou tag → afficher l'erreur + "Les fichiers ont déjà été modifiés. Utilisez `git restore .` pour annuler si nécessaire."
- Échec push → afficher l'erreur + "Le commit et le tag ont été créés localement. Rejouez `git push origin main --tags` manuellement."

**SOLID :**
- S : cette étape ne fait que les opérations git — pas de build, pas de modification de version
- I : staging fichier par fichier plutôt que `git add .` pour éviter d'inclure des fichiers non prévus

**Implémente :** REQ-005, REQ-NF-003 (AC-2)

**Contrat de test :**
- Commit créé avec le message exact `chore(release): release v<version>`
- Tag annoté `v<version>` présent dans `git tag -l`
- Push réussi → message de confirmation avec hash et tag affiché
- Push échoué → message informatif clair, aucun re-essai automatique

---

## DES-007 : Bump SNAPSHOT post-release

**Problème :** Remettre `build.gradle.kts` en version de développement après la release pour que les commits suivants ne repartent pas sur la version publiée.

**Approche retenue :** Modification unique de `build.gradle.kts` (PATCH+1 + `-SNAPSHOT`), commit dédié. `plugin.json` et `marketplace.json` ne sont pas touchés — ils conservent la version de release comme référence stable pour les installateurs.

**Algorithme :**

```
version release  : "0.1.2"
→ PATCH + 1      → "0.1.3"
→ + "-SNAPSHOT"  → "0.1.3-SNAPSHOT"
```

**Séquence :**

1. Calculer la version SNAPSHOT suivante (PATCH de la version release + 1)
2. Modifier `build.gradle.kts` : remplacer `version = "<version release>"` par `version = "<patch+1>-SNAPSHOT"`
3. Stager et committer :
   ```
   git add mahi-mcp/build.gradle.kts
   git commit -m "chore(release): bump to <patch+1>-SNAPSHOT"
   ```
4. Si échec → afficher "Bump SNAPSHOT échoué : <erreur>. Effectuez-le manuellement." — ne pas bloquer la release déjà publiée
5. Si succès → afficher "Version de développement : `<patch+1>-SNAPSHOT`"

**Justification :** `plugin.json` et `marketplace.json` conservent la version de release — c'est la source de vérité pour les utilisateurs qui installent le plugin. Seul `build.gradle.kts` repasse en SNAPSHOT pour signaler l'état de développement du code source.

**SOLID :**
- S : cette étape ne touche qu'un seul fichier pour un seul objectif — le marqueur de version de développement

**Implémente :** REQ-006

**Contrat de test :**
- `build.gradle.kts` contient `version = "<patch+1>-SNAPSHOT"` après exécution
- `plugin.json` inchangé (conserve la version de release)
- `marketplace.json` inchangé (conserve la version de release)
- Échec du bump → message informatif uniquement, release déjà publiée intacte

---

## Couverture des exigences

| Exigence | DES couvrant | Statut |
|----------|-------------|--------|
| REQ-001 — Vérification des pré-conditions | DES-002 | ✅ |
| REQ-002 — Saisie et validation de la version | DES-003 | ✅ |
| REQ-003 — Mise à jour des fichiers de version | DES-004 | ✅ |
| REQ-004 — Build du jar via Gradle | DES-005 | ✅ |
| REQ-005 — Commit et tag git | DES-006 | ✅ |
| REQ-006 — Bump SNAPSHOT post-release | DES-007 | ✅ |
| REQ-NF-001 — Slash command du repo | DES-001 | ✅ |
| REQ-NF-002 — Cross-plateforme Windows/Unix | DES-005 | ✅ |
| REQ-NF-003 — Idempotence et sécurité | DES-002, DES-004, DES-006 | ✅ |
