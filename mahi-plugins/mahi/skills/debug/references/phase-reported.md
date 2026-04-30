# Phase : Reported

**Objectif :** Capturer et structurer le bug report, détecter le type d'input, charger
le contexte ciblé depuis les sources disponibles (git, graph, docs, spec). Produire un
artifact `bug-report` précis et actionnable.

---

## Étape 1 : Détecter le type d'input

Analyser `INPUT_CONTENT` (texte fourni après le titre dans `/debug new`) :

| Signaux | Type détecté |
|---------|--------------|
| Lignes `at com.`, `Exception in thread`, `Caused by:`, `java.lang.` | `stacktrace` |
| `FAILED`, `AssertionError`, `expected:` / `but was:`, nom méthode test | `failing-test` |
| Mix des deux | `mixed` |
| Texte descriptif uniquement | `explanation` |

Si `INPUT_CONTENT` est vide (l'utilisateur n'a fourni aucune description après le titre) :
→ Demander via `AskUserQuestion` : "Décrivez le bug (stacktrace, message d'erreur, ou comportement observé)."

---

## Étape 2 : Extraire les informations structurées

### Pour `stacktrace` ou `mixed`

Extraire :
- **Exception** : classe complète + message (ex: `NullPointerException: Cannot invoke method X`)
- **Point d'entrée projet** : premier frame qui n'est PAS du framework (Spring, Hibernate, JUnit…) — c'est là que le code du projet est impliqué
  ```
  at ia.mahi.service.WorkflowServiceImpl.fire(WorkflowServiceImpl.java:87)
  ```
  → `file = WorkflowServiceImpl.java`, `line = 87`, `class = WorkflowServiceImpl`, `method = fire`
- **Cause racine candidate** (`Caused by:` si présent) — exception plus profonde
- **Contexte d'appel** : 2-3 frames au-dessus du point d'entrée projet

### Pour `failing-test`

Extraire :
- Nom complet du test (`com.example.MyServiceTest#testSomething`)
- Message d'assertion (`expected: <X> but was: <Y>`)
- Classe testée (déduire depuis le nom de la classe de test)

### Pour `explanation`

Extraire :
- Comportement attendu vs observé (en une phrase chacun)
- Composant mentionné (service, endpoint, feature)
- Conditions de déclenchement (toujours / parfois / sous condition)

---

## Étape 3 : Charger le contexte ciblé (en parallèle)

Lancer les recherches suivantes simultanément :

### 3a — Modifications git récentes
```bash
git log --oneline -20 -- <fichier-extrait-du-stacktrace>
```
Si le fichier n'est pas connu (`explanation`) : `git log --oneline -10` (tous les fichiers).

→ Identifier les commits des 7 derniers jours qui touchent le composant concerné.

### 3b — Graphe structurel (si disponible)
Si `.mahi/graph/manifest.json` existe :
- Composant de type service/endpoint → dispatcher `Agent({ subagent_type: "graph-query", model: "haiku", prompt: "..." })` :
  - Service : "Qui dépend de `<ClassName>` ?"
  - Endpoint : "Flux complet de `<METHOD> <path>`"
  - Entité/table : "Entité `<EntityName>`"
- Si graphe absent → ignorer silencieusement

### 3c — Documentation module (si disponible)
Si `.mahi/docs/modules/<module>/module-<module>.md` existe pour le module du composant extrait :
→ Lire le doc module (remplace l'exploration brute des fichiers).

### 3d — Lien avec un spec existant (si pertinent)
Lire `.mahi/work/registry.json`. Chercher les specs récents (`status != "discarded"`) :
- Si un spec mentionne le composant incriminé dans son titre ou path → noter le lien (utile en phase ANALYZING)
- Ne pas charger le spec entier à ce stade — juste noter l'existence

---

## Étape 4 : Écrire l'artifact `bug-report`

Construire le contenu suivant et appeler :
```
mcp__plugin_mahi_mahi__write_artifact(flowId, "bug-report", <contenu>)
```

```markdown
# Bug Report : <titre>

## Type
<stacktrace | failing-test | explanation | mixed>

## Symptôme
<une phrase : "X fait Y au lieu de Z">

## Point d'entrée
<file>:<line> — `<ExceptionClass>: <message>`

(Si failing-test : `<TestClass>#<testMethod>` — `expected: X but was: Y`)

## Stack trace (extrait)
```
<3-5 lignes les plus pertinentes>
```

## Contexte chargé

**Modifications récentes sur les fichiers concernés :**
<liste des commits pertinents, ou "aucune modification récente">

**Graphe structurel :**
<résultat du graph-query, ou "graphe non disponible">

**Documentation module :**
<résultat si disponible, ou "docs non générées">

**Lien spec :**
<nom du spec lié, ou "aucun spec lié détecté">

## Étapes minimales pour reproduire
<ce qui est connu à ce stade — sera affiné en phase REPRODUCING>

## Hypothèse initiale
<optionnel : si une cause évidente saute aux yeux depuis le stacktrace>
```

---

## Étape 5 : Confirmer et avancer

Présenter le `bug-report` à l'utilisateur en français. Demander :
"Ce résumé capture-t-il correctement le bug ? Taper `/debug approve` pour passer à la reproduction, ou préciser si quelque chose manque."

Ne pas appeler `fire_event` automatiquement — attendre la confirmation explicite via `/debug approve`.
