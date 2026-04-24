# Phase : Analyzing

**Objectif :** Identifier la cause racine — pas le symptôme. Appliquer le protocole
`process-debugging` : backward tracing, analyse comparative, blame git. Formuler une
hypothèse unique avec evidence. Escalader si nécessaire.

---

## Étape 1 : Charger les références process-debugging

Lire obligatoirement (en parallèle) :
- `**/mahi*/skills/process-debugging/references/root-cause-tracing.md`
- `**/mahi*/skills/process-debugging/references/defense-in-depth.md`

Ces références contiennent le protocole de backward tracing et les 4 couches de validation.
Les avoir en contexte guide l'analyse qui suit.

---

## Étape 2 : Backward tracing (process-debugging Phase 1)

Partir du point d'erreur identifié dans le `bug-report` et remonter le flux :

### 2a — Lire le code incriminé
Lire `<file>:<line>` (point d'entrée du bug-report). Comprendre :
- Quelle est la précondition violée ?
- Quelle valeur/état est inattendu ?
- D'où vient cette valeur ? (paramètre ? champ d'instance ? valeur de retour d'une autre méthode ?)

### 2b — Identifier les callers
Remonter d'un niveau :
- Si **graph disponible** : `Agent({ subagent_type: "graph-query", prompt: "Qui dépend de <ClassName>.<method> ?" })`
- Si **graph absent** : `Grep` sur le nom de méthode dans le worktree pour trouver les callers

Pour chaque caller : vérifier ce qu'il passe comme argument. Identifier où la donnée erronée entre dans le système.

### 2c — Remonter jusqu'à la source
Répéter jusqu'à trouver le point où la donnée est créée/modifiée incorrectement.
Arrêter quand : on trouve le point de création (factory, parser, query DB, entrée utilisateur).

---

## Étape 3 : Analyse comparative (process-debugging Phase 2)

Trouver un cas similaire qui **fonctionne correctement** dans le codebase :
- Même type de méthode (autre service, autre endpoint) qui traite des données similaires
- Chercher via `Grep` sur le pattern du code incriminé

Lister les **différences** entre le cas qui marche et le cas qui échoue :
- Signature de méthode ?
- Ordre d'appel ?
- Configuration / injection de dépendance ?
- Données d'entrée (nullable ? format différent ?) ?

---

## Étape 4 : Blame git (si l'analyse comparative ne suffit pas)

```bash
# Depuis le worktree :
git log --oneline --follow -10 -- <fichier-incriminé>
git blame -L <line-10>,<line+10> <fichier-incriminé>
```

Pour chaque commit suspect dans les 30 derniers jours :
```bash
git show <hash> -- <fichier-incriminé>
```

→ Identifier si une modification récente a introduit la régression.

---

## Étape 5 : Formuler l'hypothèse unique

**Format obligatoire :** "La cause racine est `<X>` parce que `<Y>`."

Exemples :
- "La cause racine est que `WorkflowService.fire()` ne recharge pas le contexte après un `save()` concurrent parce que la version en mémoire n'est pas mise à jour."
- "La cause racine est que `UserMapper.toDto()` ne gère pas le cas `null` pour `address` parce que le champ a été rendu nullable dans la migration `V12`."

**Une seule hypothèse principale.** Si plusieurs candidats existent, choisir le plus probable
et noter les autres en "alternatives" dans l'artifact.

---

## Étape 6 : Escalade (si applicable)

Si après avoir suivi les étapes 2-4, aucune hypothèse claire n'émerge :
→ Dispatcher `spec-deep-dive` avec tout le contexte :

```
Agent({
  subagent_type: "mahi:spec-deep-dive",
  model: "opus",
  prompt: "Investigation de cause racine pour le bug '<titre>'.
    Bug report : <contenu bug-report>
    Test RED : <chemin et output>
    Backward tracing effectué : <résumé des callers analysés>
    Analyse comparative : <résumé>
    Aucune hypothèse claire après analyse manuelle.
    Objectif : identifier la cause racine et proposer 2-3 pistes de fix."
})
```

Attendre le rapport de `spec-deep-dive` avant d'écrire l'artifact `root-cause`.

---

## Étape 7 : Vérifier le lien spec (si noté en phase REPORTED)

Si un spec lié a été identifié dans le bug-report :
- Lire `<specPath>/design.md` — le composant incriminé est-il couvert par un DES ?
- Si oui : noter le DES impacté dans l'artifact (utile si le fix doit être coordonné avec la spec)

---

## Étape 8 : Écrire l'artifact `root-cause`

```
mcp__plugin_mahi_mahi__write_artifact(flowId, "root-cause", <contenu>)
```

```markdown
# Cause Racine

## Hypothèse principale
"La cause racine est `<X>` parce que `<Y>`."

## Evidence

### Code incriminé
`<fichier>:<ligne>` — <explication de ce qui est incorrect>

### Backward tracing
- `<MethodeA>` → appelle `<MethodeB>` → valeur incorrecte transmise ici : <explication>
- Point d'origine : `<fichier>:<ligne>` — c'est là que la donnée erronée est créée

### Analyse comparative
`<CasQuiFonctionne>` vs `<CasQuiEchoue>` — différence clé : <description>

### Blame git (si applicable)
Commit `<hash>` (<date>) a modifié `<fichier>` : <description du changement problématique>

## Alternatives envisagées
- <Hypothèse B> — écartée parce que <raison>

## Impact potentiel
- Autres zones susceptibles d'être affectées : <liste ou "aucune identifiée">
- Spec liée : <DES-xxx si applicable, ou "aucune">
```

---

## Étape 9 : Transition vers FIXING

Présenter l'hypothèse en français à l'utilisateur. Demander :
"Hypothèse identifiée — est-ce que cette analyse vous semble juste ? Taper `/debug approve` pour passer à la correction."
