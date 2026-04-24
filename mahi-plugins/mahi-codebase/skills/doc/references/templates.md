# Templates de documentation

Tous les templates de sortie pour le skill `/doc`. Toute sortie en **Francais**.

## Template : Module (`module-<name>.md`)

```markdown
# Module : <nom>

> Version skill : <skill_version>
> Generé le : <ISO-8601>
> Commit : <hash>
> Fichiers : <count>

## Description

<1-2 lignes décrivant le rôle du module>

## Features

| Feature | Description | Fichier doc |
|---------|-------------|-------------|
| <nom> | <description courte> | [feature-<nom>.md](feature-<nom>.md) |

## Dépendances inter-modules

- **Dépend de** : <modules>
- **Utilisé par** : <modules>

## Configuration consommée

| Clé | Source | Description |
|-----|--------|-------------|
| <env var ou config key> | <.env / config.yml / ...> | <rôle> |

## Patterns et conventions

- <patterns spécifiques au module, conventions locales>
```

## Template : Feature (`feature-<name>.md`)

```markdown
# Feature : <nom>

> Module : <module>
> Version skill : <skill_version>
> Générée le : <ISO-8601>
> Commit : <hash>
> Fichiers : <count>

## Description

<1-2 lignes décrivant le rôle de la feature>

## Entités / Modèles

| Nom | Fichier | Champs clés | Relations |
|-----|---------|-------------|-----------|
| <Entity> | <path> | <champs principaux> | <relations> |

## Services

| Nom | Fichier | Méthodes principales | Orchestre |
|-----|---------|---------------------|-----------|
| <Service> | <path> | <méthodes> | <dépendances> |

## Points d'entrée

| Route / Commande | Méthode | Fichier | Description |
|-------------------|---------|---------|-------------|
| <endpoint> | <GET/POST/...> | <path> | <description> |

## Tables / Migrations

| Table | Fichier migration | Colonnes clés |
|-------|-------------------|---------------|
| <table> | <path> | <colonnes> |

## Patterns spécifiques

- <design patterns utilisés, event flows, conventions locales>
```

## Template : Index (`index.md`)

```markdown
# Documentation du codebase

> Dernière mise à jour : <ISO-8601>
> Version skill : <skill_version>

## Modules

| Module | Features | Fichiers | Dernière génération | État |
|--------|----------|----------|--------------------|----- |
| [<nom>](modules/<nom>/module-<nom>.md) | <count> | <count> | <date> | <frais/obsolète> |

## Graphe de dépendances

<diagramme ASCII des dépendances inter-modules>
```

## Schema : manifest.json

```json
{
  "skill_version": "<schemaVersion depuis .mahi/config.json>",
  "modules": {
    "<module-name>": {
      "path": "<chemin relatif au projet>",
      "generated_at": "<ISO-8601>",
      "skill_version": "<version au moment de la génération>",
      "last_commit": "<hash>",
      "file_count": 0,
      "features": {
        "<feature-name>": {
          "generated_at": "<ISO-8601>",
          "skill_version": "<version>",
          "last_commit": "<hash>",
          "file_count": 0
        }
      },
      "analysis": {
        "generated_at": "<ISO-8601>",
        "skill_version": "<version>",
        "last_commit": "<hash>"
      }
    }
  }
}
```
