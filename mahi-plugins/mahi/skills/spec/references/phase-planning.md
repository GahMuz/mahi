# Phase : Planning

All output in French.

## Process

### Step 1: Analyze Design
Read `.sdd/specs/<spec-path>/design.md`. For each DES item, identify:
- Code changes needed
- Files to create/modify
- Tests to write
- Logical implementation order

### Step 2: Create Parent Tasks
Group related work into parent tasks (TASK-xxx):
- Each parent = one logical feature unit (e.g., "Créer le CRUD Utilisateur")
- Assign sequential IDs
- Reference DES and REQ: `Implémente : [DES-001]`, `Satisfait : [REQ-001]`

### Step 3: Break Into Subtasks
For each parent, create subtasks (TASK-xxx.y):
- Each subtask = 2-5 min atomic unit
- Include: description, exact file paths, steps, verification command
- Identify dependencies within and across parents
- Prefix with `[ ]` status icon

**Structure RED/GREEN/REFACTOR :**
Pour toute tâche impliquant du nouveau code ou de la logique métier, décomposer en :
- `TASK-xxx.1 [RED]` : Écrire les tests en échec — dérivés du "Contrat de test" du DES correspondant
- `TASK-xxx.2 [GREEN]` : Implémenter le code minimal pour faire passer les tests
- `TASK-xxx.3 [REFACTOR]` : Nettoyer le code (optionnel si l'implémentation est déjà propre)
Exceptions : tâches de configuration, documentation, dépendances — pas de `[RED]` requis.

**Splitting guidelines:**
- One concern per subtask
- If > 3 files, consider splitting
- Never group `[RED]` and `[GREEN]` in the same subtask

### Step 4: Analyze Dependencies
Build dependency graph:
- Identify which subtasks can run in parallel
- Minimize dependency chains for maximum parallelism
- Circular dependencies = error, restructure
- Draw ASCII dependency graph

### Step 4b: Validation impact graphe (sdd-graph)

Si `.sdd/graph/manifest.json` existe avec `service-call` ou `module-dep` frais :

Pour chaque DES mentionnant un service ou une entité modifiée, dispatcher :
```
Agent({
  description: "Impact graphe : <service ou entité>",
  subagent_type: "sdd-graph:graph-query",
  model: "haiku",
  prompt: "Impact : <service ou entité>. Liste complète des callers et modules dépendants."
})
```

Vérifier que chaque caller identifié par le graphe est traité dans le plan :
- Caller dans un module différent → ajouter une sous-tâche `[VERIFY] Non-régression <module>` ou noter explicitement "hors périmètre" avec justification
- Module dépendant avec fort couplage (>5 imports) → annoter la TASK parente avec "⚠ Impact cross-module : <modules>"
- Aucune TASK ne doit ignorer silencieusement un caller identifié

Si graphe absent ou stale → continuer sans cette étape.

### Step 5: Embed Rules
Glob `**/sdd-rules/SKILL.md` → exécuter le protocole de chargement (plugin + projet + priorité).
Include a "Règles" checklist section at the top of plan.md with all verifiable rules loaded (plugin + projet). These checkboxes are verified post-implementation.

Verify plan doesn't violate any rules (e.g., a task modifying a generated file when rules say "pas de modification de fichiers générés"). Report conflicts in French.

### Step 6: Verify Coverage
- Every DES → >= 1 TASK
- Every REQ → >= 1 TASK (via DES)
- Every TASK → >= 1 subtask
- No orphan references
- Report gaps (in French)

### Step 6b: Vérifier le plan (spec-planner)

Dispatcher spec-planner pour vérification automatique de couverture et structure TDD :
```
Agent({
  description: "Vérifier le plan <spec-id>",
  subagent_type: "sdd-spec:spec-planner",
  model: <from config.models.planner, default "haiku">,
  prompt: "Spec path: <spec-path>"
})
```
- Si **APPROUVÉ** : passer à Step 7
- Si **back-pressure vers design** : retourner en phase design (contrats de test manquants)
- Si **back-pressure vers requirements** : retourner en phase requirements (REQ non couverts)
- Si corrections mineures appliquées automatiquement : re-lire plan.md avant Step 7

### Step 6c: Auto-relecture de plan.md

Avant de présenter le plan, relire plan.md en 3 passes successives :

**Pass 1 — Complétude**
- Chaque DES a-t-il >= 1 TASK correspondante ?
- Chaque TASK impliquant du code a-t-elle des subtasks RED/GREEN (et optionnellement REFACTOR) ?
- Les dépendances entre subtasks sont-elles déclarées ?
- Le graphe de dépendances ASCII est-il présent ?

**Pass 2 — Correction**
- Chaque subtask est-elle atomique (2-5 min) ?
- Aucune subtask ne regroupe RED et GREEN ?
- Les commandes de vérification sont-elles présentes sur chaque subtask ?
- Pas plus de 3 fichiers par subtask (sinon à splitter) ?

**Pass 3 — Cohérence**
- Les TASKs sont-elles cohérentes avec les approches décrites dans design.md ?
- Les TASKs hors RED/GREEN (config, doc) sont-elles justifiées comme exceptions ?
- Les totaux (X tâches, Y sous-tâches) sont-ils corrects ?

Condition d'arrêt : 2 passes consécutives sans nouveau problème, ou 3 passes maximum.
Si des corrections ont été appliquées : noter le nombre avant de présenter le plan.

### Step 7: Present Plan (in French)
Present plan.md:
- Task list with subtasks, dependencies, status icons
- Dependency graph
- Totals: "X tâches, Y sous-tâches, Z parallélisables dans le premier lot"
- "Relisez le plan. Des tâches à ajuster ?"

### Step 8: Save
Write plan.md using template.
Après écriture du fichier, appeler :
```
mcp__plugin_mahi_mahi__write_artifact(workflowId: <depuis active.json>, artifactKey: "plan.md", content: <contenu complet>)
```
Append log.md entry: date, "Phase planification", X tâches et Y sous-tâches créées, dépendances identifiées.

### Step 9: Await Approval
"Le plan est prêt. X tâches, Y sous-tâches. Lancez `/spec approve` pour démarrer l'implémentation."
