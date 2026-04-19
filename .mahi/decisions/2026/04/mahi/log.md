# Log — mahi (ADR-001)

| Date | Événement |
|------|-----------|
| 2026-04-18 | ADR créé. Titre : mahi. Numéro : ADR-001. |
| 2026-04-18 | Phase framing démarrée. Problème : stabiliser la machine d'état des workflows SDD en déplaçant la logique du LLM vers un serveur MCP Java compilé. |
| 2026-04-18 | Phase exploration terminée. 3 options identifiées, 2 éliminées (Option B : incompatibilité fat jar Java ; Option C : ne résout pas le non-déterminisme). |
| 2026-04-18 | Discussion — argument retenu : seul le code Java compilé garantit le déterminisme des transitions. |
| 2026-04-18 | Discussion — Option A retenue comme finaliste unique : Spring AI MCP Server, fat jar Gradle, types stricts enum, historique persisté. |
| 2026-04-18 | Décision formalisée : Option A — Serveur MCP Java/Spring AI (fat jar, Gradle). ADR-001 rédigé. |
| 2026-04-18 | Rétrospective : 2 règles candidates ajoutées (architecture). ADR complété. |
