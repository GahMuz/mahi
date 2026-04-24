# Phase : Reproducing

**Objectif :** Reproduire le bug de façon fiable et écrire un test RED (TDD) dans le
worktree dédié. L'artifact `reproduction` documente le cas de reproduction et le test créé.

---

## Étape 1 : Charger le contexte complémentaire

Si le graphe n'a pas encore été chargé en phase `reported` (ou si le résultat était absent) :
- Lancer un graph-query ciblé sur le composant identifié
- Lire les tests existants du composant : `Grep` sur `<ClassName>Test` ou `<ClassName>Spec`
- Lire 1-2 tests similaires pour comprendre les conventions du projet (framework de test, assertions, setup)

---

## Étape 2 : Stratégie de reproduction selon le type d'input

### Type `stacktrace` ou `mixed`
1. Identifier le cas d'appel qui déclenche l'exception :
   - Lire la méthode incriminée (`<file>:<line>` du bug-report)
   - Identifier les paramètres d'entrée qui causent l'erreur (null ? valeur limite ? état invalide ?)
2. Le cas de test minimal : appeler cette méthode avec les mêmes conditions
3. Écrire le test dans le worktree (`.worktrees/<debug-id>/`)

### Type `failing-test`
1. Le test existe déjà — l'exécuter pour confirmer qu'il échoue encore
   ```bash
   # Java/Gradle (depuis le worktree) :
   cd .worktrees/<debug-id> && ./gradlew test --tests "<TestClass>#<testMethod>"
   ```
2. Si le test passe maintenant → bug intermittent, documenter les conditions
3. Si le test échoue → parfait, c'est notre test RED. Copier le chemin pour l'artifact.

### Type `explanation`
1. Formaliser le comportement observé vs attendu en termes testables
2. Chercher la méthode ou le endpoint impliqué
3. Écrire un test qui échoue quand le comportement est incorrect

---

## Étape 3 : Écrire le test RED (si pas déjà existant)

**Règles du test RED :**
- Le test doit échouer avec le **bon message** d'erreur (celui du bug-report)
- Une seule assertion — le test est aussi minimal que possible
- Ne pas corriger le bug pour faire passer le test — le bug doit rester présent
- Placer le test dans le package/dossier approprié du worktree

**Convention de nommage :**
- Java : `test<TitreEnPascalCase>()` dans `<ClassName>Test.java` existant ou nouveau
- TypeScript : `it('should <comportement attendu> when <condition>')` dans `*.spec.ts`

**Exécuter le test pour confirmer l'échec :**
```bash
# Depuis le worktree :
cd .worktrees/<debug-id> && <commande de test>
```

Le test DOIT échouer avec un message d'erreur cohérent avec le bug-report. Si le test
passe (inattendu) → revoir le cas de test, le bug est peut-être différent de ce qu'on pensait.

---

## Étape 4 : Écrire l'artifact `reproduction`

```
mcp__plugin_mahi_mahi__write_artifact(flowId, "reproduction", <contenu>)
```

```markdown
# Reproduction

## Commande / test
<commande exacte pour reproduire, ou chemin du test>

## Résultat observé (actuel)
```
<output exact — exception, message d'assertion, comportement incorrect>
```

## Résultat attendu (correct)
<comportement que le code devrait avoir>

## Test RED créé
`<chemin/dans/le/worktree/TestFile.java>#<testMethod>` — confirmé FAILING ✓

Sortie du test :
```
<extrait de l'échec du test>
```

## Fréquence
<toujours | intermittent (conditions : …) | uniquement sous conditions spécifiques>

## Conditions déclenchantes
<données d'entrée, état de la base, configuration, ordre d'opérations>
```

---

## Étape 5 : Transition vers ANALYZING

Présenter le résumé en français : "Test RED confirmé — `<chemin du test>` échoue comme attendu. Phase suivante : analyser la cause racine."

Taper `/debug approve` pour avancer en phase ANALYZING.
