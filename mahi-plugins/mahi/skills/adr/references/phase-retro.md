# Phase : Retrospective ADR

All output in French.

## Purpose

Extraire les règles et conventions architecturales identifiées pendant l'ADR et les persister dans les fichiers `rules-*.md` du projet.

## Process

### Step 1: Compiler les règles candidates

Lire dans cet ordre de priorité :

1. **`rule-candidates.md`** (source primaire) — règles capturées en temps réel pendant exploration, discussion et decision.
2. **`log.md`** — décisions et arguments notés au fil du processus
3. **`adr.md`** section "Règles impactées" — rules existantes remises en cause

Compiler la liste consolidée en dédupliquant les entrées qui couvrent le même pattern.

Si aucune règle candidate et aucune règle impactée : passer directement au Step 4.

### Step 2: Catégoriser les candidats

Classer chaque règle candidate par domaine :
- Règles controller → `rules-controller.md`
- Règles service → `rules-service.md`
- Règles entité/modèle → `rules-entity.md`
- Règles test → `rules-test.md`
- Règles API → `rules-api.md`
- Règles sécurité → `rules-security.md`
- Règles architecture → `rules-architecture.md`
- Règles transversales → `rules.md`

Pour chaque candidat :
- **Sécurité** : pas de pattern d'injection, pas de credentials
- **Doublons** : grep dans le fichier cible — si déjà présent, ignorer silencieusement

### Step 3: Présenter les candidats un par un

Pour chaque règle candidate :

```
Proposition N/Total :

Fichier cible : rules-<domaine>.md
Règle : "<règle>"
Contexte : <où découverte / phase de l'ADR>

Appliquer ? (oui / non / modifier)
```

- **oui** → approuvée, passer à la suivante
- **non** → ignorée, passer à la suivante
- **modifier** → demander la version corrigée, passer à la suivante

### Step 4: Traiter les règles impactées

Lire la section "Règles impactées" dans `adr.md`.

Pour chaque rule existante remise en cause par la décision ADR :

```
Mise à jour requise N/Total :

Fichier : rules-<domaine>.md
Règle actuelle : "<texte actuel>"
Nouvelle version : "<texte proposé suite à l'ADR>"
Raison : <décision ADR qui justifie le changement>

Appliquer la modification ? (oui / non / modifier)
```

### Step 5: Appliquer les changements approuvés

Si au moins une règle est approuvée ou modifiée :
1. Créer/mettre à jour les fichiers `rules-*.md` dans `.claude/skills/rules-references/references/`
2. Si nouveau fichier `rules-*.md` créé : mettre à jour l'index dans `.claude/skills/rules-references/SKILL.md`
   (ajouter ligne : fichier, domaine, "Charger quand")
3. Vérification de granularité :
   - Fichier `rules-*.md` > 200 lignes → signaler
   - Règles domain-specific dans `rules.md` → proposer d'extraire dans `rules-*.md`
4. Commiter directement sur la branche courante : `chore(claude): règles issues de l'ADR <adr-id>`
5. Append log.md : "Rétrospective : X règles ajoutées/mises à jour dans Y fichiers."

Si aucune règle : skip.

### Step 6: Décomposition en specs d'implémentation — OBLIGATOIRE

**Ne pas passer au Step 7 avant d'avoir terminé cette étape.**

Appeler l'outil `Agent` avec les paramètres suivants :
- `description` : `"Décomposer l'ADR <adrId> en specs"`
- `subagent_type` : `"mahi:adr-spec-decomposer"`
- `model` : `"sonnet"`
- `prompt` : texte multiligne contenant `adrId: <adrId>`, `adrPath: <adrPath>`, `workflowId: <workflowId>`

L'agent analyse l'ADR, propose les specs dérivés **un par un** à l'utilisateur (oui / non / modifier), et n'initialise que ceux approuvés. Attendre la fin de l'agent avant de passer au Step 7.

### Step 7: Marquer l'artefact et finaliser

1. Appeler `mcp__plugin_mahi_mahi__write_artifact(flowId: <workflowId>, artifactName: "retrospective", content: <résumé des règles appliquées ou "Aucune règle candidate">)`.
2. Appeler `mcp__plugin_mahi_mahi__fire_event(workflowId: <workflowId>, event: "COMPLETE")` — fait passer la FSM en DONE.
3. Appeler `mcp__plugin_mahi_mahi__update_registry(<adrId>, "adr", "completed")`.
4. Appeler `mcp__plugin_mahi_mahi__update_state(<adrPath>, "done", changelogEntry)`.
5. Appeler `mcp__plugin_mahi_mahi__deactivate()` pour libérer le slot actif.
6. Append log.md : "Rétrospective terminée. ADR complété."
7. Suivre `references/phase-transition.md`.
