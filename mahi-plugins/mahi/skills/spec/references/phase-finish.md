# Phase : Finishing

All output in French.

## Process

### Step 1: Final Verification
In the worktree:
1. Run full test suite — all must pass
2. Check for uncommitted changes — commit if needed
3. Verify all subtasks in plan.md are `[x]`

Vérifier également que la phase retournée par le serveur est correcte :
```
mcp__plugin_mahi_mahi__get_workflow(workflowId: <lire depuis active.json>)
```
> Note mahi : ne pas lire state.json pour vérifier la phase courante.
> Utiliser toujours `mcp__plugin_mahi_mahi__get_workflow` comme source de vérité.

If any check fails, report and ask how to proceed.

### Step 2: Present Summary

```
Résumé du spec : <titre>
Branche : spec/<username>/<spec-id>
Tâches : X/X terminées
Sous-tâches : Y/Y terminées
Tests : Z passent
Fichiers modifiés : N
Changements cassants : K (voir baseline-tests.json)
```

If breaking changes exist, list them with test name and reason.

### Step 3: Present Options

1. **Valider** — tout est commité, pousser la branche et lancer la rétrospective. La PR sera créée manuellement via Bitbucket.
2. **Fermer** — sauvegarder le contexte et fermer le spec pour reprendre plus tard.
3. **Abandonner** — supprimer la branche et le worktree, annuler tous les changements.

"Quelle option choisissez-vous ?"

### Step 4: Execute Choice

#### Valider
1. Vérifier qu'il ne reste aucune modification non commitée dans le worktree.
   Si oui : commiter avant de continuer.
2. Pousser la branche :
```bash
git push -u origin spec/<username>/<spec-id>
```
3. "Branche `spec/<username>/<spec-id>` poussée. Créez votre PR via Bitbucket quand vous êtes prêt."
4. Déclencher la transition de phase via Mahi MCP :
```
mcp__plugin_mahi_mahi__fire_event(
  workflowId: <lire depuis active.json>,
  event: "APPROVE_FINISHING"
)
```
5. Mettre à jour registry.json : statut → `retrospective`.
6. Suivre `references/phase-retro.md`.

> Note mahi : la mise à jour de la phase se fait via `mcp__plugin_mahi_mahi__fire_event`,
> pas via écriture dans state.json.

#### Fermer
- Sauvegarder le contexte : suivre `references/protocol-context.md` section **CLOSE**.
- "Spec fermé. Relancez avec `/spec open <titre>` quand vous êtes prêt."

#### Abandonner
**Double confirmation** : "Êtes-vous sûr ? Cette action supprimera tous les changements non mergés."
Si des modifications non commitées existent : "Des modifications non commitées existent. Les abandonner aussi ?"

Supprimer le worktree via Mahi MCP :
```
mcp__plugin_mahi_mahi__remove_worktree(workflowId: <lire depuis active.json>)
```
> Note mahi : `mcp__plugin_mahi_mahi__remove_worktree` remplace les commandes git manuelles.

Mettre à jour le registre :
```
mcp__plugin_mahi_mahi__update_registry(specId, "abandoned")
```
Puis désactiver le spec :
```
mcp__plugin_mahi_mahi__deactivate()
```
- "Spec abandonné."
