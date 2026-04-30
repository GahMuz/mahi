# Gestion du contexte ADR (open / close / switch)

Procédures pour sauvegarder et restaurer le contexte de travail d'un ADR.
Toute communication en français.

## Deux niveaux de persistance

| Niveau | Fichier | Portée | Usage |
|--------|---------|--------|-------|
| **Partagé** | `.mahi/work/adr/YYYY/MM/<id>/context.md` | Commité dans le repo — accessible à tous les développeurs | Source de vérité partagée |
| **Local** | `adr_<id>.md` dans le memory Claude Code | Machine locale uniquement (`~/.claude/projects/`) | Cache de session, rechargement rapide |

**Règle :** `/adr close` écrit les deux. `/adr open` charge depuis le memory local s'il existe, sinon depuis `context.md`, sinon reconstitue depuis `mcp__plugin_mahi_mahi__get_workflow(workflowId)` + `log.md`.

Un développeur qui clone le repo et ouvre un ADR verra le contexte via `context.md` — pas le memory local de son collègue.

## Format de `context.md` (fichier repo)

```markdown
# Contexte ADR : <adr-id>

> Phase : <currentPhase>
> Mis à jour : <ISO-8601>

## Problème
<1-2 phrases rappelant la problématique>

## Options en cours
- <option A> — <statut : en analyse | discutée | rejetée | finaliste | choisie>
- <option B> — ...

## Arguments clés
- <argument important validé pendant la discussion>
- ...

## Contraintes actives
- <contrainte qui influence les options>
- ...

## Questions ouvertes
- [ ] <question non résolue impactant la décision>
- ...

## Dernières actions
- <action 1 — brève>
- <action 2 — brève>
```

## Format de l'entrée memory Claude Code (cache local)

**Nom de fichier :** `adr_<adr-id>.md`

```markdown
---
name: ADR : <adr-id>
description: <phase, 2-3 éléments concrets — ex: "phase discussion, 3 options, Vault favori, question licensing ouverte">
type: project
---

<même contenu que context.md>
```

**Règle de description :** doit contenir adr-id, phase, et 2-3 éléments concrets pour que Claude puisse juger la pertinence sans lire le contenu.

## Chargement du contexte (utilisé par OPEN)

Appelé par `/adr open` après identification de l'ADR. Établit l'ADR comme actif sur cette machine puis présente le contexte avant de reprendre le workflow.

### Charger le contexte (priorité décroissante)

**1. Memory Claude Code local** (`adr_<adr-id>.md`) — si présent, utiliser en priorité (session précédente sur cette machine).

**2. `context.md` dans le repo** (`.mahi/work/adr/YYYY/MM/<adr-id>/context.md`) — si présent, utiliser (contexte partagé par un collègue ou session précédente).

**3. Reconstitution** — si aucun des deux n'existe : appeler `mcp__plugin_mahi_mahi__get_workflow(workflowId)` pour obtenir la phase, les artifacts, les statuts courants et le `sessionContext` (si une session précédente a appelé `mcp__plugin_mahi_mahi__save_context`), puis lire `log.md` pour les actions passées.

Présenter en français :
```
## Reprise ADR : <adr-id>  [source: memory local | context.md | reconstitué]

Phase : <phase>
Problème : <problème>

Options en cours : ...
Arguments clés : ...
Questions ouvertes : ...
Dernières actions : ...

→ Lancez `/adr approve` pour avancer ou continuez la discussion.
```

## CLOSE

**Note :** Un ADR n'utilise pas de worktree — pas d'`ExitWorktree` dans cette procédure.

### Step 1 : Identifier l'ADR actif
Appeler `mcp__plugin_mahi_mahi__get_active()`. Vérifier `type == "adr"`. (Le handler parent a déjà échoué si absent.) Récupérer `flowId` et `workflowId`.

### Step 2 : Synthétiser le contexte
Depuis la conversation courante et les fichiers de l'ADR (`log.md`, `framing.md`, `options.md`, `adr.md` selon la phase) :
- Phase courante (via `mcp__plugin_mahi_mahi__get_workflow(workflowId)`)
- Problème
- Options et leur statut courant
- Arguments clés retenus
- Contraintes actives
- Questions ouvertes non résolues
- 3-5 dernières actions significatives

### Step 3 : Persister le contexte dans le serveur Mahi
Appeler `mcp__plugin_mahi_mahi__save_context` pour stocker les données structurées côté serveur :

```
mcp__plugin_mahi_mahi__save_context(flowId: <workflowId>, context: {
  lastAction: "<dernière action significative>",
  keyDecisions: ["<argument ou option retenue 1>", ...],
  openQuestions: ["<question 1>", ...],
  nextStep: "<prochaine action recommandée>"
})
```

Ce `SessionContext` est retourné dans `mcp__plugin_mahi_mahi__get_workflow` lors de la prochaine ouverture.

### Step 4 : Écrire `context.md` enrichi (repo — partagé)
Écrire `.mahi/work/adr/YYYY/MM/<adr-id>/context.md` en suivant le format ci-dessus (inclut toutes les sections : Problème, Options, Arguments, Contraintes, Questions, Dernières actions).
Ce fichier sera commité avec le reste de l'ADR — accessible à tous les développeurs.

### Step 5 : Écrire l'entrée memory Claude Code (local)
Écrire `adr_<adr-id>.md` dans le répertoire memory du projet (même contenu + frontmatter).
Mettre à jour `MEMORY.md` : ajouter ou mettre à jour la ligne :
```
- [ADR : <adr-id>](adr_<adr-id>.md) — <description courte de l'état actuel>
```

### Step 6 : Libérer l'ADR actif via le serveur Mahi
Appeler `mcp__plugin_mahi_mahi__deactivate()` pour supprimer `.mahi/.local/active.json` — plus d'ADR actif sur cette machine.

### Step 7 : Confirmer
```
Contexte sauvegardé :
- SessionContext persisté via mcp__plugin_mahi_mahi__save_context (retourné à la prochaine ouverture)
- context.md mis à jour (partageable via git)
- Memory local mis à jour (session suivante sur cette machine)

ADR fermé.

⚠ Lancez `/clear` maintenant pour purger le contexte de cette session.
  Rouvrez ensuite avec `/adr open <adr-id>` dans la session propre.
```
