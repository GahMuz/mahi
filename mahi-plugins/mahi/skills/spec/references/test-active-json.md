# Vérification : format de active.json

## Critères
- [ ] active.json contient le champ "workflowId" non nul
- [ ] active.json contient les champs "type", "id", "path", "activatedAt"
- [ ] active.json NE contient PAS de champ "currentPhase"

## Commande de vérification
```
cat .sdd/local/active.json | grep 'workflowId'
cat .sdd/local/active.json | grep -v 'currentPhase'
```

## Format attendu

```json
{
  "type": "spec",
  "id": "<kebab-titre>",
  "path": ".sdd/specs/YYYY/MM/<kebab-titre>",
  "activatedAt": "<ISO-8601>",
  "workflowId": "<uuid-retourné-par-mcp__plugin_mahi_mahi__create_workflow>"
}
```

## Champs interdits

Les champs suivants NE DOIVENT PAS figurer dans active.json pour mahi :
- `currentPhase` — la phase courante est lue via `mcp__plugin_mahi_mahi__get_workflow(workflowId)`, pas stockée localement
- `phases` — géré côté serveur Mahi
- `progress` — géré côté serveur Mahi

## Vérification post-`/spec new`

Après exécution de `/spec new <titre>` :
1. active.json doit exister dans `.sdd/local/`
2. `workflowId` doit être une valeur non vide (UUID retourné par `mcp__plugin_mahi_mahi__create_workflow`)
3. Aucune référence à un état local de phase

## Vérification post-`/spec open`

Après exécution de `/spec open <titre>` :
1. active.json doit contenir le `workflowId` du spec ouvert
2. La phase courante est lue via `mcp__plugin_mahi_mahi__get_workflow(workflowId)` — pas depuis active.json
