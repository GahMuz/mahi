# Gestion du contexte de spec (open / close / switch)

Procédures pour sauvegarder et restaurer le contexte de travail d'une spec.
Toute communication en français.

## Deux niveaux de persistance

Le contexte d'une spec est persisté sur **deux niveaux complémentaires** :

| Niveau | Fichier | Portée | Usage |
|--------|---------|--------|-------|
| **Partagé** | `.mahi/specs/YYYY/MM/<id>/context.md` | Commité dans le repo — accessible à tous les développeurs | Source de vérité partagée |
| **Local** | `spec_<id>.md` dans le memory Claude Code | Machine locale uniquement (`~/.claude/projects/`) | Cache de session, rechargement rapide |

**Règle :** `/spec close` écrit les deux. `/spec open` charge depuis le memory local s'il existe, sinon depuis `context.md`, sinon reconstitue depuis `log.md` + `mcp__plugin_mahi_mahi__get_workflow(flowId)`.

Un développeur qui clone le repo et ouvre une spec verra le contexte via `context.md` — pas le memory local de son collègue.

## Format de `context.md` (fichier repo)

```markdown
# Contexte : <spec-id>

> Phase : <currentPhase>
> Mis à jour : <ISO-8601>

## Objectif
<1-2 phrases rappelant ce que cette spec cherche à accomplir>

## Décisions clés
- <DES-xxx ou REQ-xxx> : <décision prise et justification courte>
- ...

## Fichiers identifiés
- `<chemin>` — <rôle dans la spec>
- ...

## Questions ouvertes
- [ ] <question non résolue impactant la suite>
- ...

## Dernières actions
- <action 1 — brève>
- <action 2 — brève>
```

## Format de l'entrée memory Claude Code (cache local)

**Nom de fichier :** `spec_<spec-id>.md`

```markdown
---
name: Spec : <spec-id>
description: <phase, 2-3 éléments concrets — ex: "phase design, DES-001 approuvé, DES-002 en cours, travail sur Property.php">
type: project
---

<même contenu que context.md>
```

**Règle de description :** doit contenir spec-id, phase, et 2-3 éléments concrets pour que Claude puisse juger la pertinence sans lire le contenu.

## Chargement du contexte (utilisé par RESUME)

Appelé par `/spec open` après identification de la spec. Établit la spec comme active sur cette machine puis présente le contexte avant de reprendre le workflow.

### Charger le contexte (priorité décroissante)

**1. Memory Claude Code local** (`spec_<spec-id>.md`) — si présent, utiliser en priorité (session précédente sur cette machine).

**2. `context.md` dans le repo** (`.mahi/specs/YYYY/MM/<spec-id>/context.md`) — si présent, utiliser (contexte partagé par un collègue ou session précédente).

**3. Reconstitution** — si aucun des deux n'existe : appeler `mcp__plugin_mahi_mahi__get_workflow(flowId)` pour obtenir la phase, les artifacts, les statuts courants et le `sessionContext` (si une session précédente a appelé `mcp__plugin_mahi_mahi__save_context`), puis lire `log.md` pour les actions passées.

Présenter en français :
```
## Reprise : <spec-id>  [source: memory local | context.md | reconstitué]

Phase : <phase>
Objectif : <objectif>

Décisions clés : ...
Fichiers identifiés : ...
Questions ouvertes : ...
Dernières actions : ...

→ Lancez `/spec approve`, `/spec clarify`, ou continuez le travail.
```

## CLOSE

### Step 0 : Quitter le worktree
Appeler `ExitWorktree()` pour retourner au répertoire principal du dépôt avant la sauvegarde du contexte.

### Step 1 : Identifier la spec active
Lire `.mahi/local/active.json`. (Le handler parent a déjà échoué si absent.) Récupérer `flowId`.

### Step 2 : Synthétiser le contexte
Depuis la conversation courante et les fichiers de la spec (log.md, requirement.md, design.md) :
- Phase courante (via `mcp__plugin_mahi_mahi__get_workflow(flowId)`)
- Objectif
- Décisions clés (session courante + précédentes)
- Fichiers identifiés/modifiés
- Questions ouvertes non résolues
- 3-5 dernières actions significatives

### Step 3 : Persister le contexte dans le serveur Mahi
Appeler `mcp__plugin_mahi_mahi__save_context` pour stocker les données structurées côté serveur ET écrire `context.md` automatiquement (si `specPath` est dans les métadonnées du workflow) :
```
mcp__plugin_mahi_mahi__save_context(flowId: <depuis active.json>, context: {
  lastAction: "<dernière action significative>",
  keyDecisions: ["<décision 1>", "<décision 2>", ...],
  openQuestions: ["<question 1>", ...],
  nextStep: "<prochaine action recommandée>"
})
```
Ce `SessionContext` est retourné dans `mcp__plugin_mahi_mahi__get_workflow` lors de la prochaine ouverture.

### Step 4 : Écrire `context.md` enrichi (repo — partagé)
Écrire `.mahi/specs/YYYY/MM/<spec-id>/context.md` en suivant le format ci-dessus (inclut Fichiers identifiés et Dernières actions — plus riche que le SessionContext seul).
Ce fichier sera commité avec le reste de la spec — accessible à tous les développeurs.

### Step 5 : Écrire l'entrée memory Claude Code (local)
Écrire `spec_<spec-id>.md` dans le répertoire memory du projet (même contenu + frontmatter).
Mettre à jour `MEMORY.md` : ajouter ou mettre à jour la ligne :
```
- [Spec : <spec-id>](spec_<spec-id>.md) — <description courte de l'état actuel>
```

### Step 6 : Libérer la spec active via le serveur Mahi
Appeler `mcp__plugin_mahi_mahi__deactivate()` pour supprimer `.mahi/local/active.json` — plus de spec active sur cette machine.

### Step 7 : Confirmer
```
Contexte sauvegardé :
- SessionContext persisté via mcp__plugin_mahi_mahi__save_context (retourné à la prochaine ouverture)
- context.md mis à jour (partageable via git)
- Memory local mis à jour (session suivante sur cette machine)

Spec fermée.

⚠ Lancez `/clear` maintenant pour purger le contexte de cette session.
  Rouvrez ensuite avec `/spec open <spec-id>` (ou `/adr open <adr-id>`) dans la session propre.
```

## SWITCH

1. Si une spec est active dans cette session : exécuter CLOSE complet (sauvegarde contexte)
2. Exécuter OPEN sur la spec demandée (charge contexte + reprend le workflow)

```
Fermeture de <spec-ancienne>... context.md et memory mis à jour.
Reprise de <spec-nouvelle>...
<afficher contexte restauré>
```

## Impact sur SPLIT

Lors d'un `/spec split`, après création des specs :

1. Lire `context.md` de la spec originale (si existant) ET l'entrée memory locale (si existante)
2. Distribuer les entrées selon les items transférés :
   - **Décisions clés** : associer chaque décision à la spec dont elle couvre les REQ/DES
   - **Fichiers identifiés** : selon le domaine du fichier
   - **Questions ouvertes** : dupliquer si transversales, distribuer si spécifiques
   - **Dernières actions** : conserver dans l'originale, noter l'origine dans la nouvelle
3. Écrire `context.md` dans chaque nouveau répertoire de spec
4. Mettre à jour les entrées memory locales pour les deux specs
5. Mettre à jour MEMORY.md
