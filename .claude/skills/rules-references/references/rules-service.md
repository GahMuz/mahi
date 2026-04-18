# Règles services

## Résolution du repo root dans les services MCP

Quand un service MCP gère des fichiers machine-locaux qui doivent toujours pointer vers le repo root (même
depuis un worktree), résoudre le chemin absolu au démarrage via `git rev-parse --show-toplevel` dans
`@PostConstruct` et mémoriser le résultat. Le skill/LLM ne passe jamais de chemin dans ses appels MCP —
la résolution est une responsabilité exclusive du serveur.

```java
@PostConstruct
void init() {
    this.repoRoot = Path.of(runGit("rev-parse", "--show-toplevel"));
}
```

> Origine : spec `sdd-spec-worktree-mcp` — `ActiveStateService`

## Séparation des services selon la portée des fichiers gérés

Séparer les services selon la portée des fichiers gérés :
- Fichiers à portée **machine/repo** (`active.json`, `registry.md` au repo root) → service dédié avec
  chemin résolu au démarrage (voir règle ci-dessus)
- Fichiers à portée **spec/worktree** (`state.json` dans le répertoire de la spec) → service distinct
  avec chemin passé en paramètre à chaque appel

Ne pas fusionner dans un seul service "StateManager" : les stratégies de résolution de chemin sont
différentes et les tests d'isolation seraient plus complexes.

> Origine : spec `sdd-spec-worktree-mcp` — `ActiveStateService` vs `StateFileService`