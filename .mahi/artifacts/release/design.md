# Design : release — Script de release cross-plateforme pour la marketplace Mahi

## Contexte

Correction des chemins vs requirements : plugin dans mahi-plugins/mahi/ (et non mahi-plugins/mahi-workflow/).
Fichiers concernés : mahi-mcp/build.gradle.kts, mahi-plugins/mahi/.claude-plugin/plugin.json, .claude-plugin/marketplace.json, mahi-plugins/mahi/mahi-mcp-server.jar.
Contrainte Windows : PowerShell requis pour Gradle (JAVA_HOME avec espaces).

## DES-001 : Structure de la slash command /release
Fichier : .claude/commands/release.md. Frontmatter allowed-tools: [Read, Bash, Edit]. 6 étapes séquentielles. Couvre REQ-NF-001.

## DES-002 : Vérification des pré-conditions
3 vérifications : branche=main, absence active.json, repo propre (confirmation si sale). Couvre REQ-001, REQ-NF-003.

## DES-003 : Saisie et validation de la version cible
Lecture build.gradle.kts, calcul patch/minor/major, boucle de saisie + validation MAJOR.MINOR.PATCH, confirmation. Couvre REQ-002.

## DES-004 : Mise à jour atomique des fichiers de version (Option A)
Vérification parallèle des 3 fichiers avant toute écriture. Mise à jour séquentielle si cohérents. Couvre REQ-003, REQ-NF-003.

## DES-005 : Build Gradle cross-plateforme
Détection OS via uname -s. Windows : powershell.exe gradlew.bat bootJar. Unix : ./gradlew bootJar. Vérification jar. Couvre REQ-004, REQ-NF-002.

## DES-006 : Commit, tag git et push
Staging explicite 4 fichiers. Commit chore(release). Tag annoté. Push --tags. Couvre REQ-005, REQ-NF-003.

## DES-007 : Bump SNAPSHOT post-release
build.gradle.kts → PATCH+1-SNAPSHOT. Commit dédié. plugin.json et marketplace.json inchangés. Couvre REQ-006.