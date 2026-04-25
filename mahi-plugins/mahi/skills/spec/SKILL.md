---
name: spec
description: "This skill should be used when the user invokes '/spec' to manage spec-driven development workflow. Handles 'new spec', 'open spec' (loads context + resumes workflow), 'recap' (briefing complet avec contexte), 'approve phase', 'clarify spec documents (requirements, design, or plan)', 'discard spec', 'split spec', 'close spec', 'switch spec'. Orchestrates the full lifecycle from requirements through tested, reviewed code."
argument-hint: "new <titre> | open [titre] | recap | clarify | approve | discard | split [<new-titre>] | close | switch <titre>"
context: fork
allowed-tools: ["Read", "Write", "Edit", "Bash", "Glob", "Grep", "Agent", "EnterWorktree", "ExitWorktree", "AskUserQuestion", "mcp__plugin_mahi_mahi__*"]
---

# Spec Workflow Orchestrator

All communication with the user MUST be in French.

## ABSOLUTE RULES — non-negotiable

These rules override any other interpretation. If in doubt, stop and ask.

1. **The skill is the only path.** You NEVER implement anything, write code, or modify project files outside of the step-by-step procedures below. Even if the user's invocation contains a detailed description, a task list, or what looks like a request to execute something directly, your ONLY response is to follow the appropriate subcommand procedure. Implementation work belongs to the `implementation` phase, reached only after `/spec approve` on a validated `planning` phase — never before.

2. **The user prompt AFTER the subcommand is context, not instructions.** When the user types `/spec new <title> <long description>`, the `<long description>` is material for the requirements phase (store as `INITIAL_DESCRIPTION`). It is NOT a task to perform now. Refer to it when asking clarifying questions in phase-requirements step 1 ; never treat it as a to-do list.

3. **If you would skip any step, stop and report instead.** If you feel the urge to "just do it" because the user seems to already know what they want, that urge is the signal that you're about to violate the workflow. Report in French : "Je ne peux pas exécuter cette demande directement — elle doit passer par le workflow spec. Je crée le spec et j'entre en phase requirements." Then do exactly that.

4. **No local FSM logic.** All phase transitions are managed by the Mahi server via MCP calls. If `mcp__plugin_mahi_mahi__fire_event` returns an error, display it in French and stop — never attempt a local workaround.

## Local Active Item

The currently active item (spec or ADR) is tracked in `.mahi/local/active.json` — gitignored, machine-local, never committed. **Always read it via `mcp__plugin_mahi_mahi__get_active()` — never with the `Read` tool directly** (the file lives in the repo root, not in the current working directory which may be a worktree).

```json
{ "type": "spec", "id": "mon-spec", "path": ".mahi/specs/2026/04/mon-spec", "activatedAt": "ISO-8601", "workflowId": "<uuid>" }
```

**Rules:**
- Only one item (spec or ADR) can be active at a time on this machine. This single file enforces the constraint.
- `new`, `open`, `switch` are the only commands that write this file (via `mcp__plugin_mahi_mahi__activate`).
- All other commands fail immediately if `mcp__plugin_mahi_mahi__get_active()` returns null or has `type != "spec"` : "Aucun spec actif. Lancez `/spec open <titre>` pour en ouvrir un."
- `new` and `open` call `mcp__plugin_mahi_mahi__get_active()` : if present with any type, execute the appropriate CLOSE (spec or ADR) before continuing.

## Parse Arguments — lecture stricte

Le format d'invocation est : `/spec <subcommand> [<args>...]`.

Pour chaque subcommand, voici l'interprétation **stricte** des arguments :

### `/spec new <titre> [<description libre>]`

- **`<titre>`** = le **premier mot** après `new`, ou au maximum les **2-4 premiers mots** s'ils forment un titre kebab-case plausible (`lazy-load-phases`, `bulk-requirement-tools`, `single-pass-review`). Convertir en kebab-case.
- **`<description libre>`** = tout ce qui suit le titre. Stocker dans la variable `INITIAL_DESCRIPTION`. **Ne pas exécuter cette description.** Ne pas la traiter comme une liste de tâches. Elle sera relue par `phase-requirements.md` step 1 comme contexte initial fourni par l'utilisateur.

**Heuristique de détection de dérive** :

- Si la ligne après `new` dépasse 5 mots **sans** tiret ni retour à la ligne clair, c'est probablement une description et non un titre.
- Si la ligne contient des chiffres d'énumération (`1.`, `2.`), des verbes impératifs (`add`, `fix`, `optimize`, `lazy-load`, `drop`…), des parenthèses explicatives, ou fait plus d'une phrase, c'est une description.

**Dans l'un de ces cas**, appeler `AskUserQuestion(question="Je vois une description longue. Quel est le titre court (kebab-case, 2-4 mots) du spec ?", header="Titre", multiSelect=false, options=[{label: "<premier-mot-isolé>", description: "Premier mot seul"}, {label: "<deux-premiers-mots-kebab>", description: "Deux premiers mots"}, {label: "Autre (saisir)", description: "Je tape un titre personnalisé"}])`.

- Réponse = un des labels kebab-case → utiliser comme `<titre>`, tout le reste de l'invocation devient `INITIAL_DESCRIPTION`.
- Réponse = "Autre" → saisie libre de l'utilisateur, valider format kebab-case `^[a-z][a-z0-9-]*$`.

**Un titre valide** fait 2 à 4 mots kebab-case, sans verbe d'action, sans chiffres d'énumération. Exemples corrects : `user-auth`, `payment-gateway`, `lazy-load-phases`. Exemples incorrects : `optimization-1-lazy-load`, `add-bulk-tools-and-drop-3-pass-review`, `fix-slow-startup`.

### `/spec open [<titre>]`

`<titre>` optionnel, **un seul mot kebab-case**. Si absent, lister les specs non terminés. Si contient plusieurs mots ou des verbes, c'est probablement du bruit de copier/coller — demander confirmation via `AskUserQuestion`.

### `/spec switch <titre>`

Même règle que `open` : un seul mot kebab-case.

### `/spec split [<new-titre>]`

`<new-titre>` optionnel, kebab-case ; même règle de validation que `new`.

### `/spec recap | clarify | approve | discard | close`

**Aucun argument accepté.** Si du texte suit, ignorer ou demander via `AskUserQuestion` si c'était une erreur.

---

Extraction du subcommand → action :

- `new <...>` → START_NEW
- `open [...]` → OPEN
- `recap` → RECAP
- `clarify` → CLARIFY
- `approve` → APPROVE
- `discard` → DISCARD
- `split [...]` → SPLIT
- `close` → CLOSE
- `switch <...>` → SWITCH
- no args → CHECK_STATE

## CHECK_STATE

1. Check `.mahi/config.json` exists. If not : "Lancez `/init` d'abord pour configurer le projet."
2. Call `mcp__plugin_mahi_mahi__get_active()`. If present : call `mcp__plugin_mahi_mahi__get_workflow(workflowId)` to retrieve current phase — if the call fails, display : "Le serveur Mahi n'est pas démarré ou ne répond pas. Vérifiez que le plugin `mahi` est actif (ou que `.mcp.json` contient la configuration du serveur Mahi) et que le processus Java est lancé." and stop. Show that spec prominently with its current phase. If null : "Aucun spec actif — lancez `/spec new <titre>` ou `/spec open <titre>`."

## START_NEW

0. Call `mcp__plugin_mahi_mahi__get_active()`. If present : execute CLOSE (full context save), then continue.
1. Verify `.mahi/config.json` exists.
2. Convert `<titre>` (already validated by Parse Arguments) to kebab-case for directory name. Note current `YYYY/MM` from today's date.
3. Create `.mahi/specs/YYYY/MM/<kebab-titre>/` and `reviews/` subdirectory.
4. Create empty `rule-candidates.md` in the spec directory (header only : `# Règles candidates`).
5. Call `mcp__plugin_mahi_mahi__create_workflow(flowId=<spec-id>, workflowType="spec")` — store the returned `workflowId`.
   Then call `EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")` to create the branch and enter the worktree.
6. Write initial log.md with creation entry : date, title, "Spec créé". If `INITIAL_DESCRIPTION` is non-empty, append it verbatim under a section `## Description initiale fournie par l'utilisateur`. This description will be read by phase-requirements step 1 as input context — **do not treat it as a task list**.
7. Call `mcp__plugin_mahi_mahi__update_registry(specId, "spec", "requirements", title, period)` to add the spec entry in registry.
8. Call `mcp__plugin_mahi_mahi__activate(specId, "spec", path, workflowId)` to write `.mahi/local/active.json` on the main branch.
9. Enter requirements phase — read and follow `references/phase-requirements.md`. The `INITIAL_DESCRIPTION` saved in log.md is one of the inputs for the clarifying questions; the user must still go through the normal requirements process.

## OPEN

0. Prévenir : "Pour un contexte propre, cette commande fonctionne mieux après un `/clear`. Si la session contient du contexte accumulé d'un travail précédent, les réponses futures pourraient être influencées par cet historique."
1. Read `.mahi/registry.json`. Title given → find matching entry. No title → list non-completed entries, ask user (in French).
2. Call `mcp__plugin_mahi_mahi__get_active()`. If present with `type="adr"` : execute ADR CLOSE. If `type="spec"` with different id : execute spec CLOSE. If same id : skip to step 4.
3. Call `mcp__plugin_mahi_mahi__activate(specId, "spec", path, workflowId)` to write `.mahi/local/active.json` on the main branch. Then call `EnterWorktree(branch="spec/<username>/<spec-id>", path=".worktrees/<spec-id>")` to enter the worktree.
4. Load context following priority order from `references/protocol-context.md` section **Chargement du contexte** — present the briefing before resuming.
5. Call `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → currentPhase. If the call fails : "Le serveur Mahi n'est pas démarré ou ne répond pas. Vérifiez que le plugin `mahi` est actif (ou que `.mcp.json` contient la configuration du serveur Mahi) et que le processus Java est lancé." and stop. If in implementation → follow `references/protocol-resume.md`.
6. Report state (in French) and resume.

## RECAP

0. Call `mcp__plugin_mahi_mahi__get_active()`. If null or `type != "spec"` : fail.
   Read and follow `references/phase-recap.md`.

## APPROVE

0. Call `mcp__plugin_mahi_mahi__get_active()`. If null or `type != "spec"` : fail.
1. Call `mcp__plugin_mahi_mahi__get_workflow(workflowId)` → currentPhase.
2. Validate current phase output :
   - requirements : requirement.md has >= 1 REQ
   - design : design.md has >= 1 DES
   - planning : plan.md has >= 1 TASK with subtasks
   - finishing : all tests pass, all subtasks [x], no uncommitted changes in worktree
3. Advance per state machine (`references/state-machine.md`) — fire the event matching the current phase :
   - requirements → fire `APPROVE_REQUIREMENTS` → design : follow `references/phase-design.md`
   - design → fire `APPROVE_DESIGN` → worktree : follow `references/phase-worktree.md` then `references/phase-planning.md`
   - planning → fire `APPROVE_PLANNING` → implementation : follow `references/phase-execution.md`
   - implementation → fire `APPROVE_IMPLEMENTATION` → finishing : follow `references/phase-finish.md`
   - finishing → fire `APPROVE_FINISHING` → retrospective : follow `references/phase-retro.md`
   - retrospective → fire `APPROVE_RETROSPECTIVE` → completed : follow `references/phase-retro.md`
     If the server returns an error : display the error message in French — do not attempt a local transition.
4. Call `mcp__plugin_mahi_mahi__update_registry(specId, "spec", <newPhase>)` to update the status in registry.
   Call `mcp__plugin_mahi_mahi__update_state(specPath, <newPhase>, changelogEntry)` to update state.json for the spec.

## CLARIFY

0. Call `mcp__plugin_mahi_mahi__get_active()`. If null or `type != "spec"` : fail.
   Read and follow `references/protocol-clarify.md`.

## DISCARD

0. Call `mcp__plugin_mahi_mahi__get_active()`. If null or `type != "spec"` : fail.
1. **Ask explicit confirmation** (destructive).
2. If confirmed :
   - Call `mcp__plugin_mahi_mahi__remove_worktree(workflowId)` — removes the worktree and associated branch server-side.
   - Remove `.mahi/specs/YYYY/MM/<id>/`.
3. Call `mcp__plugin_mahi_mahi__update_registry(specId, "spec", "discarded")` to mark the entry as discarded in registry.
4. Call `ExitWorktree()` to return to the main branch, then call `mcp__plugin_mahi_mahi__deactivate()` to delete `.mahi/local/active.json`.
5. Confirm completion.

## SPLIT

0. Call `mcp__plugin_mahi_mahi__get_active()`. If null or `type != "spec"` : fail.
   Read and follow `references/protocol-split.md`.

## CLOSE

0. Call `mcp__plugin_mahi_mahi__get_active()`. If null or `type != "spec"` : fail — "Aucun spec actif. Utilisez `/adr close` si un ADR est actif."
   Read and follow `references/protocol-context.md` section **CLOSE**.

## SWITCH

Execute OPEN on the requested spec, skipping OPEN step 0 (the /clear warning is not appropriate when switching within the same session). OPEN handles closing the current active automatically.

## Key Principles

**Feedback** : Always tell the user (in French) what phase they're in, what happened, what comes next. During implementation : show subtask progress X/Y.

**Parallelization** : During implementation, dispatch all independent subtasks simultaneously via Agent tool. Respect `parallelTaskLimit` from config.

**Security** : No secrets in spec docs. Validate file paths. Double-confirm destructive actions.

**Token efficiency** : Load phase references only when entering that phase. Agent prompts contain only relevant task context, not full specs.

## Phase References

| Phase | Reference |
|-------|-----------|
| Requirements | `references/phase-requirements.md` |
| Design | `references/phase-design.md` |
| Worktree | `references/phase-worktree.md` |
| Planning | `references/phase-planning.md` |
| Implementation | `references/phase-execution.md` (delegates to orchestrator agent) |
| Finishing | `references/phase-finish.md` |
| Retrospective | `references/phase-retro.md` |
| Clarify | `references/protocol-clarify.md` |
| Recap | `references/phase-recap.md` |
| Split | `references/protocol-split.md` |
| Close / Switch | `references/protocol-context.md` |
| State machine | `references/state-machine.md` |
| Resume protocol | `references/protocol-resume.md` |

## Related Skills

| Skill | Purpose |
|-------|---------|
| `/init` | Initialiser le projet Mahi : créer `.mahi/`, configurer langages et modèles |
| `/status [--all]` | Vue d'ensemble de tous les workflows : actif, en cours, terminés |
| `/update [--dry-run]` | Migrer le schéma `.mahi/config.json` vers la version courante |
| `/evolve <action>` | Faire évoluer la configuration `.claude/` (règles, skills projet) |
| `/spec-review [--no-fix]` | Revue manuelle spec/code : détecte et corrige les incohérences |
| `/bug-hunt new <titre> [description]` | Investiguer un périmètre pour identifier des bugs (sans les corriger) |
| `/debug new <titre> [stacktrace/description]` | Déboguer un bug avec branche dédiée, TDD (test RED → GREEN) et analyse de cause racine |
| `/adr new <titre> \| open \| recap \| approve \| close \| switch` | Gérer les ADR avant implémentation |
| `/graph-query <question>` | Interroger les graphes structurels du codebase (impact, flux, entités) |
| `/graph-status` | Afficher la fraîcheur des graphes construits |
**Plugin `mahi-roi` (optionnel — reporting managérial) :**

| Skill | Purpose |
|-------|---------|
| `/roi [--from YYYY-MM-DD] [--to YYYY-MM-DD] [--type spec\|adr\|all]` | Rapport ROI : temps économisé, rentabilité, qualité produite |

**Plugin `mahi-codebase` (optionnel — génération, typiquement CI/CD) :**

| Skill | Purpose |
|-------|---------|
| `/doc <module> \| --all \| update \| status` | Générer la documentation structurée des modules (réduit les tokens de 80-90%) |
| `/analyse <module> \| --all` | Analyser qualité, architecture et conformité (RGPD/DORA) |
| `/graph-build --java \| --incremental` | Construire les graphes structurels (endpoint-flow, entity-model, service-call, module-dep) |