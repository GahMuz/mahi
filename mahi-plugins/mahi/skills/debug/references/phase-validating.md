# Phase : Validating

**Objectif :** Vérifier qu'il n'y a aucune régression, valider les cas limites, commiter
le fix proprement sur la branche debug. Produire le `test-report` final.

---

## Étape 1 : Identifier le scope de test

Depuis le fix appliqué (artifact `fix`), identifier les suites de tests concernées :
- **Suite directe** : tests du composant modifié (`<ClassName>Test`)
- **Suites callers** : composants identifiés lors du backward tracing en phase ANALYZING
- **Si module Gradle distinct** : scope du module plutôt que la classe

---

## Étape 2 : Exécuter la suite complète du composant

```bash
cd .worktrees/<debug-id> && <commande test suite>
```

Exemples :
- Java/Gradle : `./gradlew test --tests "<package>.*"` ou `./gradlew :<module>:test`
- Maven : `./mvnw test -pl <module>`
- Node/Jest : `npx jest --testPathPattern="<composant>"`

**Résultat attendu :** Tous les tests passent. Le test RED créé en phase REPRODUCING est maintenant GREEN.

Si des tests **non liés** au bug échouent → investiguer avant de continuer. Ne pas ignorer.

---

## Étape 3 : Tester les callers (non-régression)

Pour chaque caller identifié en phase ANALYZING :
```bash
cd .worktrees/<debug-id> && <commande test caller>
```

Si un caller échoue → le fix introduit une régression. Retourner en phase FIXING :
- Revenir sur le changement
- Comprendre l'impact sur le caller
- Adapter le fix (ou fixer le caller si le comportement précédent était incorrect)

---

## Étape 4 : Vérifier les cas limites

Pour la méthode/composant fixé, tester manuellement ou via assertions :
- **Null input** : que se passe-t-il si la donnée principale est null ?
- **Empty / zéro** : collection vide, string vide, zéro ?
- **Valeurs extrêmes** : Long.MAX_VALUE, dates passées/futures, strings très longues ?
- **Concurrence** (si applicable) : le fix est-il thread-safe ?

Pour chaque cas limite non couvert par un test existant, ajouter une assertion dans le test RED (transformer en test multi-scenarios) ou créer un test complémentaire.

---

## Étape 5 : Commiter le fix

Stager uniquement les fichiers modifiés pour le fix (pas les artifacts `.mahi/`) :
```bash
cd .worktrees/<debug-id>
git add <fichiers-modifiés> <fichiers-test>
git commit -m "fix(<scope>): <description courte du bug corrigé>"
```

Convention du message :
- `fix(workflow): prevent NPE when firing event on stale context`
- `fix(mapper): handle null address in UserMapper.toDto()`

**Ne pas commiter** : les fichiers `.mahi/`, les fichiers de config locaux, les logs.

---

## Étape 6 : Consigner dans log.md

Appender dans `.mahi/debug/YYYY/MM/<debug-id>/log.md` :
```
[<date>] Phase validating — Fix commité : <hash> — <message de commit>
         Durée totale session : <début> → maintenant = <durée>
         Root cause : <hypothèse en une ligne>
         Tests exécutés : <nombre> — tous passants ✓
```

---

## Étape 7 : Écrire l'artifact `test-report`

```
mcp__plugin_mahi_mahi__write_artifact(flowId, "test-report", <contenu>)
```

```markdown
# Rapport de validation

## Tests exécutés

| Suite | Résultat |
|-------|----------|
| `<ClassName>Test` (suite directe) | X/Y passants ✓ |
| `<CallerTest>` (non-régression) | X/Y passants ✓ |

## Test RED → GREEN
`<chemin>#<method>` — PASSING ✓

## Régressions détectées
<aucune | liste des problèmes résolus>

## Cas limites vérifiés
- null input : ✓ / ✗ (testé dans `<test>`)
- empty : ✓ / ✗
- <autres si applicable> : ✓

## Commit du fix
`<hash>` — `<message de commit>`
Branche : `debug/<username>/<debug-id>`

## Durée totale de la session
<date début> → <date fin> = <durée en heures/minutes>

## Résumé exécutif
**Bug :** <symptôme en une phrase>
**Cause racine :** <hypothèse en une phrase>
**Fix :** <description du changement en une phrase>
```

---

## Étape 8 : Clore la session

Après `write_artifact("test-report")`, APPROVE déclenche `fire_event(CLOSE)` → état DONE.

La session est ensuite fermée automatiquement via CLOSE (ExitWorktree + deactivate).

Afficher en français le récapitulatif final :
```
✅ Debug terminé : '<titre>'

Cause racine : <hypothèse>
Fix : <description>
Commit : <hash> sur branche debug/<username>/<debug-id>

Prochaines étapes :
- Créer une PR depuis debug/<username>/<debug-id> vers main
- Optionnel : lancer /spec-review si le bug touchait une feature spécifiée
```
