# Journal — mcp-for-spec

| Date | Phase | Événement |
|------|-------|-----------|
| 2026-04-18 | requirements | Spec créé |
| 2026-04-18 | requirements | 4 axes clarifiés : opérations granulaires (Option B), cohérence présence+propagation, contexte de session persisté, métriques durée de phase uniquement (timestamps transitions existants) |
| 2026-04-18 | requirements → design | Phase requirements approuvée — 7 REQs validés (REQ-001 à REQ-006 + REQ-NF-001) |
| 2026-04-18 | design | Phase design créée — 8 DES rédigés : DES-001 (modèle RequirementItem/DesignItem/SessionContext), DES-002 (outils REQ), DES-003 (outils DES), DES-004 (propagation STALE fine-grained), DES-005 (mahi_check_coherence), DES-006 (mahi_save_context), DES-007 (métriques phaseDurations), DES-008 (convention nommage). Décisions SOLID : DES-001 Option B (champs typés vs metadata), DES-004 Option B (propagation dans WorkflowService vs moteur), DES-005 guard dans SpecWorkflowDefinition. Aucun conflit règles projet détecté. |
| 2026-04-18 | design → worktree | Phase design approuvée. Worktree créé dans .worktrees/mcp-for-spec sur la branche spec/vincent-bailly/mcp-for-spec. Baseline capturée : 22 tests passent. |
| 2026-04-18 | worktree → planning | Worktree completé. Phase planification démarrée. |
| 2026-04-18 | planning | Plan créé : 8 tâches, 18 sous-tâches. Structure RED/GREEN/REFACTOR. TASK-001 (hiérarchie Artifact) est le fondement séquentiel ; lots 2 à 4 parallélisables (4 agents simultanés). 1 correction pass 2 : TASK-001.2 splitté. |
| 2026-04-18 | planning → implementation | Phase planning approuvée. Phase d'implémentation démarrée. |
| 2026-04-18 | implementation | Lot 1 terminé (Wave 1-4) : TASK-001.1 [RED], TASK-001.2 [GREEN], TASK-001.2b [GREEN], TASK-001.3 [REFACTOR]. Hiérarchie Artifact complète, ArtifactState supprimé. 26/26 tests passent. Correction : @JsonIgnore sur isValid() pour éviter sérialisation du champ "valid". |
| 2026-04-18 | implementation | Lots 2-6 terminés : TASK-002 à TASK-008. Opérations REQ/DES, saveContext, phaseDurations, propagation STALE fine-grained, checkCoherence, guard coherence, convention nommage. 55/55 tests passent (22 baseline + 33 nouveaux). |