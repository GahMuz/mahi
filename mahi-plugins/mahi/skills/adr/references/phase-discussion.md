# Phase : Discussion

All output in French.

## Purpose

Comparer les options en profondeur, confronter les arguments, et converger vers une décision. Le rôle de Claude est de structurer la discussion, pas d'imposer un choix.

## Process

### Step 1: Lire framing.md et options.md

Identifier les options non éliminées. Préparer la grille de comparaison.

### Step 2: Présenter la matrice de trade-offs

```
## Comparaison des options

| Critère              | Option A | Option B | Option C |
|----------------------|----------|----------|----------|
| Complexité           | LOW      | HIGH     | MEDIUM   |
| Coût                 | Gratuit  | $$$      | $        |
| Compatibilité        | ✓        | ✓        | ✗        |
| Maturité             | ✓        | ✓        | Beta     |
| Migration existant   | Facile   | Complexe | Moyenne  |
```

Adapter les critères au domaine du problème.

### Step 3: Discussion guidée option par option

Pour chaque option non éliminée, présenter :

```
**Option <X> : <nom>**

Points forts dans votre contexte : <liste>
Points faibles dans votre contexte : <liste>
Question clé : <question ouverte spécifique à cette option>
```

Laisser l'utilisateur réagir. Noter les arguments dans `log.md` au fil de la discussion.

### Step 4: Gérer les questions ouvertes

Si une question technique bloque la discussion ("on ne sait pas si X supporte Y") :
- Proposer une recherche ciblée via l'agent `mahi:spec-deep-dive`
- Ou noter comme hypothèse et continuer

### Step 5: Identifier le(s) finaliste(s)

Après discussion de toutes les options :
"À ce stade, quelle(s) option(s) retenez-vous comme finaliste(s) ?"

Si 1 finaliste → passage direct à la décision.
Si 2 finalistes → comparaison finale ciblée sur les points de différence.

### Step 5b: Auto-relecture de la conclusion de discussion

Avant de vérifier le consensus, relire `log.md` et les notes de discussion en 3 passes :

**Pass 1 — Complétude**
- Toutes les options non éliminées ont-elles été discutées ?
- Les arguments clés (pour et contre) sont-ils dans `log.md` ?
- La/les option(s) finaliste(s) est/sont-elle(s) identifiée(s) ?

**Pass 2 — Correction**
- Les points forts et faibles sont-ils spécifiques au contexte du projet (pas génériques) ?
- Les questions ouvertes ont-elles été résolues ou documentées comme hypothèses ?
- Aucune option finaliste n'est-elle en contradiction flagrante avec une contrainte de framing ?

**Pass 3 — Cohérence**
- La conclusion candidate est-elle cohérente avec les arguments enregistrés dans `log.md` ?
- Pas de contradiction entre le consensus verbal et les trade-offs documentés dans `options.md` ?

Condition d'arrêt : 2 passes consécutives sans nouveau problème, ou 3 passes maximum.
Si des incohérences sont trouvées : les noter et relancer la discussion sur le point précis avant de continuer.

### Step 6: Vérifier le consensus

"Avons-nous convergé vers une décision ? Souhaitez-vous formaliser le choix de <option> ?"

Si oui → indiquer : "Lancez `/adr approve` pour formaliser la décision."
Si non → continuer la discussion.

**Note :** la transition DISCUSSING→DECIDING (`FORMALIZE_DECISION`) n'a pas de guard artefact — `write_artifact` n'est pas requis avant cet événement.

### Step 7: Rule candidates

Si pendant la discussion un pattern ou une convention émerge, ajouter dans `rule-candidates.md` :

```
## [discussion] <règle en une ligne>
- **Domaine** : <architecture|sécurité|infra|api|transversal>
- **Contexte** : <argument ou moment de la discussion qui l'a fait émerger>
- **Décision** : <convention proposée>
```

### Step 8: Append log.md (au fil de la discussion)

Après chaque échange significatif :
```
Discussion — argument retenu : <argument>
Discussion — option <X> rejetée : <raison>
Discussion — consensus vers <option> : <résumé>
```
