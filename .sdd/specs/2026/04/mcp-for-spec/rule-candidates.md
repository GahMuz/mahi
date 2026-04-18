# Règles candidates

## [task-implementer] Éviter dépendance circulaire WorkflowService ↔ WorkflowDefinition via guard inline
- **Domaine** : service, architecture
- **Contexte** : TASK-005.2 — guard de cohérence dans SpecWorkflowDefinition (DES-005) devait appeler WorkflowService.checkCoherence
- **Décision** : La logique de cohérence a été dupliquée inline dans le guard plutôt que d'injecter WorkflowService dans SpecWorkflowDefinition, pour éviter la dépendance circulaire WorkflowService → WorkflowEngine → WorkflowRegistry → SpecWorkflowDefinition → WorkflowService. La logique est extraite dans une méthode statique privée `coherenceGuard()` — si elle doit diverger de WorkflowService.checkCoherence, envisager un CoherenceChecker dédié injectable dans les deux.

## [task-implementer] @JsonIgnore sur les méthodes `is*()` dans les entités Jackson polymorphiques
- **Domaine** : entity, test, api
- **Contexte** : TASK-001.2b — Artifact.isValid() causait la sérialisation du champ "valid" dans le JSON
- **Décision** : Ajouter @JsonIgnore sur toute méthode `is*()` dans les classes héritant d'Artifact (et en général dans les entités Jackson) si la méthode ne correspond pas à un champ à sérialiser. Spring Boot configure Jackson avec FAIL_ON_UNKNOWN_PROPERTIES=false en prod mais pas dans les tests unitaires.

## [task-implementer] WorkflowService doit stocker la référence au WorkflowRegistry pour les méthodes get()
- **Domaine** : service
- **Contexte** : TASK-007.2 — calcul phaseDurations nécessite d'appeler registry.get(workflowType).getStateToPhaseMapping()
- **Décision** : WorkflowService stocke le registry comme champ (en plus de le passer à WorkflowEngine) pour permettre l'enrichissement du contexte retourné par get(). Pattern général : si une façade doit enrichir le résultat du moteur avec des informations de la définition, elle garde le registry.
