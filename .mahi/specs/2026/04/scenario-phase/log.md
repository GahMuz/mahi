# Journal — scenario-phase

## 2026-04-19T08:16:44Z — Création

Spec initiée via `/spec new` depuis la conversation principale.

**Intention initiale :** Ajouter une phase "scénario" légère commune à tous les types de workflows (spec, ADR, bug-finding, etc.) qui capturait l'intention initiale dans un artefact `scenario.md`, géré par le MCP, et servait de routeur quand le type n'est pas encore connu.

**Clarifications recueillies :**
- Phase légère (pas de recherche codebase) qui précède la phase propre au type
- Mémoire persistante : dialogue Q&A appendé dans `scenario.md`, rejouable pour comparer l'intention avec le résultat après évolution du plugin
- Rôle de routeur : `/scenario new` démarre sans type, dialogue, propose un type avec confirmation avant création effective
- `/spec new` et équivalents continuent de fonctionner : ils créent le workflow typé mais passent d'abord par la phase scénario
