/# Phase : Design

All output in French.

## Process

### Step 1: Read Requirements
Load `.sdd/specs/<spec-path>/requirement.md`. Extraire :
- Tous les REQ items et leurs critères d'acceptation
- La section **"Contexte codebase"** : modules existants concernés, patterns en place, points d'attention — cette section oriente directement les décisions architecturales en phase design (quels modules étendre, quels patterns respecter, quelles contraintes techniques appliquer)

### Step 1b: Specs passés liés

Lire `.sdd/specs/registry.md`. Pour chaque spec avec statut `completed` ou `retrospective` :
- Comparer le titre et les mots-clés avec le spec courant
- Si lien probable : lire son `design.md`

Les designs passés liés fournissent :
- Des patterns architecturaux déjà validés à réutiliser ou étendre
- Des décisions DES déjà prises (éviter de les contredire sans raison)
- Des contrats d'interface existants à respecter

Inclure dans le contexte de conception :
```
💡 Specs liés : <titre-1>, <titre-2> — designs chargés comme contexte.
```
Si aucun spec lié : continuer sans mention.

### Step 2: Charger les règles (sdd-rules)
Glob `**/sdd-rules/SKILL.md` → lire le fichier, exécuter le protocole de chargement (Étapes 1, 2, 3).

### Step 2b: Impact analysis graphe (sdd-graph) — AVANT rédaction des DES

C'est le moment le plus important pour l'impact cross-module : le design n'est pas encore validé, on peut encore changer l'approche.

Si `.sdd/graph/manifest.json` existe avec des graphes frais :

Pour chaque service ou entité évoqué dans les REQ, dispatcher en parallèle :
```
Agent({
  description: "Impact graphe : <service ou entité>",
  subagent_type: "sdd-graph:graph-query",
  model: "haiku",
  prompt: "Blast radius : <service ou entité>. Lister callers directs, modules dépendants, couplage afférent/efférent."
})
```

Intégrer les résultats dans la rédaction de chaque DES concerné :
- Blast radius élevé (>3 modules ou >5 callers) → le DES DOIT mentionner l'impact cross-module et proposer une stratégie (interface partagée, anti-corruption layer, ou découpage)
- Service injecté dans plusieurs modules → le DES DOIT spécifier la stratégie de non-régression des callers existants
- Entité avec relations `CASCADE` → le DES DOIT couvrir l'impact sur les entités associées

Résultat : les DES reflètent dès leur rédaction les contraintes structurelles réelles du codebase.

Si graphe absent ou stale → passer directement à Step 2c.

### Step 2c: Deep Investigation (if needed)
Le contexte codebase fourni par la phase requirements et le graphe (Step 2b) couvrent les zones déjà identifiées.
Dispatcher `spec-deep-dive` uniquement si une question spécifique reste sans réponse après ces deux sources (ex: impact sur un module non encore analysé, contrainte de performance à mesurer) :
```
Agent({ description: "Analyse approfondie du codebase", subagent_type: "sdd-spec:spec-deep-dive", model: "opus", prompt: "<question ciblée>" })
```
Optional — ne pas dispatcher si le contexte codebase + graphe répondent déjà aux questions architecturales.

### Step 3: Design Each Requirement Group
For each logical group of related requirements:
1. Assign DES-xxx ID
2. Describe problem (in French)
3. Pour les décisions architecturales non triviales : proposer 2-3 approches avec avantages/inconvénients avant de choisir. Présenter à l'utilisateur et attendre son choix. Pour les DES simples, une seule approche avec justification suffit.
4. Documenter l'approche retenue avec la raison du choix et les alternatives rejetées
5. Note tradeoffs
6. Si le DES touche du code existant : préférer étendre les patterns en place plutôt qu'en introduire de nouveaux. Ne pas proposer de refactoring hors périmètre.
7. List: `Implémente : [REQ-001, REQ-002]`
8. Define **Contrat de test** : behaviors to verify (derived from REQ acceptance criteria), edge cases, cross-module integrations to test

### Step 4: Explore Alternatives (in French)
For non-trivial decisions, present options:
- "Option A : <approche> — avantages : ..., inconvénients : ..."
- "Option B : <approche> — avantages : ..., inconvénients : ..."
- "Je recommande l'option A car..."
- Let user decide

### Step 5: Validate Against SOLID Principles

Every design section must be checked against SOLID:

- **S — Single Responsibility** : Chaque module/classe a une seule raison de changer
- **O — Open/Closed** : Ouvert à l'extension, fermé à la modification
- **L — Liskov Substitution** : Les sous-types doivent être substituables à leurs types de base
- **I — Interface Segregation** : Préférer plusieurs interfaces spécifiques à une interface générale
- **D — Dependency Inversion** : Dépendre d'abstractions, pas d'implémentations concrètes

For each DES section, verify the approach respects these principles. If a violation is found, revise the design or document the justified exception.

### Step 6: Validate Against Rules
Apply the rules loaded in Step 2 against each DES section:
- Check design against project conventions
- Verify technology choices align with project stack
- Confirm architectural patterns match

**If a plugin rule conflicts with a project rule:**
Do NOT silently choose one over the other. Present the conflict to the user in French:
- "Conflit détecté entre les principes SOLID et les règles projet :"
- Explain the SOLID principle at stake
- Explain the project rule that contradicts it
- "Quelle direction souhaitez-vous prendre ?"
- Wait for user decision before proceeding

### Step 6a: Auto-revue du design

Avant de dispatcher spec-design-validator, relire design.md en 3 passes successives :

**Pass 1 — Complétude**
- Chaque DES a-t-il un "Contrat de test" ?
- Les dépendances entre DES sont-elles déclarées ?
- La table de couverture REQ → DES est-elle présente ?
- Chaque DES a-t-il un ID, une approche et une justification ?

**Pass 2 — Correction**
- Pas de décisions sans justification ?
- Les alternatives ont-elles été genuinement considérées ?
- Les principes SOLID sont-ils vérifiés pour chaque DES ?
- Les contrats de test sont-ils dérivés des critères d'acceptation REQ ?

**Pass 3 — Cohérence**
- Les DES sont-ils cohérents avec les REQs correspondants ?
- Pas de contradiction avec les designs passés liés chargés en contexte ?
- La couverture REQ → DES ne laisse aucun REQ avec ❌ inexpliqué ?

Condition d'arrêt : 2 passes consécutives sans nouveau problème, ou 3 passes maximum.
Si des corrections ont été appliquées : noter le nombre avant de déléguer la validation formelle.

### Step 6b: Valider le design (spec-design-validator)

Dispatcher l'agent de validation pour vérification automatique règles + testabilité :
```
Agent({
  description: "Valider design <spec-id>",
  subagent_type: "sdd-spec:spec-design-validator",
  model: <from config.models.design-validator, default "sonnet">,
  prompt: "Spec path: <spec-path>"
})
```
L'agent itère jusqu'à zéro violation corrigeable automatiquement.
Les violations résiduelles nécessitant une décision architecturale sont présentées à l'utilisateur avant de continuer.

### Step 7: Present Design
Present complete design.md (in French):
- All DES items with approaches and rationale
- Cross-references to REQs
- SOLID compliance notes
- "Cette conception vous convient-elle ? Des sections à revoir ?"

### Step 7b: Concern Detection
After presenting the design, evaluate whether the DES sections reveal distinct architectural concerns that would benefit from separate specs:
- Sections with no shared dependencies or interfaces
- Sections with very different technology stacks or delivery timelines
- A section that introduces a reusable generic system alongside a domain-specific feature

**If detected**, add a non-blocking advisory:

```
💡 La conception révèle deux préoccupations architecturales distinctes :
- A : <label> — DES-001, DES-002 (spécifique au domaine)
- B : <label> — DES-003 (système générique réutilisable)

Séparer B dans une spec dédiée permettrait de la livrer et réutiliser indépendamment.
Souhaitez-vous faire un `/spec split` maintenant ?
```

Advisory only — if the user declines, proceed without raising it again.

### Step 8: Generate Coverage Mapping
Add a "Couverture des exigences" table at the end of design.md:
- List every REQ-xxx
- Map to which DES-xxx covers it
- Mark ✅ if covered, ❌ if not
- Any ❌ must be addressed before approval

### Step 9: Save
Write design.md using template.
Après écriture du fichier, appeler :
```
mahi_write_artifact(workflowId: <depuis active.json>, artifactKey: "design.md", content: <contenu complet>)
```
Par élément DES-xxx finalisé, appeler :
```
mahi_add_design_info(workflowId: <depuis active.json>, id: "DES-xxx", title: "<titre>", implementsReqs: ["REQ-xxx", ...])
```
Append log.md entry: date, "Phase conception", DES sections créées, décisions SOLID, conflits résolus.

### Step 10: Await Approval
"La conception est prête pour relecture. Lancez `/spec approve` pour passer à la planification."

## Quality Criteria
- Coverage mapping table complete (no ❌)
- Every REQ addressed by >= 1 DES
- Every DES has a "Contrat de test" section
- SOLID principles respected (or exceptions justified)
- Alternatives genuinely considered
- Tradeoffs honestly stated
- Implementable in small tasks
- Project rules respected (conflicts resolved with user)
- Patterns existants respectés : la conception suit les conventions du codebase plutôt que d'en introduire de nouvelles sans justification
- spec-design-validator passed (zero auto-fixable violations)
- `mahi_write_artifact` et `mahi_add_design_info` appelés après finalisation

## Formatting Rules (apply when writing design.md)
- Maximum line length : 200 characters — wrap longer lines
- Never use comma-separated inline lists with more than 2 items :
  convert to bullet points
