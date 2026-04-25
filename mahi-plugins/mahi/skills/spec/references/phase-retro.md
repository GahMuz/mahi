# Phase : Retrospective

All output in French.

## Purpose

Extraire les règles apprises pendant le spec, mettre à jour la documentation `.mahi/docs/`, et persister les apprentissages dans les fichiers `rules-*.md` du projet.

## Process

### Step 1: Extract Learnings

Lire dans cet ordre de priorité :

1. **`rule-candidates.md`** (source primaire) — règles capturées en temps réel par les agents pendant l'exécution et la revue. Lister chaque candidat.
2. **`log.md`** — décisions prises, contournements, points de friction
3. **`reviews/`** — problèmes récurrents signalés par le code-reviewer
4. **`baseline-tests.json` `breakingChanges`** — patterns de changements cassants

> Note mahi : il n'y a pas de lecture de state.json changelog.
> Les informations de clarification et de transition sont disponibles via :
> `mcp__plugin_mahi_mahi__get_workflow(workflowId: <lire depuis active.json>)`

Compiler la liste consolidée de candidats en dédupliquant les entrées qui couvrent le même pattern.

### Step 2: Update Module Documentation

Identifier les modules touchés (depuis les chemins de fichiers dans plan.md).
Pour chaque module ayant une doc existante dans `.mahi/docs/modules/<module>/` :
```
Agent({
  description: "Mettre à jour la doc du module <module>",
  subagent_type: "mahi:doc-generator",
  model: <from config.models.doc-generator, default "haiku">,
  prompt: "Mettre à jour la doc du module <module>. Chemin source: <path>. Doc existante: .mahi/docs/modules/<module>/module-<module>.md"
})
```
Si aucune doc existante pour un module touché : suggérer `/doc <module>` pour une génération initiale.

### Step 3: Categorize Rule Candidates

Classer chaque règle candidate par domaine :
- Règles controller → `rules-controller.md`
- Règles service → `rules-service.md`
- Règles entité/modèle → `rules-entity.md`
- Règles test → `rules-test.md`
- Règles API → `rules-api.md`
- Règles sécurité → `rules-security.md`
- Règles transversales → `rules.md`

Pour chaque règle candidate, avant de la présenter :
- **Sécurité** (comme `/sdd-evolve`) : pas de pattern d'injection, pas de credentials
- **Doublons** : Grep dans le fichier cible — si déjà présent, ignorer silencieusement

Si aucune règle candidate : passer à Step 5.

### Step 4: Present Rules One by One

Pour chaque règle candidate, présenter individuellement :

```
Proposition N/Total :

Fichier cible : rules-<domain>.md
Règle : "<règle>"
Contexte : <où découverte / combien de fois flaggée>

Appliquer ? (oui / non / modifier)
```

- **oui** → approuvée, passer à la suivante
- **non** → ignorée, passer à la suivante
- **modifier** → demander la version corrigée, passer à la suivante

### Step 5: Apply Approved Rules on Spec Branch

Si au moins une règle est approuvée :
1. S'assurer que le worktree de la spec est actif (branche `spec/<username>/<spec-id>`)
2. Créer/mettre à jour les fichiers `rules-*.md` dans `.claude/skills/rules-references/references/`
3. Si nouveau fichier `rules-*.md` créé : mettre à jour l'index dans `.claude/skills/rules-references/SKILL.md`
   (ajouter ligne : fichier, domaine, "Charger quand")
4. Vérification de granularité (comme `/sdd-evolve audit`) :
   - Fichier `rules-*.md` > 200 lignes → signaler
   - Règles domain-specific dans `rules.md` → proposer d'extraire dans `rules-*.md`
5. Commiter sur la branche spec : `chore(claude): apprentissages du spec <titre>`
6. Append log.md : "Rétrospective : X règles ajoutées dans Y fichiers."

Si aucune règle approuvée : skip.

### Step 6: Write Retrospective Document

Écrire `retrospective.md` dans `.mahi/specs/<spec-path>/` en suivant ce template :

```markdown
# Rétrospective : <spec-titre>

> Date : <ISO-8601>
> Durée totale : <durée en jours ou heures depuis la création>

## Ce qui a bien fonctionné
- <apprentissage positif 1>
- <apprentissage positif 2>

## Ce qui n'a pas fonctionné / axes d'amélioration
- <difficulté rencontrée et piste d'amélioration>

## Métriques
- REQ : <nombre>  |  DES : <nombre>  |  TASKs : <nombre>  |  Tests : <baseline → final>
- Changements cassants : <nombre> (<liste ou "aucun">)
- Revues de code : <nombre>

## Règles ajoutées
- <règle 1 → fichier cible>
- <ou "Aucune">

## Prochaines actions suggérées
- <action recommandée 1>
```

Marquer l'artifact comme valide côté serveur :
```
mcp__plugin_mahi_mahi__write_artifact(
  flowId: <lire depuis active.json>,
  artifactName: "retrospective",
  content: <contenu complet de retrospective.md>
)
```

### Step 7: Finalize

1. Déclencher la transition vers completed via Mahi MCP :
```
mcp__plugin_mahi_mahi__fire_event(
  workflowId: <lire depuis active.json>,
  event: "APPROVE_RETROSPECTIVE"
)
```
2. Mettre à jour registry.json : statut → `completed`.
3. Append log.md : "Rétrospective terminée. Spec complété."

> Note mahi : il n'y a pas de mise à jour de state.json.
> La transition vers `completed` est déclenchée via `mcp__plugin_mahi_mahi__fire_event`.

"Spec `<titre>` complété. Les fichiers sont conservés dans `.mahi/specs/<spec-path>/` pour référence future."
