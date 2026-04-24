# Phase : Decision

All output in French.

## Purpose

Formaliser la décision dans un document ADR structuré et durable. Ce document sera la référence pour tous les développeurs qui rejoindront le projet.

## Process

### Step 1: Synthétiser les inputs

Lire :
- `.mahi/decisions/YYYY/MM/<adr-id>/framing.md` — problème, contraintes, non-objectifs
- `.mahi/decisions/YYYY/MM/<adr-id>/options.md` — toutes les options avec trade-offs
- `.mahi/decisions/YYYY/MM/<adr-id>/log.md` — arguments clés de la discussion, option choisie

### Step 2: Rédiger adr.md

Écrire `.mahi/decisions/YYYY/MM/<adr-id>/adr.md` :

```markdown
# ADR-NNN : <titre>

## Statut
Décidé — <date ISO-8601>

## Contexte
<description du problème depuis framing.md — 2-4 phrases>

## Contraintes
- <contrainte 1>
- <contrainte 2>

## Options considérées

| Option | Pour | Contre | Complexité |
|--------|------|--------|------------|
| <A>    | ...  | ...    | LOW        |
| <B>    | ...  | ...    | HIGH       |

## Décision
**Option retenue : <nom de l'option>**

<justification en 2-4 phrases : pourquoi cette option, en quoi elle satisfait les contraintes, pourquoi les autres ont été écartées>

## Conséquences

**Positives :**
- <impact positif 1>
- <impact positif 2>

**Négatives / Risques :**
- <risque ou impact négatif 1>
- <mitigation si connue>

## Règles impactées
<si la décision est conforme à toutes les rules existantes : "Aucune — décision conforme aux conventions existantes.">
<si une rule est remise en cause :>
- **`<fichier-rule>`** — règle : "<texte de la rule>" → **remise en cause** : <explication pourquoi cette décision l'invalide ou la modifie>
  Validation requise : [ ] Approuvé par <qui> le <date>

## Prochaines étapes
- [ ] <action concrète 1 — ex: spike technique, spec d'implémentation>
- [ ] <action concrète 2>
```

### Step 2b: Auto-relecture de adr.md

Avant de présenter l'ADR, relire `adr.md` en 3 passes successives :

**Pass 1 — Complétude**
- Toutes les sections sont-elles présentes (Statut, Contexte, Contraintes, Options, Décision, Conséquences, Règles impactées, Prochaines étapes) ?
- La table des options inclut-elle toutes les options considérées ?
- Les prochaines étapes sont-elles concrètes et actionnables ?

**Pass 2 — Correction**
- La justification de la décision cite-t-elle les contraintes de `framing.md` ?
- Les conséquences négatives sont-elles honnêtes (pas minimisées) ?
- La section "Règles impactées" est-elle correctement renseignée (pas oubliée si une rule est remise en cause) ?

**Pass 3 — Cohérence**
- La décision est-elle cohérente avec les arguments clés enregistrés dans `log.md` ?
- Les options rejetées sont-elles correctement résumées sans être caricaturées ?
- Le contexte correspond-il fidèlement au `framing.md` ?

Condition d'arrêt : 2 passes consécutives sans nouveau problème, ou 3 passes maximum.
Si des corrections ont été appliquées : noter le nombre avant de présenter l'ADR.

### Step 3: Présenter et valider

Présenter `adr.md` complet. Demander :
"L'ADR capture-t-il fidèlement la décision et son raisonnement ? Souhaitez-vous modifier quelque chose ?"

Appliquer les corrections demandées.

### Step 4: Marquer l'artefact VALID et informer

Une fois l'ADR validé par l'utilisateur, appeler :

```
mcp__plugin_mahi_mahi__write_artifact(flowId: <workflowId>, artifactName: "adr", content: <contenu complet de adr.md>)
```

Cet appel marque l'artefact `adr` comme VALID côté serveur — requis pour que `START_RETROSPECTIVE` puisse s'exécuter sans erreur de guard.

Indiquer ensuite : "Lancez `/adr approve` pour finaliser cet ADR et passer à la rétrospective."

### Step 5: Rule candidates

Si la décision elle-même constitue une règle généralisable, ajouter dans `rule-candidates.md` :

```
## [decision] <règle en une ligne>
- **Domaine** : <architecture|sécurité|infra|api|service|transversal>
- **Contexte** : décision de l'ADR-NNN — <résumé>
- **Décision** : <convention à appliquer systématiquement>
```

### Step 6: Append log.md

```
Décision formalisée : <option choisie>. ADR-NNN rédigé.
```
