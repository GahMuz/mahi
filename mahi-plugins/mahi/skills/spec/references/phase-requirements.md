# Phase : Requirements

All questions and output in French.

## ⚠️ Règle absolue de format

Le fichier `requirement.md` contient **uniquement** des user stories structurées REQ-xxx.

**INTERDIT dans requirement.md :**
- Spécifications techniques libres (code, SQL, annotations, architecture)
- Détails d'implémentation (noms de classes, schémas de BDD, configurations)
- Critères d'acceptation regroupés en fin de document sans REQ-xxx

**Ces éléments appartiennent à `design.md`** — pas à `requirement.md`.

**Si l'utilisateur fournit d'emblée une description technique détaillée** : extraire les besoins utilisateur sous-jacents et les traduire en user stories REQ-xxx. Les détails techniques seront capturés en phase design.

---

## Process

### Step 1: Understand Context
Read the spec title and any description. Identify domain, likely scope, users/stakeholders.

### Step 1b: Identify Glossary Terms

Avant de poser les questions, identifier les termes métier ou techniques
qui pourraient être ambigus dans le contexte de cette spec.
Les lister pour les inclure dans le glossaire du document final.

### Step 1c: Recherche contexte codebase

Avant de poser les questions de clarification, chercher du contexte dans le code existant :

0. **Graphe structurel (sdd-graph) — consulter en premier si disponible**

   Si `.mahi/graph/manifest.json` existe :
   - Si `module-dep.json` est `"fresh"` : lire le fichier, identifier les modules dont le nom correspond au domaine du spec (ex: "commande", "paiement", "user")
   - Si `entity-model.json` est `"fresh"` ET que la spec semble toucher des données : lire les entités des modules concernés
   - Si `service-call.json` est `"fresh"` : identifier les services du domaine et leurs dépendances directes

   Enrichir la section "Contexte codebase" avec :
   - Modules impactés + couplage afférent/efférent (depuis `module-dep.json`)
   - Entités JPA concernées + leurs relations (depuis `entity-model.json`)
   - Services identifiés + dépendances injectées (depuis `service-call.json`)

   Si le graphe est absent ou stale : passer directement au point 1 ci-dessous.

1. Vérifier si `.mahi/docs/index.md` existe — si oui, identifier les modules potentiellement concernés par le titre et la description de la spec
   - Pour chaque module pertinent : lire `.mahi/docs/modules/<nom>/module-<nom>.md`
2. Si les docs sont absents ou insuffisants pour les zones concernées : dispatcher `spec-deep-dive`
   ```
   Agent({ subagent_type: "sdd-spec:spec-deep-dive", model: "opus", prompt: "<question ciblée sur la zone concernée>" })
   ```
3. Synthétiser dans une section **"Contexte codebase"** à inclure dans requirement.md :
   - Modules existants concernés et leur rôle
   - Patterns ou conventions déjà en place pertinents pour cette spec
   - Points d'attention identifiés (dépendances, contraintes, dette technique)

Cette section est transmise à la phase design pour orienter les décisions architecturales.
Si aucun module n'est concerné (nouvelle fonctionnalité complètement isolée) : noter "Aucun module existant concerné."

### Step 1d: Vérification préliminaire du périmètre

Avant de poser les premières questions, évaluer si la demande est réaliste comme spec unique :
- La description mentionne-t-elle plusieurs systèmes indépendants sans lien fonctionnel ?
- Y a-t-il des signaux de "deux specs en une" (ex. "refactorer X ET créer Y de zéro") ?

Si oui : signaler le problème de périmètre immédiatement, proposer un split ou une redéfinition du scope. Ne pas continuer sans clarification.

### Step 1e: Specs passés liés

Lire `.mahi/registry.json`. Pour chaque spec avec statut `completed` ou `retrospective` :
- Comparer le titre et les mots-clés avec le titre et la description du spec courant
- Si lien probable (même domaine fonctionnel, mêmes entités, même module) : lire son `requirement.md`

Inclure dans le briefing initial à l'utilisateur :
```
💡 Specs liés trouvés : <titre-1>, <titre-2>
   Leurs exigences sont chargées comme contexte.
```

Ce contexte oriente les questions de clarification (éviter de re-poser ce qui est déjà établi) et enrichit le glossaire.
Si aucun spec lié : continuer sans mention.

### Step 2: Ask Clarifying Questions (in French)
Do not assume requirements. Ask specific questions:
- "Que doit-il se passer quand X ?"
- "Qui est l'utilisateur principal de cette fonctionnalité ?"
- "Existe-t-il des patterns existants dans le code pour cela ?"
- "Quels sont les cas limites ?"
- "Qu'est-ce qui ne devrait PAS être dans le périmètre ?"

Poser une question à la fois, en commençant par l'inconnue la plus critique. Attendre la réponse avant de passer à la suivante.

### Step 2b: Define Scope

Après les questions de clarification, définir explicitement :
- **Dans le périmètre** : ce qui sera implémenté dans cette spec
- **Hors périmètre** : ce qui est exclu et pourquoi (différé, autre spec, hors domaine)

Inclure ces tableaux dans le document final — ils évitent les dérives de périmètre.

### Step 3: Draft Requirements

Pour chaque exigence identifiée, créer une section REQ-xxx suivant **exactement** ce format :

```markdown
### REQ-001 : <Titre court de l'exigence>

**Récit utilisateur :**
En tant que <rôle>, je veux <capacité> afin de <bénéfice>.

**Critères d'acceptation :**

1. LE <Système/Composant> DOIT <action>
2. QUAND <condition> ALORS LE <Système> DOIT <action>
3. QUAND <condition> ALORS LE <Système> NE DOIT PAS <action>
4. LE <Système> DEVRAIT <action souhaitée>

**Priorité :** obligatoire | souhaitable | optionnel

**Statut :** brouillon
```

Mots-clés pour les critères d'acceptation :
- `DOIT` — exigence absolue (SHALL / MUST)
- `NE DOIT PAS` — interdiction absolue (SHALL NOT / MUST NOT)
- `DEVRAIT` — exigence souhaitée (SHOULD)
- `PEUT` — comportement optionnel permis (MAY / COULD)
- `QUAND … ALORS` — condition + conséquence (WHEN … THEN)

Règles :
- IDs séquentiels, zéro-paddés à 3 chiffres : REQ-001, REQ-002, …
- Exigences non fonctionnelles : préfixe REQ-NF-xxx (performance, sécurité, scalabilité…)
- Récit utilisateur obligatoire sur chaque REQ, même pour des exigences techniques
- Critères d'acceptation : liste numérotée, une condition par ligne, verbe modal obligatoire
- Une seule préoccupation par REQ

### Step 3b: Draft Non-Functional Requirements

Identifier les exigences non fonctionnelles pertinentes :
- **Performance** : temps de réponse, débit, latence
- **Sécurité** : authentification, autorisation, chiffrement
- **Scalabilité** : volumétrie, charge concurrente
- **Disponibilité** : SLA, tolérance aux pannes
- **Maintenabilité** : observabilité, logs, alertes

Créer des REQ-NF-xxx pour chaque contrainte identifiée.
Les critères d'acceptation doivent être **mesurables et chiffrés**
(ex. "DOIT répondre en moins de 200ms au 95e percentile").

### Step 3c: Auto-relecture de requirement.md

Avant de présenter les exigences, relire requirement.md en 3 passes successives :

**Pass 1 — Complétude**
- Chaque besoin mentionné par l'utilisateur a-t-il un REQ correspondant ?
- Les cas limites sont-ils couverts ?
- Les exigences non fonctionnelles pertinentes ont-elles un REQ-NF-xxx ?
- Le glossaire contient-il tous les termes ambigus ?
- Les tableaux "Dans le périmètre" et "Hors périmètre" sont-ils remplis ?

**Pass 2 — Correction**
- Chaque critère d'acceptation est-il testable (observable, mesurable) ?
- Les mots modaux sont-ils appropriés (DOIT / NE DOIT PAS / DEVRAIT / PEUT) ?
- Chaque REQ a-t-il un récit utilisateur ("En tant que...") ?
- Les REQ-NF ont-ils des seuils chiffrés ?
- Aucun détail d'implémentation technique dans requirement.md ?

**Pass 3 — Cohérence**
- Les REQs sont-ils cohérents entre eux (pas de contradiction) ?
- Le périmètre est-il cohérent avec le titre du spec ?
- Les IDs sont-ils séquentiels sans trou (REQ-001, REQ-002...) ?

Condition d'arrêt : 2 passes consécutives sans nouveau problème, ou 3 passes maximum.
Si des corrections ont été appliquées : noter le nombre en français avant de présenter.

### Step 4: Present for Review
Present complete requirement.md to user (in French):
- List all REQ items
- Ask: "Ces exigences couvrent-elles bien votre besoin ? Quelque chose à ajouter, modifier ou retirer ?"
- Iterate until satisfied

### Step 4b: Concern Detection (after each iteration)
After each round of user input, silently evaluate whether multiple distinct concerns are mixed:
- Different domains (e.g., refactoring existing code + building a new generic system)
- Items that could ship independently with no dependency between them
- Different stakeholders or risk profiles
- "While we're at it" additions that feel like scope creep

**If mixing detected**, add a non-blocking advisory after the requirements list:

```
💡 Ces exigences semblent couvrir deux préoccupations distinctes :
- A : <label> — REQ-001, REQ-002 (refactoring du module existant)
- B : <label> — REQ-003, REQ-004 (nouveau système générique)

Souhaitez-vous séparer B dans une spec dédiée ? (`/spec split` — je ferai la répartition pour vous)
Ou continuer avec tout dans cette spec ?
```

**Avant de proposer un split**, évaluer si les deux préoccupations ont des dépendances mutuelles.
Si oui, signaler immédiatement qu'un découpage A/B naïf créerait un cycle, et proposer directement un découpage en 3 :

```
💡 Ces deux préoccupations sont interdépendantes — un split direct créerait un cycle.
Découpage recommandé en 3 specs :
- A : <domaine sans la partie partagée>
- B-générique : <système autonome sans dépendance>
- B-intégration : <déploiement de B-générique sur A> (dépend de A et B-générique)
```

This is advisory only — never block the user. If they choose to continue, do not raise the concern again unless new conflicting requirements are added.

### Step 5: Save

Write `requirement.md` using the template from `references/templates.md` section `requirement.md`.
Après écriture du fichier, synchroniser l'artefact :
```
mcp__plugin_mahi_mahi__write_artifact(flowId: <depuis active.json>, artifactName: "requirements", content: <contenu complet>)
```
Par exigence finalisée, enregistrer les données structurées :
```
mcp__plugin_mahi_mahi__add_requirement(flowId: <depuis active.json>, req: {
  id: "REQ-xxx",
  title: "<titre>",
  priority: "must|should|could",
  status: "VALID",
  content: "<description complète>",
  acceptanceCriteria: [
    { id: "REQ-xxx.AC-1", description: "<critère testable>" },
    ...
  ]
})
```
Append log.md entry: date, "Phase exigences", actions (X exigences rédigées), decisions prises.

### Step 6: Await Approval
"Les exigences sont prêtes pour relecture. Quand vous êtes satisfait, lancez `/spec approve` pour passer à la conception."

## Quality Criteria
- Every REQ has a clear user story in French ("En tant que...")
- Acceptance criteria are testable conditions, not implementation descriptions
- Non-functional requirements covered with REQ-NF-xxx and measurable thresholds
- Glossaire présent pour tout terme ambigu ou spécifique au domaine
- Périmètre explicite : tableaux "dans" et "hors" périmètre remplis
- Section "Contexte codebase" présente (modules concernés ou mention explicite "Aucun")
- No architecture, code, or SQL in requirement.md
- Each REQ addresses a single concern
- `mcp__plugin_mahi_mahi__write_artifact` et `mcp__plugin_mahi_mahi__add_requirement` appelés après finalisation (avec `acceptanceCriteria` structurés)

## Formatting Rules (apply when writing requirement.md)
- Maximum line length : 200 characters — wrap longer lines
- Never use comma-separated inline lists with more than 2 items :
  convert to bullet points
- Each acceptance criterion on its own line
