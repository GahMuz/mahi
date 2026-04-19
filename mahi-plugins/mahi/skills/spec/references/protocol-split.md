# Phase : Split

Split an active spec into two specs when distinct concerns are mixed. All output in French.

## When to Use

Triggered by `/spec split [<new-title>]` or recommended proactively when concern mixing is detected during requirements or design.

Applicable phases: `requirements`, `design`, `planning`.
Not recommended during `implementation` — finish the current wave first, then split.

## Process

### Step 1: Identify Active Spec
Read `.mahi/specs/registry.json`. If argument provided, match by title. Otherwise show active non-completed specs and ask which to split.

### Step 2: Load All Artifacts
Read the spec's documents based on current phase:
- Always: `requirement.md`
- If design or later: `design.md`
- If planning or later: `plan.md`
- Always: `log.md`

Obtenir l'état courant via Mahi MCP :
```
mcp__plugin_mahi_mahi__get_workflow(workflowId: <lire depuis active.json>)
```
> Note mahi : il n'y a pas de lecture de state.json.
> La phase courante est lue depuis la réponse de `mcp__plugin_mahi_mahi__get_workflow`.

### Step 3: Analyse Concerns
Group items by distinct concern. Look for:
- **Different domains** — e.g., refactoring existing code vs. building a new generic system
- **Different stakeholders** — items serving different users or teams
- **Independent deliverability** — items that could ship at different times without dependency
- **Different risk profiles** — a safe refactor bundled with an exploratory new system
- **"While we're at it"** patterns — items added during elicitation that belong to a different scope

Present the proposed grouping in French:

```
## Analyse de la spec : <titre>

### Préoccupation A — <label> (conservée dans cette spec)
- REQ-001 : <résumé>
- REQ-002 : <résumé>
- DES-001 : <résumé> (si applicable)

### Préoccupation B — <label> (nouvelle spec)
- REQ-003 : <résumé>
- REQ-004 : <résumé>
- DES-002 : <résumé> (si applicable)

Cette séparation vous convient-elle ? Souhaitez-vous ajuster la répartition ?
```

Allow the user to move items between groups before proceeding.

### Step 3b: Dependency Cycle Detection (mandatory before proceeding)

Analyse the proposed groups for cross-dependencies **in both directions**.

For each item in group A: does it reference (REQ, DES, or TASK) an item in group B?
For each item in group B: does it reference an item in group A?

**If A→B AND B→A** : cycle detected — the split is invalid as proposed.

Present the situation clearly:

```
Dépendances cycliques détectées — ce découpage n'est pas viable :

→ Préoccupation A dépend de B :
  - DES-002 (A) implémente REQ-004 (B)

→ Préoccupation B dépend de A :
  - REQ-004 (B) requiert le refactoring de REQ-001 (A) pour fonctionner

Un découpage A/B crée une dépendance mutuelle : ni l'une ni l'autre ne peut
être livrée indépendamment.
```

Then present **two resolution options** :

**Option 1 — Annuler le split**
Conserver tout dans la spec originale et reprendre la définition des exigences.

**Option 2 — Découpage en 3 specs**
Extraire la partie partagée (le "système générique") en une spec autonome sans dépendance amont.

```
Découpage suggéré en 3 specs :

Spec 1 : <original> — <domaine A sans la partie partagée>
  REQ-001, REQ-002 (refactoring pur, sans référence à B)

Spec 2 : <nouveau-générique> — <système générique autonome>
  REQ-003 (système générique, aucune dépendance vers A ou B)

Spec 3 : <nouveau-intégration> — <déploiement sur le domaine A>
  REQ-004 (intègre Spec 2 sur Spec 1 — dépend de 1 et 2, mais 1 et 2 ne dépendent pas d'elle)

Ordre de livraison : Spec 1 ∥ Spec 2 → Spec 3
```

Si l'utilisateur choisit l'option 2 avec plusieurs specs : appliquer le Step 4 et suivants
pour chaque nouvelle spec à créer, dans l'ordre (la plus indépendante d'abord).

**Règle fondamentale :** Le graphe de dépendances entre specs doit être un DAG
(Directed Acyclic Graph) — aucun cycle n'est acceptable.

### Step 4: Name the New Spec(s)
If `<new-title>` was provided as argument, use it. Otherwise ask:
"Quel titre pour la nouvelle spec ? (kebab-case généré automatiquement)"

### Step 5: Create New Spec Directory
Note current `YYYY/MM`. Create:
```bash
mkdir -p .mahi/specs/YYYY/MM/<new-kebab-title>/reviews
```

### Step 6: Write New Spec Artifacts

#### requirement.md
Copy selected REQ items verbatim (preserve IDs — do NOT renumber).
If a copied REQ depends on a REQ staying in the original spec, add:
```
> Dépend de [REQ-xxx] dans la spec `<original-spec-id>` — coordonner la livraison.
```

#### design.md (if design phase or later)
Copy selected DES items verbatim (preserve IDs).
For each DES that references a REQ now in the original spec, add the same cross-spec dependency note.

#### plan.md (if planning phase or later)
Copy selected TASK items verbatim (preserve IDs).
Cross-spec TASK dependencies: add a note under the affected TASK.

#### log.md
```markdown
# Journal — <new-title>

## <ISO-8601> — Création par division

Spec créée par division de `<original-spec-id>` (<original-title>).

**Raison du split :** <concern detected / user-stated reason>

**Éléments transférés :**
- <REQ-xxx>, <REQ-yyy> depuis requirement.md
- <DES-xxx> depuis design.md (si applicable)
- <TASK-xxx> depuis plan.md (si applicable)
```

#### Création du workflow Mahi pour la nouvelle spec
```
mcp__plugin_mahi_mahi__create_workflow(
  flowId: <new-spec-id>,
  workflowType: "spec"
)
```
Stocker le `workflowId` retourné pour écriture dans `active.json` de la nouvelle spec.

> Note mahi : il n'y a pas de création de state.json.
> La phase initiale est déterminée par le serveur Mahi selon le contenu transféré.

Si des REQs ET des DESs sont transférés : déclencher `mcp__plugin_mahi_mahi__fire_event(workflowId, event="APPROVE_REQUIREMENTS")` pour passer en design.
Si des REQs, DESs ET TASKs sont transférés : déclencher `APPROVE_REQUIREMENTS` puis `APPROVE_DESIGN` (requirements → design → worktree → planning).

### Step 7: Update Original Spec

#### requirement.md
For each transferred REQ, replace the item body with:
```
> Transféré vers la spec `<new-spec-id>` (<new-title>). [Voir REQ-xxx](.mahi/specs/YYYY/MM/<new-spec-id>/requirement.md)
```
Keep the REQ-xxx ID in place — never reuse it.

#### design.md / plan.md
Same treatment for transferred DES and TASK items.

#### log.md
Append entry:
```markdown
## <ISO-8601> — Division de spec

Préoccupation `<concern B label>` extraite vers la spec `<new-spec-id>`.

**Éléments transférés :** REQ-xxx, REQ-yyy, DES-xxx (si applicable), TASK-xxx (si applicable)
**Raison :** <user-stated reason>
```

### Step 8: Update Registry
Call `mcp__plugin_mahi_mahi__update_registry(<new-spec-id>, <phase>, title, period)` to add the new spec row.
If the original spec's status changed: call `mcp__plugin_mahi_mahi__update_registry(<original-spec-id>, <phase>)` to update it.

### Step 8b: Split Memory Entry
Follow the **Impact sur SPLIT** section in `references/protocol-context.md` to distribute the memory entry between the two specs.

### Step 9: Activate One Spec

Ask the user which spec to continue (in French):

```
Division effectuée :

A : <original-title> — conserve REQ-001, REQ-002, DES-001  (phase : <phase>)
B : <new-title>      — contient REQ-003, REQ-004, DES-002  (phase : <phase>)

Références croisées documentées dans les deux specs.

Quelle spec souhaitez-vous continuer ? (A / B)
```

Once the user answers:
- Call `mcp__plugin_mahi_mahi__activate(specId, "spec", path, workflowId)` for the chosen spec.
- Call `mcp__plugin_mahi_mahi__update_state(specPath, <initialPhase>, null)` to initialize state.json for the new derived spec.
- The other spec is ready to open later with `/spec open <titre>`.

## Cross-Reference Integrity Rules

- **Never renumber IDs** — IDs are stable across the split
- **Never delete items** — replaced with a transfer stub
- **Cross-spec references** — always noted with both spec path and ID
- **Orphan check** — after split, verify no DES in either spec references a REQ that was moved without a cross-reference note
