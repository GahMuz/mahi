---
name: adr
description: "Ce skill est utilisé quand l'utilisateur invoque '/adr' pour gérer les Architecture Decision Records. Gère 'new adr', 'open adr' (charge le contexte + reprend le workflow), 'recap', 'approve phase', 'close adr', 'switch adr'. Orchestre le cycle de vie complet : cadrage du problème → exploration des options → discussion → décision → rétrospective."
argument-hint: "new <titre> | open [titre] | recap | approve | close | switch <titre>"
context: fork
allowed-tools: ["Read", "Write", "Edit", "Bash", "Glob", "Grep", "Agent", "AskUserQuestion", "mcp__plugin_mahi_mahi__*"]
---

# ADR Workflow Orchestrator

All communication with the user MUST be in French.

## ABSOLUTE RULES — non-negotiable

1. **Aucune logique FSM locale.** Toutes les transitions de phase sont gérées par le serveur Mahi via les appels MCP. Si `mcp__plugin_mahi_mahi__fire_event` retourne une erreur, l'afficher en français et stopper — ne jamais tenter une transition locale.

2. **Jamais lire `active.json` directement.** Toujours utiliser `mcp__plugin_mahi_mahi__get_active()`.

3. **Aucune implémentation.** Un ADR produit uniquement des documents (framing.md, options.md, adr.md). Pas de code, pas de branches, pas de worktrees.

4. **`write_artifact` avant `fire_event` pour les transitions guardées.** Les transitions FRAMING→EXPLORING et EXPLORING→DISCUSSING et DECIDING→RETROSPECTIVE nécessitent que l'artefact correspondant soit VALID côté serveur.

## Local Active Item

The currently active item (spec or ADR) is tracked in `.mahi/local/active.json` — gitignored, machine-local, never committed. **Always read it via `mcp__plugin_mahi_mahi__get_active()` — never with the `Read` tool directly.**

```json
{ "type": "adr", "id": "gestion-secrets-spring", "path": ".mahi/decisions/2026/04/gestion-secrets-spring", "activatedAt": "ISO-8601", "workflowId": "<uuid>" }
```

**Rules:**
- Only one item (spec or ADR) can be active at a time. This single file enforces the constraint.
- `new`, `open`, `switch` are the only commands that write this file (via `mcp__plugin_mahi_mahi__activate`).
- All other commands fail immediately if `mcp__plugin_mahi_mahi__get_active()` returns null or has `type != "adr"` : "Aucun ADR actif. Lancez `/adr open <titre>` pour en ouvrir un."
- `new` and `open` call `mcp__plugin_mahi_mahi__get_active()` : if present with any type, execute the appropriate CLOSE (spec or ADR) before continuing.

## Parse Arguments

Extract subcommand from user input:
- `new <titre>` → START_NEW
- `open [titre]` → OPEN
- `recap` → RECAP
- `approve` → APPROVE
- `close` → CLOSE
- `switch <titre>` → SWITCH
- no args → CHECK_STATE

### Titre kebab-case

Même règle que `/spec new` : `<titre>` = 2-4 premiers mots en kebab-case. Si la ligne après `new` dépasse 5 mots ou contient des verbes impératifs, demander confirmation via `AskUserQuestion`.

## CHECK_STATE

1. Vérifier que `.mahi/config.json` existe. Si absent : "Lancez `/init` d'abord pour configurer le projet."
2. Appeler `mcp__plugin_mahi_mahi__get_active()`. Si présent avec `type="adr"` : appeler `mcp__plugin_mahi_mahi__get_workflow(workflowId)` pour récupérer la phase courante — si l'appel échoue, afficher : "Le serveur Mahi n'est pas démarré ou ne répond pas. Vérifiez que le plugin `mahi` est actif et que le processus Java est lancé." et stopper. Afficher l'ADR et sa phase. Si absent ou `type="spec"` : "Aucun ADR actif — lancez `/adr new <titre>` ou `/adr open <titre>`."

## START_NEW

0. Appeler `mcp__plugin_mahi_mahi__get_active()`. Si présent : exécuter le CLOSE approprié (`type="spec"` → spec CLOSE, `type="adr"` → ADR CLOSE), puis continuer.
1. Vérifier que `.mahi/config.json` existe.
2. Convertir le `<titre>` en kebab-case. Noter la date courante `YYYY/MM`.
3. Calculer le numéro ADR : lire `.mahi/registry.json`, compter les entrées avec `"type": "adr"` (tous statuts confondus) + 1 → `ADR-NNN` (format zéro-padded sur 3 chiffres). Si registry.json absent ou vide : ADR-001.
4. Créer `.mahi/decisions/YYYY/MM/<adr-id>/`.
5. Créer `log.md` (en-tête : `# Journal : <titre>` + entrée de création) et `rule-candidates.md` (en-tête : `# Règles candidates`).
6. Appeler `mcp__plugin_mahi_mahi__create_workflow(flowId=<adr-id>, workflowType="adr")` — stocker le `workflowId` retourné.
7. Appeler `mcp__plugin_mahi_mahi__update_registry(adrId, "adr", "framing", title, period)` pour enregistrer l'ADR dans le registre.
8. Appeler `mcp__plugin_mahi_mahi__activate(adrId, "adr", path, workflowId)` pour écrire `.mahi/local/active.json`.
9. Entrer la phase framing — lire et suivre `references/phase-framing.md`.

## OPEN

0. Prévenir : "Pour un contexte propre, cette commande fonctionne mieux après un `/clear`. Si la session contient du contexte accumulé d'un travail précédent, les réponses futures pourraient être influencées par cet historique."
1. Lire `.mahi/registry.json`. Titre donné → trouver l'entrée correspondante. Pas de titre → lister les entrées non terminées, demander à l'utilisateur (en français).
2. Appeler `mcp__plugin_mahi_mahi__get_active()`. Si présent avec `type="spec"` : exécuter spec CLOSE. Si `type="adr"` avec id différent : exécuter ADR CLOSE. Si même id : aller directement au step 4.
3. Appeler `mcp__plugin_mahi_mahi__activate(adrId, "adr", path, workflowId)` pour écrire `.mahi/local/active.json`.
4. Charger le contexte selon l'ordre de priorité de `references/protocol-context.md` section **Chargement du contexte** — présenter le briefing avant de reprendre.
5. Appeler `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → `currentPhase`. Si l'appel échoue : "Le serveur Mahi n'est pas démarré ou ne répond pas. Vérifiez que le plugin `mahi` est actif et que le processus Java est lancé." et stopper.
6. Rapporter l'état (en français) et reprendre.

## RECAP

0. Appeler `mcp__plugin_mahi_mahi__get_active()`. Si null ou `type != "adr"` : échec.

Appeler `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → `currentPhase`.

Présenter un résumé structuré :

```
## Récap ADR — <titre> (ADR-NNN)

**Phase :** <phase courante>

### Problème
<1-2 phrases depuis framing.md>

### Options identifiées
- <option A> — <statut : en analyse | discutée | rejetée | finaliste>
- ...

### Arguments clés
- <argument ou contrainte important validé>

### Questions ouvertes
- [ ] <question non résolue>

### Commandes disponibles
<liste selon la phase — voir ci-dessous>
```

**Commandes par phase :**

**FRAMING :** `/adr approve` — valider le cadrage et passer à l'exploration / `/adr close` — sauvegarder et fermer

**EXPLORING :** `/adr approve` — valider les options et passer à la discussion / `/adr close` — sauvegarder et fermer

**DISCUSSING :** `/adr approve` — consensus atteint, formaliser la décision / `/adr close` — sauvegarder et fermer

**DECIDING :** `/adr approve` — ADR finalisé, lancer la rétrospective / `/adr close` — sauvegarder et fermer

**RETROSPECTIVE :** en cours — répondre aux propositions de règles une par une / `/adr approve` — finaliser

**DONE :** `/adr open <titre>` — consulter / `/spec new <titre>` — démarrer l'implémentation

## APPROVE

0. Appeler `mcp__plugin_mahi_mahi__get_active()`. Si null ou `type != "adr"` : échec.
1. Appeler `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → `currentPhase`.
2. Valider la sortie de la phase courante :
   - `FRAMING` : framing.md a un énoncé du problème + ≥1 contrainte
   - `EXPLORING` : options.md a ≥2 options avec pour/contre
   - `DISCUSSING` : log.md a ≥1 entrée de discussion, consensus mentionné
   - `DECIDING` : adr.md existe avec décision et justification
   - `RETROSPECTIVE` : règles candidates traitées
3. Avancer selon la table de transition :

| currentPhase | write_artifact | fire_event | Phase suivante | Référence |
|---|---|---|---|---|
| FRAMING | `("framing", <contenu framing.md>)` | `START_EXPLORATION` | EXPLORING | `references/phase-exploration.md` |
| EXPLORING | `("options", <contenu options.md>)` | `START_DISCUSSION` | DISCUSSING | `references/phase-discussion.md` |
| DISCUSSING | *(pas d'artefact)* | `FORMALIZE_DECISION` | DECIDING | `references/phase-decision.md` |
| DECIDING | `("adr", <contenu adr.md>)` | `START_RETROSPECTIVE` | RETROSPECTIVE | `references/phase-retro.md` |
| RETROSPECTIVE | `("retrospective", <résumé rétro>)` | `COMPLETE` | DONE | `references/phase-transition.md` |

   Si le serveur retourne une erreur : afficher le message d'erreur en français — ne pas tenter une transition locale.

4. Appeler `mcp__plugin_mahi_mahi__update_registry(adrId, "adr", <newPhase>)` pour mettre à jour le statut dans le registre.
5. Appeler `mcp__plugin_mahi_mahi__update_state(adrPath, <newPhase>, changelogEntry)` pour mettre à jour l'état.
6. Entrer la prochaine phase en lisant la référence correspondante.

## CLOSE

0. Appeler `mcp__plugin_mahi_mahi__get_active()`. Si null ou `type != "adr"` : échec — "Aucun ADR actif. Utilisez `/spec close` si une spec est active."
   Lire et suivre `references/protocol-context.md` section **CLOSE**.

## SWITCH

Exécuter OPEN sur l'ADR demandé, en sautant l'avertissement `/clear` du step 0 de OPEN (pas approprié lors d'un switch dans la même session). OPEN gère automatiquement la fermeture de l'item actif courant.

## Key Principles

**Feedback :** Toujours dire à l'utilisateur (en français) dans quelle phase il se trouve, ce qui s'est passé, et ce qui vient ensuite.

**Pas d'implémentation :** Un ADR produit uniquement des documents. Pas de code, pas de branches, pas de worktrees.

**Sécurité :** Pas de secrets ni de credentials dans les documents ADR.

**Token efficiency :** Charger les références de phase uniquement à l'entrée dans cette phase.

## Phase References

| Phase | Reference |
|-------|-----------|
| Framing | `references/phase-framing.md` |
| Exploration | `references/phase-exploration.md` |
| Discussion | `references/phase-discussion.md` |
| Decision | `references/phase-decision.md` |
| Retrospective | `references/phase-retro.md` |
| Transition | `references/phase-transition.md` |
| Context | `references/protocol-context.md` |
| State machine | `references/state-machine.md` |

## Related Skills

| Skill | Purpose |
|-------|---------|
| `/spec new <titre>` | Démarrer l'implémentation d'une décision ADR |
| `/status [--all]` | Vue d'ensemble de tous les workflows : actif, en cours, terminés |
| `/init` | Initialiser le projet Mahi |
