---
name: process-tdd
description: Ce skill doit être utilisé quand on implémente une sous-tâche spec (Mahi), écrit des tests en premier, suit le cycle RED-GREEN-REFACTOR, ou rencontre des mots-clés "TDD", "test-driven", "test first". Injecté systématiquement par spec-orchestrator. Couvre le cycle TDD, la correspondance sous-tâches [RED]/[GREEN], la dérivation des tests depuis les contrats DES, et la granularité des commits.
allowed-tools: ["Read", "Bash"]
---

# Process TDD

Applique le développement piloté par les tests pendant l'implémentation spec. RED-GREEN-REFACTOR pour tout code, flexibilité pour les tâches non-code.

**Principe fondamental :** Un test qui passe sans implémentation n'est pas un test — c'est du bruit.

## Sous-tâches [RED] / [GREEN] en mode spec-driven

Dans un plan Mahi, RED et GREEN sont des **sous-tâches séparées**, pas des phases au sein d'une même sous-tâche :

- `[RED]` → écrire les tests en échec uniquement. Arrêter ici. Ne pas écrire de code de production.
- `[GREEN]` → écrire le code minimal pour faire passer les tests de la sous-tâche `[RED]` correspondante. Les fichiers de test **existent déjà — ne pas les réécrire**.
- `[REFACTOR]` → nettoyer uniquement. Tous les tests doivent rester verts.
- Sans marqueur → cycle TDD complet (étapes 1–3 ci-dessous).

**Contrainte implicite :** un `[GREEN]` dépend toujours de son `[RED]`. Ne jamais commencer un `[GREEN]` si le `[RED]` correspondant n'est pas `[x]`.

## Dériver les tests du contrat DES

Quand la sous-tâche référence un `DES-xxx`, lire son **Contrat de test** avant d'écrire quoi que ce soit :

```
Contrat de test DES-xxx :
  Comportements à vérifier :
    - <comportement dérivé du critère d'acceptation REQ-xxx>
  Cas limites :
    - <cas limite identifié>
  Intégrations à tester :
    - <module ou service impliqué>
```

Le premier test RED doit couvrir le comportement principal listé dans ce contrat — pas un comportement inventé.

## Le cycle (sous-tâches sans marqueur)

### 1. RED — Écrire un test en échec

Avant tout code de production :
1. Créer ou étendre un fichier de test (Write pour nouveau, Edit pour existant)
2. Écrire un test décrivant le comportement attendu (depuis le contrat DES ou les AC REQ)
3. Lancer le test — confirmer qu'il **échoue pour la bonne raison** :
   - Le test **échoue** (feature absente) ≠ le test **errore** (syntax error, import manquant)
   - Lire le message d'erreur : doit indiquer que la feature n'existe pas encore
   - Si message d'erreur technique → corriger l'erreur, relancer, vérifier à nouveau
   - Si le test passe → il ne valide pas un nouveau comportement, le réécrire

### 2. GREEN — Écrire le code minimal

1. Écrire le minimum de code pour faire passer le test
2. Lancer le test — confirmer qu'il **passe**
3. Lancer tous les tests liés — confirmer qu'il n'y a pas de régression
4. Si les tests échouent : corriger l'implémentation, pas le test

### 3. REFACTOR — Nettoyer

Avec les tests verts comme filet de sécurité :
1. Supprimer les doublons, améliorer les noms, simplifier
2. Lancer tous les tests — confirmer qu'ils sont toujours **verts**
3. Committer

Ne jamais refactorer et changer le comportement simultanément.

## Granularité des commits

Un commit par cycle TDD complété (commits automatisés par l'agent) :
- Format : `feat(TASK-xxx.y): <description>`
- Inclure test + implémentation dans le même commit
- Chaque commit laisse le codebase dans un état passant

Pour les commits interactifs via `/commit`, le format du skill commit a la priorité.

## Quand appliquer strictement

TDD complet obligatoire pour : nouvelles features, logique métier, bug fixes, endpoints API, transformations de données, opérations BDD.

## Quand être flexible

Pas de phase RED pour : changements de config, documentation, mises à jour de dépendances, renames, scaffolding boilerplate. Lancer quand même la suite complète après ces changements.

## Détection des outils de test

| Language | Vérifier | Frameworks |
|----------|----------|------------|
| Node/TS | `package.json` scripts.test | jest, mocha, vitest |
| PHP | `composer.json`, `phpunit.xml` | phpunit, pest |
| Java | `pom.xml`, `build.gradle` | junit, testng |

Localiser les dossiers de test : `tests/`, `test/`, `__tests__/`, `src/test/`.

## Placement des fichiers de test

Suivre la convention existante dans le projet :
- Co-localisé : `src/module.ts` → `src/module.test.ts`
- Miroir : `src/module.ts` → `tests/src/module.test.ts`
- Java : `src/main/java/...` → `src/test/java/...`
- PHP : `src/Service.php` → `tests/ServiceTest.php`

## Lancer les tests

Ciblé pendant le développement, suite complète avant le commit :
- Node : `npx jest <file>`, `npx vitest run <file>`
- PHP : `./vendor/bin/phpunit --filter <test>`
- Java : `mvn test -Dtest=<Class>`, `./gradlew test --tests <Class>`

## Anti-patterns

**1. Tester le comportement du mock**
Si le test passe parce que le mock est présent, pas parce que le code fonctionne — le test est invalide. Tester le comportement réel du composant, pas l'existence du mock.

**2. Méthodes test-only dans les classes de production**
`destroy()`, `reset()`, `cleanup()` qui n'existent que pour les tests n'appartiennent pas à la classe de production. Les mettre dans des test utilities.

**3. Mocker sans comprendre les dépendances**
Avant de mocker une méthode : identifier tous ses side-effects. Si le test dépend d'un de ces side-effects, mocker à un niveau plus bas — pas la méthode elle-même. Mocker "par précaution" casse les tests silencieusement.

**4. Mocks incomplets**
Mocker uniquement les champs que l'on connaît crée des bugs silencieux quand du code en aval utilise des champs non mockés. Toujours reproduire la structure complète de la vraie réponse — tous les champs que le système pourrait consommer, pas seulement ceux utilisés dans le test immédiat.

## Preuves, pas affirmations

Ne jamais déclarer "les tests passent" sans les avoir lancés. Toujours exécuter, lire la sortie, confirmer, inclure les résultats réels dans le rapport de tâche.
