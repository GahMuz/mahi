# Défense en profondeur

Après avoir trouvé une cause racine, la corriger en un seul endroit paraît suffisant. Mais cette unique vérification peut être contournée par des chemins de code différents, des mocks, ou un refactoring.

**Principe fondamental :** Ajouter de la validation à chaque couche que la mauvaise valeur traverse. Rendre le bug structurellement impossible.

## Les quatre couches

### Couche 1 — Validation en point d'entrée
Rejeter les entrées invalides à la frontière de l'API publique avant qu'elles n'entrent dans le système.

```
function createX(param):
  if param is empty → throw "param cannot be empty"
  if param does not exist → throw "param not found: {param}"
  proceed
```

### Couche 2 — Validation de la logique métier
S'assurer que la valeur est cohérente pour l'opération spécifique en cours.

```
function processX(value, context):
  if value is null → throw "value required for {context}"
  proceed
```

### Couche 3 — Gardes d'environnement
Prévenir les opérations dangereuses dans des contextes spécifiques (tests, staging, etc.).

```
function dangerousOp(target):
  if test environment AND target outside safe directory:
    throw "Refusing {op} outside safe directory during tests: {target}"
  proceed
```

### Couche 4 — Instrumentation de debug
Logger le contexte avant l'opération pour les forensics quand les autres couches échouent.

```
log("About to perform {op}", {
  target,
  caller_stack,
  environment
})
```

## Appliquer le pattern

1. Tracer le flux de données (voir `root-cause-tracing.md`)
2. Lister chaque point par lequel la mauvaise valeur passe
3. Ajouter une couche de validation à chaque point
4. Tester que contourner la couche 1 est rattrapé par la couche 2, etc.

## Pourquoi les quatre couches

Chaque couche attrape ce que les autres manquent :
- Des chemins de code différents contournent la validation en point d'entrée
- Les mocks contournent les vérifications de logique métier
- Les cas limites dans des environnements spécifiques nécessitent des gardes d'environnement
- Le logging de debug identifie l'utilisation incorrecte structurelle quand tout le reste échoue

Un seul point de validation ne suffit pas. Rendre le bug impossible, pas seulement improbable.
