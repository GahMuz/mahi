# Phase : Reporting

**Objectif** : présenter les bugs au développeur, recueillir la validation individuelle,
créer les sessions `/debug` pré-remplies pour les bugs validés.

---

## Étape 0 — Analyse des causes racines (si ≥ 3 bugs)

Si le findings artifact contient **3 bugs ou plus**, dispatcher l'analyser en premier :

```
Agent({
  description: "Analyser les causes racines",
  subagent_type: "mahi:bug-hunt-analyser",
  model: "haiku",
  prompt: "huntPath: <huntPath>
    findings: <contenu du findings artifact>"
})
```

Présenter le rapport de regroupement à l'utilisateur avant la validation individuelle.
Pour les groupes avec **stratégie "1 session debug unifiée"** : traiter le groupe comme un seul bug lors de l'Étape 2 (titre = "Cause racine : <description>", description = bugs regroupés).

Si moins de 3 bugs : passer directement à l'Étape 1.

---

## Étape 1 — Présenter le résumé global

Afficher en français :
```
# Résultat du Bug Hunt : <titre>

N bugs identifiés : X critiques, Y majeurs, Z mineurs
<si analyse effectuée> M groupes de cause racine identifiés — <réduction> sessions debug économisées

Validation individuelle à suivre — chaque bug (ou groupe) sera présenté séparément.
```

---

## Étape 2 — Validation individuelle de chaque bug

Pour chaque bug du `findings` artifact, dans l'ordre de priorité (CRITIQUE d'abord) :

Afficher en français :
```
── BUG-00X [CRITIQUE] <titre> ──────────────────
Fichier : <file>:<line>
Couche  : <catégorie>
Problème : <explication>
Impact : <conséquence si non corrigé>
```

Puis appeler :
```
AskUserQuestion(
  question: "Créer une session /debug pour ce bug ?",
  header: "BUG-00X — <titre court>",
  multiSelect: false,
  options: [
    { label: "Oui", description: "Créer la session debug avec le contexte pré-rempli" },
    { label: "Non", description: "Écarter ce bug (faux positif ou connu)" },
    { label: "Modifier", description: "Ajuster la description avant de créer" }
  ]
)
```

- **"Oui"** → étape 3 pour ce bug, puis passer au suivant
- **"Non"** → noter comme écarté avec raison, passer au suivant
- **"Modifier"** → `AskUserQuestion` saisie libre ("Quelle correction apporter ?"),
  puis étape 3 avec la description corrigée

---

## Étape 3 — Créer la session /debug (pour chaque bug validé)

```
mcp__plugin_mahi_mahi__create_workflow(
  flowId: <debug-id>,      // id tiré du findings (kebab-case)
  workflowType: "debug"
)
mcp__plugin_mahi_mahi__update_registry(
  debugId, "debug", "reported", <titre-bug>, period
)
```

Créer le répertoire et écrire le fichier prefill via le **Write tool**
(PAS `write_artifact` MCP — bug-hunt est l'item actif) :

`.mahi/debug/YYYY/MM/<debug-id>/prefill.md` :

```markdown
# Contexte pré-rempli (depuis bug-hunt <hunt-id>)

## Titre
<titre du bug>

## Type
explanation

## Symptôme
<problème en une phrase>

## Point d'entrée
`<file>:<line>`

## Description détaillée
<explication complète du finding — couche, conditions, logique du bug>

## Conditions de déclenchement
<quand ce bug se manifeste — données d'entrée, état, scénario>

## Classes à analyser
<liste des classes du finding>

## Impact estimé
[CRITIQUE|MAJEUR|MINEUR] — <conséquence>

## Couche détectée
<Validation d'entrée | Complétude CRUD | Ressources | Concurrence | Logique métier | Side effects>
```

Confirmer : "Session debug `<debug-id>` créée — contexte pré-rempli.
Lancer avec `/debug open <debug-id>`."

---

## Étape 4 — Écrire l'artifact `bug-list`

Après avoir traité tous les bugs :

```
mcp__plugin_mahi_mahi__write_artifact(flowId, "bug-list", <contenu>)
```

```markdown
# Bug List : <titre>

## Sessions debug créées
| Id | Titre | Couche | Sévérité | Statut |
|----|-------|--------|----------|--------|
| `<debug-id>` | <titre> | <couche> | CRITIQUE | créé ✓ |
| `<debug-id>` | <titre> | <couche> | MAJEUR | créé ✓ |

## Bugs écartés
| Titre | Raison |
|-------|--------|
| <titre> | <raison — faux positif, comportement intentionnel, déjà connu> |

## À confirmer ultérieurement
| Titre | Question |
|-------|----------|
| <titre> | <ce qui était ambigu> |

## Prochaines étapes
Pour commencer à corriger un bug :
`/debug open <debug-id>`

Pour investiguer un autre périmètre :
`/bug-hunt new <nouveau-titre>`
```

---

## Étape 5 — Clore la session bug-hunt

Après `write_artifact("bug-list")`, appeler `/bug-hunt approve` → `COMPLETE` → DONE → CLOSE.

Afficher le récapitulatif final :
```
✅ Bug Hunt terminé : '<titre>'

N sessions debug créées : <liste des debug-id>
M bugs écartés

Prochaine étape :
  /debug open <premier-debug-id-critique>
```
