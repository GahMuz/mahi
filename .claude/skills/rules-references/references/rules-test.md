# Règles tests

## @TempDir + constructeur pour services fichier

Pour tester un service qui résout un chemin système au démarrage (`@PostConstruct`, `git rev-parse`),
exposer le répertoire de base en paramètre de constructeur :
- Le constructeur de **production** calcule le chemin via git (ou autre mécanisme OS)
- Le constructeur de **test** accepte un `@TempDir` JUnit 5

```java
// Production
public ActiveStateServiceImpl() {
    this.repoRoot = Path.of(runGit("rev-parse", "--show-toplevel"));
}

// Test
ActiveStateServiceImpl(Path repoRoot) {
    this.repoRoot = repoRoot;
}

// Usage test
@Test
void activate_shouldWriteActiveJson(@TempDir Path tempDir) {
    var service = new ActiveStateServiceImpl(tempDir);
    // ...
}
```

Éviter `PowerMock` ou tout mock de `Path`/`File` statique — les opérations réelles sur filesystem
temporaire sont plus fiables, plus lisibles, et détectent les vrais problèmes de permissions/encodage.

> Origine : spec `sdd-spec-worktree-mcp` — `ActiveStateServiceTest`