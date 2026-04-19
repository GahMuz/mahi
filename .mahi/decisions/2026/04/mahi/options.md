# Options : mahi

## Option A : Serveur MCP Java/Spring AI (fat jar, Gradle)

**Description** : Implémenter Mahi comme un serveur MCP Spring Boot (Spring AI MCP Server) compilé en fat jar via Gradle. Le serveur expose des outils MCP (`@McpTool`) et des ressources (`@McpResource`) pour la gestion déterministe de la machine d'état. Les états et événements sont des types Java stricts (enums), l'historique des transitions est persisté en JSON.

**Pour** :
- Déterminisme total : les règles de transition sont du code Java compilé, non interprétables
- Intégration native avec le plugin SDD via fat jar embarqué
- Spring AI MCP Server : protocole MCP géré, pas de code boilerplate
- Types stricts (enum) pour états et événements : erreur de compilation si transition invalide
- Historique des transitions persisté : auditabilité complète
- Support multi-workflow : architecture extensible (WorkflowDefinition est une interface)
- `@McpResource` pour exposer les artefacts en lecture (scenario.md, requirements.md, design.md, plan.md)
- Java 21 + Gradle : aligné avec l'écosystème du projet

**Contre** :
- Nécessite un runtime JVM (toujours présent dans le contexte SDD)
- Temps de démarrage JVM au premier appel (acceptable en STDIO)
- Maintenance d'un service externe à configurer dans `.mcp.json`

**Complexité de mise en œuvre** : MEDIUM
**Coût** : Gratuit (OSS)
**Maturité** : Spring Boot production-ready, Spring AI MCP Server 1.0 stable
**Compatible avec nos contraintes** : oui — Java 21, Gradle, fat jar, dossier `mahi-mcp/`, STDIO, SYNC

**Conformité aux rules** : conforme

---

## Option B : Script Python/TypeScript MCP (interprété)

**Description** : Implémenter la machine d'état dans un script Python ou TypeScript utilisant le SDK MCP officiel. Le script est exécuté directement sans compilation.

**Pour** :
- Démarrage rapide (pas de compilation)
- Ecosystème MCP riche en Python/TypeScript

**Contre** :
- Pas de types stricts compilés : les transitions restent vulnérables aux erreurs d'interprétation
- Dépendances runtime supplémentaires (Python/Node) non garanties dans le contexte plugin SDD
- Incompatible avec la contrainte "fat jar intégrable dans le plugin"
- Ecosystème du projet Java : incohérence technologique

**Complexité de mise en œuvre** : LOW
**Coût** : Gratuit
**Maturité** : production-ready
**Compatible avec nos contraintes** : non — viole la contrainte "fat jar Java"

**Conformité aux rules** : remet en cause (contrainte fat jar Java)

---

## Option C : Logique embarquée dans le plugin (LLM + Markdown)

**Description** : Conserver la machine d'état dans le plugin SDD actuel (Markdown + LLM), mais en renforçant les instructions et ajoutant des validations côté LLM.

**Pour** :
- Aucun composant supplémentaire à maintenir

**Contre** :
- Ne résout pas le problème fondamental : le LLM reste non-déterministe
- Aucune garantie sur les transitions
- Pas d'auditabilité des transitions
- Régression impossible à tester

**Complexité de mise en œuvre** : LOW
**Coût** : Gratuit
**Maturité** : N/A
**Compatible avec nos contraintes** : non — le problème est précisément l'interprétation variable par le LLM

**Conformité aux rules** : remet en cause (objectif de stabilité)

---

## Options éliminées

| Option | Raison d'élimination |
|--------|----------------------|
| Option B — Python/TypeScript | Incompatible avec la contrainte fat jar Java, incohérence technologique |
| Option C — LLM + Markdown renforcé | Ne résout pas le problème fondamental de non-déterminisme |
