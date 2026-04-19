# Exigences : release — Script de release cross-plateforme pour la marketplace Mahi

## Contexte codebase

**Module `mahi-mcp/`** — Serveur MCP Java Spring Boot.
Build via Gradle (`./gradlew bootJar` / `gradlew.bat bootJar`).
La tâche `bootJar` produit `mahi-mcp-server.jar` et le copie automatiquement
dans `mahi-plugins/mahi-workflow/`.
Version courante : `0.1.0-SNAPSHOT` dans `build.gradle.kts`.

**Module `mahi-plugins/mahi-workflow/`** — Plugin Mahi contenant :
- `plugin.json` : métadonnées versionnées (`"version": "0.1.0"`)
- `mahi-mcp-server.jar` : fat jar compilé, versionné et committé avec le plugin
- `skills/`, `agents/` : les fichiers du workflow

**Fichier `.claude-plugin/marketplace.json`** — Registre local des plugins Mahi.
Contient la liste des plugins avec leur version, description et métadonnées.
Le champ `"version"` de chaque plugin doit être cohérent avec `plugin.json`.

**Patterns existants** :
- Pas de script de release existant — processus manuel jusqu'ici
- Le repo n'a pas de git tags de version
- La slash command `/commit` existe dans `mahi-plugins/mahi-workflow/skills/commit/SKILL.md`
  comme référence de pattern pour les skills

**Contrainte d'implémentation (clarification)** :
La commande de release est implémentée en tant que slash command dans
`.claude/commands/` à la racine du repo (pas dans un plugin).
Elle vérifie obligatoirement que l'exécution a lieu depuis la branche `main`,
refuse si un worktree est actif, et est réservée au mainteneur.

---

## Glossaire

| Terme | Définition |
|-------|------------|
| Release | Opération de publication d'une nouvelle version du plugin Mahi dans la marketplace locale |
| Version sémantique | Format `MAJOR.MINOR.PATCH` (ex. `0.1.0`) — sans suffixe `-SNAPSHOT` en release |
| `plugin.json` | Fichier de métadonnées du plugin Mahi dans `mahi-plugins/<nom>/plugin.json` |
| `marketplace.json` | Registre local des plugins dans `.claude-plugin/marketplace.json` |
| `build.gradle.kts` | Fichier de build Gradle qui déclare la version du jar (`version = "X.Y.Z-SNAPSHOT"`) |
| Fat jar | Archive Java autonome `mahi-mcp-server.jar` contenant toutes les dépendances |
| Slash command | Commande Claude Code définie dans `.claude/commands/` et invoquée par l'utilisateur avec `/` |
| Mainteneur | Rôle unique détenant le droit de créer des releases — vincent-bailly dans ce projet |
| Worktree actif | Présence de `.sdd/local/active.json` indiquant un spec ou ADR en cours |
| `main` | Branche principale du repo — seul point de départ valide pour une release |

---

## Périmètre

### Dans le périmètre

| Fonctionnalité | Description |
|----------------|-------------|
| Slash command `/release` | Commande dans `.claude/commands/release.md` orchestrant le processus de release |
| Vérification des pré-conditions | Contrôle de la branche (`main`), absence de worktree actif, propreté du repo git |
| Saisie de la version cible | Demande de la version sémantique cible et validation du format |
| Mise à jour de la version | Remplacement du numéro de version dans `build.gradle.kts`, `plugin.json`, `marketplace.json` |
| Build du jar | Exécution de Gradle (`gradlew bootJar` / `./gradlew bootJar` selon l'OS) |
| Commit et tag git | Commit des fichiers modifiés et création du tag git `v<version>` |
| Push remote | Push de `main` et des tags vers le remote (`git push origin main --tags`) |
| Cross-plateforme | Fonctionne sur Windows (gradlew.bat) et Unix (gradlew) |

### Hors périmètre

| Fonctionnalité | Raison |
|----------------|--------|
| Publication vers un registry externe | Pas de registry npm/Maven cible dans ce projet |
| Rollback automatique | Hors périmètre initial — opération rare traitée manuellement |
| Gestion de changelog | Hors périmètre — les messages de commit servent d'historique |
| Versioning multi-plugin | Un seul plugin (`mahi-workflow`) pour l'instant |
| Intégration CI/CD | Pas de pipeline CI dans ce projet |

---

## Exigences fonctionnelles

### REQ-001 : Vérification des pré-conditions avant release

**Récit utilisateur :**
En tant que mainteneur du projet Mahi, je veux que la commande `/release` vérifie
les pré-conditions avant de démarrer afin d'éviter de publier une version depuis
un état incohérent du repo.

**Critères d'acceptation :**

1. QUAND l'utilisateur invoque `/release` ALORS LA commande DOIT vérifier que la branche
   git courante est `main`
2. QUAND la branche courante n'est pas `main` ALORS LA commande DOIT refuser avec le message :
   "Release impossible : vous n'êtes pas sur la branche `main` (branche courante : <nom>)."
3. QUAND le fichier `.sdd/local/active.json` existe ALORS LA commande DOIT refuser avec
   le message : "Release impossible : un spec ou ADR est actuellement actif. Fermez-le d'abord."
4. QUAND le repo git contient des fichiers modifiés non commités ALORS LA commande DOIT
   afficher un avertissement et demander confirmation avant de continuer
5. QUAND toutes les pré-conditions sont satisfaites ALORS LA commande DOIT afficher :
   "Pré-conditions vérifiées — prêt pour la release."

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-002 : Saisie et validation de la version cible

**Récit utilisateur :**
En tant que mainteneur, je veux saisir le numéro de version cible de la release afin
que le processus de publication utilise la version correcte dans tous les artefacts.

**Critères d'acceptation :**

1. QUAND les pré-conditions sont validées ALORS LA commande DOIT lire la version courante
   depuis `mahi-mcp/build.gradle.kts` (ex. `0.1.0-SNAPSHOT`) et calculer la version patch
   par défaut en incrémentant le PATCH de 1 et en supprimant le suffixe (ex. `0.1.1`)
2. LA commande DOIT proposer cette version par défaut :
   "Numéro de version pour cette release [0.1.1] ? (Entrée = valider, ou saisir major/minor/patch)"
3. QUAND l'utilisateur appuie sur Entrée sans saisir ALORS LA commande DOIT utiliser
   la version patch proposée par défaut
4. QUAND l'utilisateur saisit `major`, `minor` ou `patch` ALORS LA commande DOIT calculer
   et proposer la version correspondante avant confirmation
5. LE format de la version finale DOIT respecter le pattern `MAJOR.MINOR.PATCH`
   (uniquement des chiffres séparés par des points, sans suffixe)
6. QUAND la version saisie ne respecte pas le format ALORS LA commande DOIT rejeter
   la saisie et redemander
7. QUAND la version est validée ALORS LA commande DOIT afficher un récapitulatif :
   "Release `v<version>` — confirmer ? (oui / annuler)"

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-003 : Mise à jour des fichiers de version

**Récit utilisateur :**
En tant que mainteneur, je veux que la commande mette à jour automatiquement tous les
fichiers contenant le numéro de version afin que la release soit cohérente à travers
tous les artefacts du projet.

**Critères d'acceptation :**

1. QUAND la release est confirmée ALORS LA commande DOIT mettre à jour la version dans
   `mahi-mcp/build.gradle.kts` (ligne `version = "X.Y.Z-SNAPSHOT"` → `version = "X.Y.Z"`)
2. LA commande DOIT mettre à jour le champ `"version"` dans
   `mahi-plugins/mahi-workflow/plugin.json`
3. LA commande DOIT mettre à jour le champ `"version"` correspondant à `mahi-workflow`
   dans `.claude-plugin/marketplace.json`
4. QUAND un fichier à modifier n'existe pas ou ne contient pas la version attendue ALORS
   LA commande DOIT signaler l'incohérence et s'arrêter avant toute modification
5. LES trois fichiers DOIVENT être mis à jour atomiquement (tous ou aucun) avant le build

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-004 : Build du jar via Gradle

**Récit utilisateur :**
En tant que mainteneur, je veux que la commande déclenche automatiquement le build Gradle
afin que le jar `mahi-mcp-server.jar` soit recompilé et copié dans le plugin avec la
bonne version.

**Critères d'acceptation :**

1. QUAND les versions sont mises à jour ALORS LA commande DOIT exécuter le build Gradle
   depuis le répertoire `mahi-mcp/`
2. SUR Windows, LE build DOIT utiliser `gradlew.bat bootJar`
3. SUR Unix/macOS, LE build DOIT utiliser `./gradlew bootJar`
4. LA détection de l'OS DOIT être automatique — la commande ne DOIT PAS demander à
   l'utilisateur de choisir la plateforme
5. QUAND le build échoue ALORS LA commande DOIT afficher la sortie d'erreur Gradle et
   proposer de corriger avant de continuer
6. QUAND le build réussit ALORS LA commande DOIT vérifier que
   `mahi-plugins/mahi-workflow/mahi-mcp-server.jar` a bien été mis à jour

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-005 : Commit et tag git de la release

**Récit utilisateur :**
En tant que mainteneur, je veux que la commande crée automatiquement le commit et le tag
git de la release afin que la version soit traçable dans l'historique git.

**Critères d'acceptation :**

1. QUAND le build réussit ALORS LA commande DOIT stager les fichiers modifiés :
   - `mahi-mcp/build.gradle.kts`
   - `mahi-plugins/mahi-workflow/plugin.json`
   - `mahi-plugins/mahi-workflow/mahi-mcp-server.jar`
   - `.claude-plugin/marketplace.json`
2. LE message de commit DOIT suivre le format :
   `chore(release): release v<version>`
3. LA commande DOIT créer un tag git annoté `v<version>` sur le commit de release
4. LE tag DOIT inclure le message : `Release v<version>`
5. QUAND le commit ou le tag échoue ALORS LA commande DOIT afficher l'erreur et
   informer l'utilisateur que les fichiers ont déjà été modifiés
6. QUAND le commit et le tag sont créés ALORS LA commande DOIT pousser vers le remote :
   `git push origin main --tags`
7. QUAND le push réussit ALORS LA commande DOIT afficher :
   "Release v<version> publiée (commit <hash>, tag v<version>)."
8. QUAND le push échoue ALORS LA commande DOIT afficher l'erreur et informer l'utilisateur
   que le commit et le tag sont créés localement — le push peut être rejoué manuellement

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-006 : Remise en version SNAPSHOT après release

**Récit utilisateur :**
En tant que mainteneur, je veux que la commande remette automatiquement `build.gradle.kts`
en version SNAPSHOT après la release afin que le développement suivant reparte sur
une version de développement correcte.

**Critères d'acceptation :**

1. QUAND le tag de release est créé ALORS LA commande DOIT mettre à jour
   `build.gradle.kts` en incrémentant le patch et ajoutant `-SNAPSHOT`
   (ex. `0.2.0` → `0.2.1-SNAPSHOT`)
2. LA commande DOIT committer ce changement avec le message :
   `chore(release): bump to <version>-SNAPSHOT`
3. LE fichier `plugin.json` et `marketplace.json` NE DOIVENT PAS être modifiés
   lors de ce bump (ils conservent la version de release jusqu'à la prochaine release)
4. QUAND le bump échoue ALORS LA commande DOIT informer l'utilisateur et ne pas
   bloquer — le bump peut être fait manuellement

**Priorité :** souhaitable
**Statut :** brouillon

---

## Exigences non fonctionnelles

### REQ-NF-001 : Implémentation en tant que slash command du repo

**Récit utilisateur :**
En tant que mainteneur, je veux que `/release` soit une commande définie dans
`.claude/commands/` du repo afin qu'elle soit disponible uniquement dans ce projet
et non publiée comme un plugin marketplace.

**Critères d'acceptation :**

1. LA commande DOIT être définie dans `.claude/commands/release.md` à la racine du repo
2. LA commande NE DOIT PAS être placée dans `mahi-plugins/` ni déclarée dans `plugin.json`
3. LA commande NE DOIT PAS être publiée dans `marketplace.json`
4. LA commande DOIT être accessible via `/release` dans Claude Code quand la session
   est ouverte à la racine du repo Mahi

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-NF-002 : Compatibilité cross-plateforme Windows / Unix

**Récit utilisateur :**
En tant que mainteneur travaillant sur Windows ou Unix, je veux que le script de release
fonctionne sans modification sur les deux plateformes afin de ne pas maintenir
deux versions de la commande.

**Critères d'acceptation :**

1. LA commande DOIT détecter automatiquement l'OS pour choisir `gradlew.bat` ou `gradlew`
2. LA commande NE DOIT PAS requérir d'adaptation manuelle selon la plateforme
3. LES chemins de fichiers utilisés par la commande DOIVENT être compatibles avec les
   deux systèmes de fichiers

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-NF-003 : Idempotence et sécurité des modifications

**Récit utilisateur :**
En tant que mainteneur, je veux que la commande soit sûre à annuler en cas d'erreur
afin de ne pas laisser le repo dans un état incohérent si la release échoue à mi-chemin.

**Critères d'acceptation :**

1. QUAND la commande est annulée avant le commit ALORS LE repo DOIT pouvoir être
   restauré à son état initial via `git restore .`
2. LA commande DOIT informer l'utilisateur des actions déjà effectuées si elle est
   interrompue après le commit
3. LA commande NE DOIT PAS supprimer ni écraser des données sans confirmation préalable

**Priorité :** souhaitable
**Statut :** brouillon