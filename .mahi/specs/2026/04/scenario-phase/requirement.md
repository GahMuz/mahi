# Requirements — scenario-phase

## Contexte

Tous les workflows Mahi (spec, ADR, bug-finding, etc.) démarrent actuellement avec une phase propre à leur type (`requirements`, `analysis`, etc.). Il n'existe pas de phase commune légère permettant de capturer l'intention initiale de l'utilisateur avant toute analyse, ni de point d'entrée générique quand le type de workflow n'est pas encore connu.

## REQ-001 — Phase scénario initiale commune à tous les workflows

Tous les workflows Mahi (type `spec`, `adr`, et futurs types) doivent démarrer par une phase `scenario` avant leur première phase propre.

**AC-1** : À la création de tout workflow, l'état initial est `SCENARIO` (non `REQUIREMENTS`, `ANALYSIS`, etc.).  
**AC-2** : L'événement `APPROVE_SCENARIO` fait transitionner vers le premier état propre au type (ex. `REQUIREMENTS` pour un spec, premier état ADR pour un adr).  
**AC-3** : Les transitions existantes après la phase scénario ne changent pas.

## REQ-002 — Artefact scenario.md géré par le MCP

Chaque workflow possède un fichier `scenario.md` créé et géré par le serveur Mahi via un outil MCP dédié.

**AC-1** : `scenario.md` est créé automatiquement à la création du workflow, avec le titre et l'input initial de l'utilisateur.  
**AC-2** : Chaque échange (question Claude / réponse utilisateur) est appendé à `scenario.md` via un appel MCP explicite (pas d'écriture directe par le LLM).  
**AC-3** : `scenario.md` est immuable une fois la phase `scenario` terminée (aucun append après `APPROVE_SCENARIO`).  
**AC-4** : Le fichier est localisé dans le répertoire de la spec : `.mahi/specs/<période>/<spec-id>/scenario.md`.

## REQ-003 — Commande `/scenario new` : point d'entrée sans type prédéfini

Une commande `/scenario new [titre]` permet de démarrer un dialogue sans connaître à l'avance le type de workflow à créer.

**AC-1** : La commande démarre la phase scénario (dialogue léger, Q&A) sans créer de workflow typé immédiatement.  
**AC-2** : À l'issue du dialogue, Claude propose un type de workflow (`spec`, `adr`, `bugfix`, etc.) avec une justification courte.  
**AC-3** : L'utilisateur confirme ou corrige le type proposé avant la création effective du workflow (`mahi_create_workflow`).  
**AC-4** : Une fois confirmé, le workflow est créé avec le type retenu, `scenario.md` est finalisé, et la phase propre au type commence.

## REQ-004 — Compatibilité des commandes typées existantes

Les commandes `/spec new`, `/adr new` et équivalents continuent de fonctionner sans changement de comportement visible pour l'utilisateur.

**AC-1** : `/spec new [titre]` crée un workflow de type `spec` en état `SCENARIO` et démarre la phase scénario avant `requirements`.  
**AC-2** : Le dialogue de la phase scénario est allégé (pas de questions redondantes avec `requirements`) mais produit bien un `scenario.md`.  
**AC-3** : La transition vers `REQUIREMENTS` se fait via `APPROVE_SCENARIO` après le dialogue.

## REQ-005 — Persistance et rejeu du scénario

`scenario.md` sert de mémoire persistante du dialogue initial, rejouable pour comparaison.

**AC-1** : `scenario.md` contient : le titre, l'input initial complet de l'utilisateur, et chaque paire Q&A dans l'ordre chronologique.  
**AC-2** : La structure de `scenario.md` est normalisée (format défini dans le design) pour permettre un traitement automatisé futur.  
**AC-3** : Il doit être possible de "rejouer" un scénario existant en passant `scenario.md` comme contexte d'entrée d'un nouveau workflow du même type — l'intention est comparée au résultat produit.

## REQ-006 — Outil MCP dédié pour le scénario

Le serveur Mahi expose un outil MCP permettant de gérer `scenario.md`.

**AC-1** : `mahi_append_scenario(workflowId, role, content)` ajoute une entrée à `scenario.md` (rôles : `user`, `assistant`, `system`).  
**AC-2** : L'outil vérifie que le workflow est bien en phase `SCENARIO` avant d'accepter un append ; toute tentative hors phase retourne une erreur.  
**AC-3** : `mahi_get_workflow` retourne le contenu de `scenario.md` dans la réponse quand la phase courante est `SCENARIO`.
