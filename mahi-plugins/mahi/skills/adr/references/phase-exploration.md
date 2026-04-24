# Phase : Exploration

All output in French.

## Purpose

Identifier et analyser toutes les options envisageables. L'objectif est d'avoir une vue complète du paysage des solutions avant de commencer à comparer.

## Process

### Step 1: Lire le framing

Lire `.mahi/decisions/YYYY/MM/<adr-id>/framing.md`. Garder en tête les contraintes et non-objectifs — ils élimineront certaines options d'emblée.

### Step 2: Recueillir les options connues

"Quelles options avez-vous déjà envisagées ou entendues ?" — lister sans juger.
Ajouter les options évidentes du domaine si non mentionnées.

### Step 3: Recherche approfondie si nécessaire

Si une option nécessite une analyse technique poussée (fonctionnalités, coût, compatibilité, maturité) :

```
Agent({
  description: "Analyse approfondie de <option>",
  subagent_type: "mahi:spec-deep-dive",
  model: "opus",
  prompt: "Analyser <option> dans le contexte suivant : <problème depuis framing.md>.
    Contraintes : <contraintes>.
    Produire : fonctionnalités clés, avantages, inconvénients, complexité de mise en œuvre,
    coût (licence, infra, maintenance), maturité (communauté, support), exemples d'usage en prod."
})
```

### Step 4: Écrire options.md

Pour chaque option, documenter :

```markdown
# Options : <titre>

## Option A : <nom>

**Description** : <1-2 phrases>

**Pour** :
- <avantage 1>
- <avantage 2>

**Contre** :
- <inconvénient 1>
- <inconvénient 2>

**Complexité de mise en œuvre** : LOW | MEDIUM | HIGH
**Coût** : <estimation>
**Maturité** : <production-ready | beta | expérimental>
**Compatible avec nos contraintes** : oui | non | partiel — <détail>

---
```

Répéter pour chaque option. Minimum 2 options requises pour passer à la discussion.

### Step 5: Conformité aux rules projet

Pour chaque option, évaluer la conformité avec les rules chargées en framing :
- **Conforme** : l'option respecte les conventions et patterns existants
- **En tension** : l'option nécessiterait de modifier une rule (documenter laquelle)
- **Remise en cause** : l'option invalide une rule existante (documenter laquelle et pourquoi)

Ajouter dans `options.md` pour chaque option :
```
**Conformité aux rules** : conforme | en tension (<rule>) | remet en cause (<rule>)
```

Une option "en tension" ou "remet en cause" n'est PAS éliminée — c'est une information de trade-off, pas un veto.

### Step 6: Éliminations immédiates

Si une option viole directement une contrainte de `framing.md` : la documenter comme éliminée avec la raison.

```markdown
## Options éliminées

| Option | Raison d'élimination |
|--------|----------------------|
| <option> | <contrainte violée> |
```

### Step 6b: Auto-relecture de options.md

Avant de présenter les options, relire `options.md` en 3 passes successives :

**Pass 1 — Complétude**
- Minimum 2 options non éliminées sont-elles documentées ?
- Chaque option a-t-elle : description, pour, contre, complexité, coût, maturité, conformité aux rules ?
- Les options éliminées ont-elles une raison documentée dans le tableau d'éliminations ?

**Pass 2 — Correction**
- Les avantages/inconvénients sont-ils spécifiques au contexte du projet (pas génériques) ?
- Les éliminations sont-elles justifiées par des contraintes de `framing.md` (pas des préférences) ?
- La complexité de mise en œuvre est-elle évaluée réalistement ?

**Pass 3 — Cohérence**
- Les options sont-elles cohérentes avec les contraintes et non-objectifs de `framing.md` ?
- La conformité aux rules est-elle correctement évaluée (conforme / en tension / remet en cause) ?

Condition d'arrêt : 2 passes consécutives sans nouveau problème, ou 3 passes maximum.
Si des corrections ont été appliquées : noter le nombre avant de présenter.

### Step 7: Présenter et valider

Présenter `options.md`. Demander :
"Toutes les options pertinentes sont-elles couvertes ? Souhaitez-vous en ajouter ou approfondir une analyse ?"

### Step 8: Marquer l'artefact VALID et informer

Une fois les options validées par l'utilisateur, appeler :

```
mcp__plugin_mahi_mahi__write_artifact(flowId: <workflowId>, artifactName: "options", content: <contenu complet de options.md>)
```

Cet appel marque l'artefact `options` comme VALID côté serveur — requis pour que `START_DISCUSSION` puisse s'exécuter sans erreur de guard.

Indiquer ensuite : "Lancez `/adr approve` pour passer à la phase de discussion."

### Step 9: Rule candidates

Si pendant l'exploration une convention architecturale mérite d'être généralisée, ajouter dans `rule-candidates.md` :

```
## [exploration] <règle en une ligne>
- **Domaine** : <architecture|sécurité|infra|api|transversal>
- **Contexte** : <ce qui a déclenché l'identification>
- **Décision** : <convention proposée>
```

### Step 10: Append log.md

```
Phase exploration terminée. X options identifiées, Y éliminées.
```
