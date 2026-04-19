# Plan : release — Script de release cross-plateforme

## Règles
*(Aucune règle sdd-rules chargée — répertoire absent du projet)*

---

## Tâches

### TASK-001 : Créer le fichier `.claude/commands/release.md` avec structure et frontmatter
**Implémente :** [DES-001]
**Satisfait :** [REQ-NF-001, REQ-NF-002]
**Exception RED/GREEN :** Tâche de création de fichier de configuration (slash command Markdown — pas de code métier testable unitairement).

#### TASK-001.1 [x] Créer `.claude/commands/release.md` avec frontmatter et squelette
- **Fichier :** `.claude/commands/release.md` (dans le worktree)
- **Action :** Créer le fichier avec frontmatter `allowed-tools: [Read, Bash, Edit]`, description, et les 6 titres de section (étapes 1 à 6) sans contenu
- **Vérification :** `ls .claude/commands/release.md` → fichier présent

---

### TASK-002 : Implémenter l'Étape 1 (pré-conditions) et l'Étape 2 (saisie version)
**Dépend de :** TASK-001.1
**Implémente :** [DES-002, DES-003]
**Satisfait :** [REQ-001, REQ-002]
**Exception RED/GREEN :** Tâche de contenu Markdown — instructions interprétées par Claude Code.

#### TASK-002.1 [x] Rédiger l'Étape 1 : Vérification des pré-conditions
- **Fichier :** `.claude/commands/release.md`
- **Action :** Ajouter la séquence : vérification branche main, absence active.json, repo propre (confirmation si sale), message de succès
- **Contenu clé :** Messages d'erreur exacts de REQ-001 (AC-2, AC-3), confirmation optionnelle (AC-4)
- **Vérification :** Relire la section et vérifier que les 5 AC de REQ-001 sont couverts

#### TASK-002.2 [x] Rédiger l'Étape 2 : Saisie et validation de la version cible
- **Fichier :** `.claude/commands/release.md`
- **Dépend de :** TASK-002.1
- **Action :** Ajouter la séquence : lecture build.gradle.kts, calcul 3 variantes, boucle de saisie avec validation `^\d+\.\d+\.\d+$`, confirmation finale
- **Contenu clé :** Format de question REQ-002.AC-2, gestion major/minor/patch (AC-4), annulation propre (AC-7)
- **Vérification :** Relire et vérifier les 7 AC de REQ-002

---

### TASK-003 : Implémenter les Étapes 3 à 6 (version, build, git, bump)
**Dépend de :** TASK-002.2
**Implémente :** [DES-004, DES-005, DES-006, DES-007]
**Satisfait :** [REQ-003, REQ-004, REQ-005, REQ-006, REQ-NF-002, REQ-NF-003]
**Exception RED/GREEN :** Contenu Markdown — instructions interprétées par Claude Code.

#### TASK-003.1 [x] Rédiger l'Étape 3 : Mise à jour atomique des fichiers de version
- **Fichier :** `.claude/commands/release.md`
- **Action :** Phase vérification (Read parallèle des 3 fichiers, arrêt si incohérence) puis phase écriture (Edit séquentiel). Chemins : `mahi-mcp/build.gradle.kts`, `mahi-plugins/mahi/.claude-plugin/plugin.json`, `.claude-plugin/marketplace.json`
- **Contenu clé :** Option A — vérification stricte avant toute écriture (REQ-003.AC-4, AC-5), message d'incohérence explicite
- **Vérification :** Relire et vérifier les 5 AC de REQ-003

#### TASK-003.2 [x] Rédiger l'Étape 4 : Build Gradle cross-plateforme
- **Fichier :** `.claude/commands/release.md`
- **Dépend de :** TASK-003.1
- **Action :** Détection OS via `uname -s`, commande Windows (PowerShell + gradlew.bat), commande Unix (./gradlew bootJar), vérification jar post-build
- **Contenu clé :** Contrainte PowerShell Windows (JAVA_HOME espaces), vérification jar dans `mahi-plugins/mahi/`, gestion échec build (REQ-004.AC-5)
- **Vérification :** Relire et vérifier les 6 AC de REQ-004

#### TASK-003.3 [x] Rédiger l'Étape 5 : Commit, tag git et push
- **Fichier :** `.claude/commands/release.md`
- **Dépend de :** TASK-003.2
- **Action :** Staging explicite des 4 fichiers, commit `chore(release): release v<version>`, tag annoté `v<version>`, push `origin main --tags`, affichage hash
- **Contenu clé :** Messages d'erreur distincts pour échec commit/tag vs échec push (REQ-005.AC-5, AC-8), message de succès exact (AC-7)
- **Vérification :** Relire et vérifier les 8 AC de REQ-005

#### TASK-003.4 [x] Rédiger l'Étape 6 : Bump SNAPSHOT post-release
- **Fichier :** `.claude/commands/release.md`
- **Dépend de :** TASK-003.3
- **Action :** Calcul PATCH+1-SNAPSHOT, Edit build.gradle.kts uniquement, commit `chore(release): bump to <version>-SNAPSHOT`, gestion échec non bloquante
- **Contenu clé :** plugin.json et marketplace.json NON modifiés (REQ-006.AC-3), échec non bloquant (AC-4)
- **Vérification :** Relire et vérifier les 4 AC de REQ-006

---

### TASK-004 : Validation de couverture et vérification finale
**Dépend de :** TASK-003.4
**Implémente :** [DES-001 à DES-007]
**Satisfait :** [Toutes REQ]

#### TASK-004.1 [x] Vérifier que le fichier n'est pas référencé dans plugin.json ni marketplace.json
- **Fichiers :** `mahi-plugins/mahi/.claude-plugin/plugin.json`, `.claude-plugin/marketplace.json`
- **Action :** Lire les deux fichiers, vérifier l'absence de toute référence à `release.md` ou `/release`
- **Vérification :** Grep `release` dans les deux fichiers → aucune occurrence concernant la commande

#### TASK-004.2 [x] Relecture finale et vérification de couverture des AC
- **Fichier :** `.claude/commands/release.md`
- **Dépend de :** TASK-004.1
- **Action :** Relire le fichier complet, cocher mentalement chaque AC de REQ-001 à REQ-NF-003, signaler tout manque
- **Vérification :** 100% des AC couverts, aucune instruction ambiguë

---

## Graphe de dépendances

```
TASK-001.1
    ├── TASK-002.1
    │       └── TASK-002.2
    │               └── TASK-003.1
    │                       └── TASK-003.2
    │                               └── TASK-003.3
    │                                       └── TASK-003.4
    │                                               └── TASK-004.1
    │                                                       └── TASK-004.2
```

> Note : La chaîne est entièrement séquentielle car toutes les tâches écrivent dans le même fichier `.claude/commands/release.md`. Aucune parallélisation possible — parallélisables dans le premier lot : 1 (TASK-001.1).

---

## Couverture

| DES | TASK couvrant |
|-----|---------------|
| DES-001 | TASK-001, TASK-004 |
| DES-002 | TASK-002.1 |
| DES-003 | TASK-002.2 |
| DES-004 | TASK-003.1 |
| DES-005 | TASK-003.2 |
| DES-006 | TASK-003.3 |
| DES-007 | TASK-003.4 |

| REQ | TASK couvrant |
|-----|---------------|
| REQ-001 | TASK-002.1 |
| REQ-002 | TASK-002.2 |
| REQ-003 | TASK-003.1 |
| REQ-004 | TASK-003.2 |
| REQ-005 | TASK-003.3 |
| REQ-006 | TASK-003.4 |
| REQ-NF-001 | TASK-001.1, TASK-004.1 |
| REQ-NF-002 | TASK-003.2 |
| REQ-NF-003 | TASK-002.1, TASK-003.1, TASK-003.3 |

---

**Total : 4 tâches, 8 sous-tâches, 1 parallélisable dans le premier lot.**
