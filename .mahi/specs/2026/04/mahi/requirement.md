# Exigences : mahi — Serveur MCP Java pour la machine d'état SDD

## Contexte codebase

**Module existant :** `mahi-mcp/` — squelette Maven Spring Boot 3.4.4 (pom.xml présent, src/ vide).
Le build sera migré vers Gradle (fat jar). Aucun code Java existant à préserver.

**ADR de référence :** ADR-001 mahi — décision : serveur MCP Java/Spring AI, fat jar Gradle, Java 21, types stricts enum, historique des transitions persisté, `@McpResource` pour les artefacts.

**Points d'attention :**
- La version Spring AI utilisée dans le pom.xml existant est `1.0.0-M6` (milestone). La version cible est `1.0.0` stable (si disponible) ou la dernière stable au moment de l'implémentation.
- Le serveur communique en STDIO (mode non-web) — pas de port HTTP.
- Le fat jar doit être exécutable directement : `java -jar mahi-mcp.jar`.

---

## Périmètre

### Dans le périmètre

| Fonctionnalité | Description |
|----------------|-------------|
| Moteur de machine d'état | Transitions déterministes avec guards et actions, types stricts |
| Gestion des artefacts | Lecture/écriture d'artefacts Markdown par workflow |
| Historique des transitions | Persistance horodatée de chaque transition |
| Workflows intégrés | spec, ADR, debug, find-bug |
| Exposition MCP outils | `@McpTool` pour toutes les opérations d'état |
| Exposition MCP ressources | `@McpResource` pour lecture des artefacts |
| Tests de régression | Tests unitaires du moteur + tests d'intégration par workflow |
| Build Gradle fat jar | Configuration Gradle avec tâche shadowJar ou bootJar |
| Gestion git worktree | Création/suppression de worktrees pour le workflow spec |

### Hors périmètre

| Fonctionnalité | Raison |
|----------------|--------|
| Interface web / dashboard | Hors contraintes ADR-001 |
| Base de données relationnelle | JSON fichier suffisant pour cette version |
| Authentification MCP | Géré par le client MCP (Claude Desktop) |
| Exécution des tâches d'implémentation | Mahi orchestre l'état, pas le code |
| Workflows non-SDD | Hors scope première version |

---

## Glossaire

| Terme | Définition |
|-------|------------|
| Workflow | Ensemble d'états, d'événements et de transitions définissant le cycle de vie d'un élément SDD |
| Machine d'état | Automate fini définissant les transitions valides entre états |
| Guard | Condition vérifiée avant d'autoriser une transition — lève une exception si non satisfaite |
| Action | Effet de bord exécuté lors d'une transition (ex. : marquer un artefact STALE) |
| Artefact | Document Markdown produit à une phase donnée (scenario.md, requirements.md, design.md, plan.md, adr.md...) |
| Transition | Passage d'un état à un autre déclenché par un événement |
| Historique | Log chronologique des transitions appliquées sur un workflow |
| Fat jar | Archive JAR auto-suffisante incluant toutes ses dépendances, exécutable via `java -jar` |
| Flow | Instance concrète d'un workflow (ex. : le spec "mahi" est un flow de type "spec") |
| MCP | Model Context Protocol — protocole permettant à un LLM d'appeler des outils et lire des ressources |
| STDIO | Mode de communication MCP par entrée/sortie standard (pas de port HTTP) |

---

## Exigences fonctionnelles

### REQ-001 : Moteur de machine d'état déterministe avec types stricts

**Récit utilisateur :**
En tant que plugin SDD, je veux que les transitions de workflow soient exécutées par un moteur Java compilé avec des types stricts, afin de garantir qu'aucune transition illégale ne soit possible.

**Critères d'acceptation :**

1. LE moteur DOIT représenter les états et événements par des types enum Java (pas des String bruts)
2. LE moteur DOIT vérifier qu'une transition `(état courant, événement)` est définie avant de l'exécuter
3. QUAND une transition n'existe pas pour `(état, événement)` ALORS LE moteur DOIT lever une exception avec le message `"Transition invalide : <état>::<événement>"`
4. LE moteur DOIT exécuter tous les guards d'une transition avant de changer d'état
5. QUAND un guard échoue ALORS LE moteur DOIT lever une exception décrivant la condition non satisfaite et NE DOIT PAS modifier l'état
6. LE moteur DOIT exécuter toutes les actions d'une transition après validation de tous les guards
7. LE moteur DOIT persister l'état après chaque transition réussie

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-002 : Historique des transitions

**Récit utilisateur :**
En tant qu'auditeur du workflow, je veux consulter l'historique complet des transitions appliquées à un flow, afin de pouvoir rejouer ou auditer le déroulement du workflow.

**Critères d'acceptation :**

1. LE système DOIT persister chaque transition dans un historique horodaté (ISO-8601)
2. CHAQUE entrée d'historique DOIT contenir :
   - l'état source
   - l'événement appliqué
   - l'état cible
   - l'horodatage de la transition
3. LE système DOIT conserver l'historique complet — aucune entrée ne DOIT être écrasée ou supprimée lors d'une transition ultérieure
4. L'historique DOIT être inclus dans la persistance JSON du flow

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-003 : Gestion des artefacts Markdown

**Récit utilisateur :**
En tant que plugin SDD, je veux écrire et lire des artefacts Markdown associés à un flow, afin de stocker les documents produits à chaque phase du workflow.

**Critères d'acceptation :**

1. LE système DOIT permettre d'écrire le contenu d'un artefact nommé associé à un flow
2. QUAND un artefact est écrit ALORS son statut DOIT passer à `DRAFT` puis `VALID`
3. LE système DOIT persister les artefacts dans `.mahi/artifacts/<flowId>/<artifactName>.md`
4. LE système DOIT exposer les artefacts en lecture via `@McpResource` avec URI `mahi://artifacts/<flowId>/<artifactName>`
5. LE système DOIT suivre le statut de chaque artefact : `MISSING`, `DRAFT`, `VALID`, `STALE`
6. QUAND un artefact upstream est modifié ALORS les artefacts en aval dans le graphe d'invalidation DOIVENT passer à `STALE`

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-004 : Graphe d'invalidation des artefacts

**Récit utilisateur :**
En tant que plugin SDD, je veux qu'une modification tardive d'un artefact upstream invalide automatiquement les artefacts dépendants, afin de maintenir la cohérence entre les phases du workflow.

**Critères d'acceptation :**

1. CHAQUE `WorkflowDefinition` DOIT déclarer un graphe d'invalidation indiquant quels artefacts sont impactés par la modification d'un artefact source
2. QUAND `addRequirementInfo` est appelé ALORS LE système DOIT propager l'invalidation selon le graphe et passer le flow en état `REANALYZING`
3. QUAND `addDesignInfo` est appelé ALORS LE système DOIT propager l'invalidation selon le graphe et passer le flow en état `REANALYZING`
4. LE graphe d'invalidation DOIT être configurable par type de workflow (spec, ADR, debug, find-bug)

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-005 : Multi-workflow — spec, ADR, debug, find-bug

**Récit utilisateur :**
En tant que plugin SDD, je veux que Mahi supporte plusieurs types de workflows (spec, ADR, debug, find-bug), afin de gérer l'ensemble du cycle de vie SDD avec un seul serveur MCP.

**Critères d'acceptation :**

1. LE système DOIT définir un type de workflow `spec` avec les états : `DRAFT`, `SCENARIO_DEFINED`, `PROJECT_RULES_LOADED`, `REQUIREMENTS_DEFINED`, `DESIGN_DEFINED`, `IMPLEMENTATION_PLAN_DEFINED`, `WORKTREE_CREATED`, `IMPLEMENTING`, `REANALYZING`, `VALIDATING`, `FINALIZING`, `RETROSPECTIVE_DONE`, `DONE`
2. LE système DOIT définir un type de workflow `adr` avec les états : `FRAMING`, `EXPLORING`, `DISCUSSING`, `DECIDING`, `RETROSPECTIVE`, `DONE`
3. LE système DOIT définir un type de workflow `debug` avec les états : `REPORTED`, `REPRODUCING`, `ANALYZING`, `FIXING`, `VALIDATING`, `DONE`
4. LE système DOIT définir un type de workflow `find-bug` avec les états : `SCANNING`, `TRIAGING`, `REPORTING`, `DONE`
5. CHAQUE type de workflow DOIT être enregistré dans un `WorkflowRegistry` central
6. LE système DOIT permettre d'ajouter un nouveau type de workflow sans modifier le moteur central

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-006 : Exposition MCP — outils de workflow

**Récit utilisateur :**
En tant que LLM pilotant le plugin SDD, je veux appeler des outils MCP pour créer, consulter et faire progresser un workflow, afin de déléguer la machine d'état à Mahi.

**Critères d'acceptation :**

1. LE serveur DOIT exposer l'outil `mahi_create_workflow(flowId, workflowType)` pour créer une nouvelle instance de workflow
2. LE serveur DOIT exposer l'outil `mahi_get_workflow(flowId)` pour obtenir l'état courant d'un flow
3. LE serveur DOIT exposer l'outil `mahi_fire_event(flowId, event)` pour appliquer une transition
4. LE serveur DOIT exposer l'outil `mahi_write_artifact(flowId, artifactName, content)` pour écrire un artefact
5. LE serveur DOIT exposer l'outil `mahi_add_requirement_info(flowId, info)` pour ajouter une information tardive sur les exigences
6. LE serveur DOIT exposer l'outil `mahi_add_design_info(flowId, info)` pour ajouter une information tardive sur le design
7. LE serveur DOIT exposer l'outil `mahi_create_worktree(flowId)` pour créer un git worktree
8. LE serveur DOIT exposer l'outil `mahi_remove_worktree(flowId)` pour supprimer un git worktree
9. QUAND un outil est appelé avec un `flowId` inexistant ALORS LE serveur DOIT retourner une erreur claire

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-007 : Exposition MCP — ressources artefacts

**Récit utilisateur :**
En tant que LLM pilotant le plugin SDD, je veux lire les artefacts Markdown d'un workflow via des ressources MCP, afin d'accéder au contenu des documents sans appeler un outil.

**Critères d'acceptation :**

1. LE serveur DOIT exposer les artefacts via `@McpResource` avec le pattern d'URI `mahi://artifacts/{flowId}/{artifactName}`
2. LES ressources suivantes DOIVENT être exposées : `scenario`, `requirements`, `design`, `plan`, `adr`, `retrospective`
3. QUAND un artefact est `MISSING` ou son fichier absent ALORS LE serveur DOIT retourner une erreur lisible (pas une NPE)
4. LE contenu retourné DOIT être le texte Markdown brut du fichier artefact

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-008 : Build Gradle fat jar

**Récit utilisateur :**
En tant qu'intégrateur du plugin SDD, je veux un fat jar Gradle exécutable incluant toutes les dépendances, afin d'embarquer Mahi dans le plugin sans dépendance runtime externe.

**Critères d'acceptation :**

1. LE projet DOIT utiliser Gradle (Kotlin DSL) comme système de build
2. LA tâche `./gradlew bootJar` DOIT produire un fat jar auto-suffisant dans `build/libs/`
3. LE fat jar DOIT être exécutable via `java -jar mahi-mcp-server-<version>.jar` sans dépendance externe autre que la JVM
4. LE build DOIT compiler avec Java 21
5. LE projet DOIT supprimer le `pom.xml` Maven existant (migration complète vers Gradle)

**Priorité :** obligatoire
**Statut :** brouillon

---

### REQ-009 : Tests de régression et couverture

**Récit utilisateur :**
En tant que développeur du plugin SDD, je veux des tests automatisés couvrant le moteur et chaque workflow, afin de détecter les régressions et incompatibilités lors des évolutions.

**Critères d'acceptation :**

1. LE projet DOIT contenir des tests unitaires du `WorkflowEngine` couvrant :
   - création d'un flow
   - transition valide
   - transition invalide (état incorrect, événement inconnu)
   - guard échoué
   - invalidation d'artefacts
2. LE projet DOIT contenir des tests d'intégration pour chaque `WorkflowDefinition` (spec, ADR, debug, find-bug) vérifiant le chemin nominal complet
3. LA tâche `./gradlew test` DOIT exécuter tous les tests sans erreur
4. LE rapport de couverture DOIT être généré via JaCoCo

**Priorité :** obligatoire
**Statut :** brouillon

---

## Exigences non fonctionnelles

### REQ-NF-001 : Démarrage rapide du serveur MCP

**Récit utilisateur :**
En tant qu'utilisateur du plugin SDD, je veux que le serveur Mahi démarre rapidement en mode STDIO, afin que les appels MCP ne soient pas perceptiblement ralenties.

**Critères d'acceptation :**

1. LE serveur DOIT démarrer en moins de 5 secondes sur une machine de développement standard (8 Go RAM, CPU moderne)
2. LE serveur DOIT opérer en mode `spring.main.web-application-type=none` (pas de port HTTP)
3. LE serveur DOIT utiliser le mode `STDIO` de Spring AI MCP Server

**Priorité :** souhaitable
**Statut :** brouillon

---

### REQ-NF-002 : Persistance atomique des flows

**Récit utilisateur :**
En tant que plugin SDD, je veux que les transitions soient persistées de façon atomique, afin d'éviter les états corrompus en cas d'interruption.

**Critères d'acceptation :**

1. LE `WorkflowStore` DOIT écrire le fichier JSON dans `.mahi/flows/<flowId>.json` de façon synchrone
2. QUAND une transition échoue (guard ou action) ALORS LE fichier de persistance NE DOIT PAS être modifié
3. LE format JSON DOIT être lisible par un humain (indentation 2 espaces)

**Priorité :** obligatoire
**Statut :** brouillon
