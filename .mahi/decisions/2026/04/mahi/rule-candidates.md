# Règles candidates

## [decision] Préférer un serveur MCP compilé (fat jar) pour les comportements déterministes critiques

- **Domaine** : architecture
- **Contexte** : ADR-001 Mahi — la machine d'état des workflows SDD ne peut pas être fiable si pilotée par un LLM interprétant du Markdown
- **Décision** : Toute logique de workflow devant être déterministe et auditable doit être implémentée dans un composant compilé (Java fat jar) exposé via MCP, et non dans des instructions Markdown interprétées par le LLM

## [exploration] Utiliser des enums Java pour les états et événements d'une machine d'état MCP

- **Domaine** : architecture
- **Contexte** : ADR-001 Mahi — les états et événements sous forme de String bruts ne sont pas vérifiés à la compilation
- **Décision** : Dans tout serveur MCP Java gérant une machine d'état, les états et événements doivent être des types enum, jamais des String bruts
