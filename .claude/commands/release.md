---
description: "Script de release : bump version, build Gradle, commit+tag git, push, bump SNAPSHOT"
allowed-tools: ["Read", "Bash", "Edit"]
---

# Commande /release

Cette commande orchestre le processus complet de release du plugin Mahi :
bump de version, build du jar, commit + tag git, push remote, et remise en SNAPSHOT.

**Réservé au mainteneur.** Doit être exécuté depuis la branche `main`.

---

## Étape 1 : Vérification des pré-conditions

Vérifier les pré-conditions dans cet ordre avant toute modification :

**1.1 — Vérifier la branche courante**

Exécuter :
```bash
git branch --show-current
```

Si le résultat est différent de `main`, afficher le message suivant et stopper immédiatement :

> "Release impossible : vous n'êtes pas sur la branche `main` (branche courante : <nom>)."

**1.2 — Vérifier l'absence de spec ou ADR actif**

Tenter de lire le fichier `.mahi/local/active.json` via Read.

Si le fichier existe, afficher le message suivant et stopper immédiatement :

> "Release impossible : un spec ou ADR est actuellement actif. Fermez-le d'abord."

**1.3 — Vérifier la propreté du repo git**

Exécuter :
```bash
git status --porcelain
```

Si la sortie est non vide :
- Afficher la liste des fichiers modifiés
- Demander à l'utilisateur : "Des fichiers non commités sont présents. Continuer quand même ? (oui / annuler)"
- Si la réponse est "annuler" → stopper proprement sans modification

**1.4 — Confirmation de succès**

Afficher : "Pré-conditions vérifiées — prêt pour la release."

## Étape 2 : Saisie et validation de la version cible

**2.1 — Lire la version courante**

Lire `mahi-mcp/build.gradle.kts` et extraire la ligne contenant `version = "..."`.

Exemple : `version = "0.1.1-SNAPSHOT"` → version courante : `0.1.1-SNAPSHOT`.

**2.2 — Calculer les 3 variantes**

À partir de la version courante (après suppression du suffixe `-SNAPSHOT`) :
- **patch** (défaut) : incrémenter PATCH de 1 — ex. `0.1.1` → `0.1.2`
- **minor** : incrémenter MINOR de 1, remettre PATCH à 0 — ex. `0.1.1` → `0.2.0`
- **major** : incrémenter MAJOR de 1, remettre MINOR et PATCH à 0 — ex. `0.1.1` → `1.0.0`

**2.3 — Demander la version cible**

Demander à l'utilisateur (en affichant la version courante et la version patch par défaut) :

```
Version courante : 0.1.1-SNAPSHOT
Version pour cette release [0.1.2] ?
(Entrée = patch par défaut | major | minor | patch | ou saisir X.Y.Z directement)
```

**2.4 — Traiter la réponse**

- Réponse **vide** → utiliser la version patch par défaut
- Réponse **"major"**, **"minor"** ou **"patch"** → calculer la version correspondante, puis retourner à l'étape 2.3 en proposant cette nouvelle version comme défaut
- Autre réponse → valider le format avec le pattern `^\d+\.\d+\.\d+$`
  - Format invalide → afficher "Format invalide. Utilisez le format X.Y.Z (ex. 1.2.3)." et retourner à 2.3
  - Format valide → continuer

**2.5 — Demander confirmation**

Afficher :

> "Release `v<version>` — confirmer ? (oui / annuler)"

Si la réponse est "annuler" → stopper proprement, aucun fichier modifié.

## Étape 3 : Vérification et mise à jour atomique des fichiers de version

**Stratégie : vérification stricte en amont — aucune écriture si un fichier échoue.**

**3.1 — Phase de vérification (lire les 3 fichiers)**

Lire simultanément les 3 fichiers suivants :
- `mahi-mcp/build.gradle.kts`
- `mahi-plugins/mahi/.claude-plugin/plugin.json`
- `.claude-plugin/marketplace.json`

Vérifications à effectuer :
- `build.gradle.kts` doit contenir la ligne `version = "<version-courante>-SNAPSHOT"` (ex. `version = "0.1.1-SNAPSHOT"`)
- `plugin.json` doit avoir le champ `"version": "<version-courante-sans-snapshot>"` (ex. `"version": "0.1.1"`)
- `marketplace.json` doit avoir le champ `"version": "<version-courante-sans-snapshot>"` dans l'entrée correspondant à `mahi`

Si l'un des 3 fichiers n'existe pas ou ne contient pas la valeur attendue :
- Afficher : "Incohérence dans <fichier> : version attendue <X> mais trouvée <Y>. Release annulée."
- Stopper immédiatement **sans modifier aucun fichier**

**3.2 — Phase de mise à jour (écriture séquentielle)**

Une fois les 3 fichiers validés, effectuer les modifications :

1. Dans `mahi-mcp/build.gradle.kts` : remplacer `version = "<courante>-SNAPSHOT"` par `version = "<cible>"`
2. Dans `mahi-plugins/mahi/.claude-plugin/plugin.json` : remplacer `"version": "<courante>"` par `"version": "<cible>"`
3. Dans `.claude-plugin/marketplace.json` : remplacer `"version": "<courante>"` (dans l'entrée `mahi`) par `"version": "<cible>"`

**Note sécurité :** En cas d'erreur pendant l'écriture, `git restore .` permet de restaurer l'état initial.

**3.3 — Confirmation**

Afficher : "Versions mises à jour dans les 3 fichiers (`build.gradle.kts`, `plugin.json`, `marketplace.json`)."

## Étape 4 : Build Gradle cross-plateforme

**4.1 — Détecter l'OS automatiquement**

Exécuter :
```bash
uname -s 2>/dev/null
```

- Si la sortie contient `MINGW` ou `CYGWIN` (ou si la commande échoue) → **Windows**
- Si la sortie contient `Linux` → **Linux**
- Si la sortie contient `Darwin` → **macOS**

**4.2 — Exécuter le build**

Selon l'OS détecté :

**Windows :**
```bash
powershell.exe -Command "Set-Location mahi-mcp; .\gradlew.bat bootJar"
```

**Unix/macOS :**
```bash
cd mahi-mcp && ./gradlew bootJar
```

**4.3 — Gérer l'échec du build**

Si le build échoue (exit code ≠ 0) :
- Afficher la sortie d'erreur Gradle complète
- Demander à l'utilisateur : "Le build a échoué. Corriger l'erreur et réessayer, ou annuler la release ? (réessayer / annuler)"
  - "réessayer" → retourner à l'étape 4.2
  - "annuler" → stopper (les fichiers de version ont déjà été modifiés — utiliser `git restore .` pour annuler)

**4.4 — Vérifier le jar**

Si le build réussit, vérifier que le fichier `mahi-plugins/mahi/mahi-mcp-server.jar` existe et a bien été mis à jour (date de modification récente).

**4.5 — Confirmation**

Afficher : "Build réussi — jar mis à jour dans `mahi-plugins/mahi/`."

## Étape 5 : Commit, tag git et push

**5.1 — Stager les 4 fichiers modifiés**

```bash
git add mahi-mcp/build.gradle.kts
git add mahi-plugins/mahi/.claude-plugin/plugin.json
git add mahi-plugins/mahi/mahi-mcp-server.jar
git add .claude-plugin/marketplace.json
```

**5.2 — Créer le commit de release**

```bash
git commit -m "chore(release): release v<version>"
```

Si le commit échoue :
- Afficher l'erreur
- Afficher : "Les fichiers ont déjà été modifiés. Utilisez `git restore .` pour annuler si nécessaire."
- Stopper

**5.3 — Créer le tag annoté**

```bash
git tag -a "v<version>" -m "Release v<version>"
```

Si le tag échoue :
- Afficher l'erreur
- Afficher : "Le commit a été créé mais le tag a échoué. Créez-le manuellement : `git tag -a v<version> -m 'Release v<version>'`"
- Stopper

**5.4 — Pousser vers le remote**

```bash
git push origin main --tags
```

Si le push échoue :
- Afficher l'erreur
- Afficher : "Le commit et le tag ont été créés localement. Rejouez `git push origin main --tags` manuellement."
- Stopper

**5.5 — Afficher le message de succès**

Lire le hash court du commit :
```bash
git log --oneline -1
```

Afficher :
> "Release `v<version>` publiée (commit `<hash>`, tag `v<version>`)."

## Étape 6 : Bump SNAPSHOT post-release

**Cette étape est non bloquante : un échec ne remet pas en cause la release déjà publiée.**

**6.1 — Calculer la version SNAPSHOT suivante**

À partir de la version de release (ex. `0.1.2`) :
- Incrémenter le PATCH de 1 → `0.1.3`
- Ajouter le suffixe `-SNAPSHOT` → `0.1.3-SNAPSHOT`

**6.2 — Modifier `build.gradle.kts`**

Remplacer la ligne `version = "<version-release>"` par `version = "<patch+1>-SNAPSHOT"`.

**Note :** `plugin.json` et `marketplace.json` ne doivent PAS être modifiés — ils conservent la version de release comme référence stable pour les utilisateurs du plugin.

**6.3 — Stager et committer**

```bash
git add mahi-mcp/build.gradle.kts
git commit -m "chore(release): bump to <patch+1>-SNAPSHOT"
```

**6.4 — Gérer l'échec**

Si une erreur survient à l'étape 6.2 ou 6.3 :
- Afficher : "Bump SNAPSHOT échoué : <erreur>. Effectuez-le manuellement."
- Continuer sans bloquer (la release est déjà publiée)

**6.5 — Confirmation finale**

Afficher : "Version de développement : `<patch+1>-SNAPSHOT`"

---

**Release terminée.**
