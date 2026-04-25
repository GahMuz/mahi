# Phase : Scoping

**Objectif** : définir précisément le périmètre à analyser via échange structuré avec
le développeur. **Zéro lecture de code dans cette phase** — uniquement les signatures
des classes core pour extraire les classes référencées.

---

## Étape 1 — Reformuler la demande

Synthétiser `INITIAL_DESCRIPTION` en 2-3 phrases claires :
- Quel système / feature est concerné ?
- Quelles opérations sont en scope (CRUD, sync, webhook, API, scheduling, …) ?
- Quel comportement est attendu / suspecté défaillant ?

Présenter au développeur : "Voici comment je comprends votre demande : [synthèse].
Est-ce correct ?"

Si la description est trop vague pour synthétiser → demander via `AskUserQuestion` :
"Pouvez-vous préciser en une phrase ce qui semble incorrect dans le comportement actuel ?"

---

## Étape 2 — Identifier la classe core

```
AskUserQuestion(
  question: "Quelle est la classe principale (point d'entrée) du système à analyser ?
Idéalement 1 à 3 classes — ex: PatrimoineService, BewaiWebhookHandler, SyncScheduler",
  header: "Classe core"
)
```

Accepter les noms courts (`PatrimoineService`) ou qualifiés (`ia.albr.service.PatrimoineService`).

---

## Étape 3 — Lire les classes core (signatures uniquement)

Pour chaque classe demandée, localiser le fichier via `Glob` si nécessaire, puis le lire.

Extraire :
- Les signatures de méthodes publiques (nom, paramètres, type de retour)
- Les types de champs injectés (`@Autowired`, `@Inject`, constructeur)
- Les types utilisés dans les paramètres et retours des méthodes
- Les imports (source de classes référencées)

**Ne pas analyser les corps de méthodes pour l'instant** — c'est la phase HUNTING.

Construire la liste des noms de classes référencées (sans les lire).

---

## Étape 4 — Charger le graphe si disponible

Si `.mahi/graph/manifest.json` existe → pour chaque classe core, en parallèle :

```
Agent(graph-query): "Qui dépend de <ClassName> ?" → noms des callers
Agent(graph-query): "Flux complet de <METHOD> <path>" → si endpoint HTTP
```

Résultat : liste de noms de classes liées (PAS leur code).

Si le graphe n'est pas disponible : continuer sans.

---

## Étape 5 — Confirmer le périmètre

Consolider : classes core + classes référencées + classes du graphe.
Dédupliquer. Exclure les classes framework (Spring, Jakarta, Hibernate…).

Présenter la liste :

```
AskUserQuestion(
  question: "Parmi ces classes, lesquelles inclure dans l'analyse de bugs ?",
  header: "Périmètre d'analyse",
  multiSelect: true,
  options: [
    { label: "<ClassA>", description: "<rôle court>" },
    { label: "<ClassB>", description: "<rôle court>" },
    …
  ]
)
```

Si > 10 classes sélectionnées → "10 classes max par hunt pour rester efficace.
Réduire la sélection, ou lancer `/bug-hunt new` pour un second hunt sur le reste."

---

## Étape 6 — Écrire l'artifact `scope`

```
mcp__plugin_mahi_mahi__write_artifact(flowId, "scope", <contenu>)
```

```markdown
# Scope : <titre>

## Description du flow investigué
<synthèse validée par le développeur>

## Classes confirmées en scope
- `<ClassA>` (`<chemin/relatif/ClassA.java>`) — <rôle court>
- `<ClassB>` (`<chemin/relatif/ClassB.java>`) — <rôle court>

## Classes exclues
- `<ClassC>` — raison : <hors périmètre / trop large / framework>

## Contexte graphe
<résumé des callers/flux identifiés, ou "aucun graphe disponible">
```

---

Dire au développeur : "Périmètre défini — N classes en scope. Tapez `/bug-hunt approve`
pour lancer l'analyse de bugs."
