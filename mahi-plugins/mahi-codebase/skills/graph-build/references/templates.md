# Templates d'artifacts — plugin mahi-codebase

Tous les schémas JSON produits par `/graph-build`. Toute sortie en **français** pour les messages utilisateur, les clés JSON restent en anglais.

---

## Schema : `manifest.json`

```json
{
  "schemaVersion": "<schemaVersion depuis .mahi/config.json>",
  "updatedAt": "<ISO-8601>",
  "stacks": ["java"],
  "sourcePaths": {
    "java": "<chemin relatif racine Java, ex: src/main/java>"
  },
  "graphs": {
    "endpoint-flow": {
      "builtAt": "<ISO-8601>",
      "lastCommit": "<hash git>",
      "entryCount": 0,
      "status": "fresh"
    },
    "entity-model": {
      "builtAt": "<ISO-8601>",
      "lastCommit": "<hash git>",
      "entryCount": 0,
      "status": "fresh"
    },
    "service-call": {
      "builtAt": "<ISO-8601>",
      "lastCommit": "<hash git>",
      "entryCount": 0,
      "status": "fresh"
    },
    "module-dep": {
      "builtAt": "<ISO-8601>",
      "lastCommit": "<hash git>",
      "entryCount": 0,
      "status": "fresh"
    }
  }
}
```

Valeurs `status` : `"fresh"` | `"stale"` | `"partial"` | `null` (jamais construit)

**Graphes disponibles dans le manifest :**
- `endpoint-flow` — P1, construit par défaut
- `entity-model` — P1, construit par défaut
- `service-call` — P1, construit par défaut
- `module-dep` — P1, construit par défaut
- `type-hierarchy` — P2, opt-in (`--graphs type-hierarchy`)
- `config-env` — P2, opt-in (`--graphs config-env`)

---

## Schema : `endpoint-flow.json`

```json
{
  "schemaVersion": "1.0.0",
  "generatedAt": "<ISO-8601>",
  "lastCommit": "<hash git>",
  "stack": "java",
  "endpoints": [
    {
      "id": "ep_001",
      "method": "GET",
      "path": "/api/v1/users/{id}",
      "module": "user",
      "controller": {
        "class": "UserController",
        "file": "src/main/java/com/acme/user/UserController.java",
        "method": "getUser",
        "line": 45
      },
      "services": [
        {
          "class": "UserService",
          "file": "src/main/java/com/acme/user/UserService.java",
          "method": "findById",
          "line": 23
        }
      ],
      "repositories": [
        {
          "class": "UserRepository",
          "file": "src/main/java/com/acme/user/UserRepository.java"
        }
      ],
      "entities": ["User"],
      "tables": ["users"],
      "requestDto": null,
      "responseDto": "UserResponse",
      "security": ["ROLE_USER"]
    }
  ]
}
```

Notes :
- `id` : `ep_<index 3 chiffres>` (séquentiel dans l'ordre de détection)
- `method` : `GET` | `POST` | `PUT` | `DELETE` | `PATCH`
- `services` : liste des services dans la chaîne d'appel (du controller vers le bas)
- `repositories` : repos injectés dans les services de la chaîne (pas les interfaces de base JpaRepository)
- `entities` : noms simples des entités JPA touchées par l'endpoint
- `tables` : noms de tables extraits des `@Table(name=...)` des entités
- `requestDto` : classe `@RequestBody` si présente, sinon `null`
- `responseDto` : type de retour de la méthode controller
- `security` : valeurs `@PreAuthorize` / `@Secured` si présentes, sinon `[]`
- `line` : numéro de ligne de la déclaration de méthode (approximatif, issu du grep)

---

## Schema : `entity-model.json`

```json
{
  "schemaVersion": "1.0.0",
  "generatedAt": "<ISO-8601>",
  "lastCommit": "<hash git>",
  "entities": [
    {
      "name": "User",
      "file": "src/main/java/com/acme/user/domain/User.java",
      "table": "users",
      "module": "user",
      "fields": [
        {
          "name": "id",
          "type": "Long",
          "column": "id",
          "annotations": ["@Id", "@GeneratedValue(strategy=AUTO)"]
        },
        {
          "name": "email",
          "type": "String",
          "column": "email",
          "annotations": ["@Column(unique=true, nullable=false)"]
        },
        {
          "name": "orders",
          "type": "List<Order>",
          "column": null,
          "annotations": ["@OneToMany(mappedBy='user', fetch=LAZY)"],
          "relation": {
            "type": "ONE_TO_MANY",
            "target": "Order",
            "mappedBy": "user",
            "fetch": "LAZY"
          }
        }
      ],
      "relations": [
        {
          "type": "ONE_TO_MANY",
          "target": "Order",
          "mappedBy": "user",
          "fetch": "LAZY",
          "cascade": "ALL"
        }
      ],
      "inheritanceStrategy": null,
      "parentEntity": null
    }
  ]
}
```

Notes :
- `table` : extrait de `@Table(name=...)`, sinon nom de classe en snake_case
- `fields` : uniquement les champs avec annotations JPA ou de relation (ignorer les champs transitoires)
- `column` : `null` pour les champs de relation (pas de colonne directe)
- `relation.cascade` : extrait de l'annotation si présent, sinon `null`
- `inheritanceStrategy` : `"SINGLE_TABLE"` | `"JOINED"` | `"TABLE_PER_CLASS"` | `null`
- `parentEntity` : nom de l'entité parente si `extends`, sinon `null`

---

## Schema : `service-call.json`

```json
{
  "schemaVersion": "1.0.0",
  "generatedAt": "<ISO-8601>",
  "lastCommit": "<hash git>",
  "nodes": [
    {
      "id": "svc_UserService",
      "name": "UserService",
      "file": "src/main/java/com/acme/user/UserService.java",
      "module": "user",
      "type": "service"
    },
    {
      "id": "svc_EmailService",
      "name": "EmailService",
      "file": "src/main/java/com/acme/notification/EmailService.java",
      "module": "notification",
      "type": "service"
    },
    {
      "id": "repo_UserRepository",
      "name": "UserRepository",
      "file": "src/main/java/com/acme/user/UserRepository.java",
      "module": "user",
      "type": "repository"
    }
  ],
  "edges": [
    {
      "from": "svc_UserService",
      "to": "svc_EmailService",
      "callSite": "src/main/java/com/acme/user/UserService.java:67",
      "injectionType": "constructor"
    },
    {
      "from": "svc_UserService",
      "to": "repo_UserRepository",
      "callSite": "src/main/java/com/acme/user/UserService.java:15",
      "injectionType": "constructor"
    }
  ]
}
```

Notes :
- `id` : `svc_<NomClasse>` pour les services, `repo_<NomClasse>` pour les repositories
- `type` : `"service"` | `"repository"`
- `injectionType` : `"constructor"` | `"field"` (selon la forme Spring DI détectée)
- Les edges représentent des dépendances d'injection (pas des appels de méthode directs — trop coûteux à extraire statiquement)
- Un edge `svc_A → svc_B` signifie : le service A a B dans son constructeur ou en `@Autowired`

---

## Schema : `module-dep.json`

```json
{
  "schemaVersion": "1.0.0",
  "generatedAt": "<ISO-8601>",
  "lastCommit": "<hash git>",
  "rootPackage": "com.acme",
  "modules": [
    {
      "id": "mod_user",
      "name": "user",
      "path": "src/main/java/com/acme/user",
      "package": "com.acme.user",
      "dependsOn": ["mod_notification", "mod_shared"],
      "usedBy": ["mod_order", "mod_admin"]
    },
    {
      "id": "mod_order",
      "name": "order",
      "path": "src/main/java/com/acme/order",
      "package": "com.acme.order",
      "dependsOn": ["mod_user", "mod_payment", "mod_shared"],
      "usedBy": ["mod_admin"]
    }
  ],
  "couplingMatrix": {
    "mod_user": {
      "mod_notification": 3,
      "mod_shared": 12
    },
    "mod_order": {
      "mod_user": 5,
      "mod_payment": 8,
      "mod_shared": 7
    }
  }
}
```

Notes :
- `rootPackage` : package commun racine (ex: `com.acme`), détecté par le préfixe commun des packages
- `dependsOn` : modules dont ce module importe des classes
- `usedBy` : calculé en inversant les `dependsOn` (pas scanné directement)
- `couplingMatrix[A][B]` : nombre d'instructions `import com.acme.B.*` dans les fichiers du module A
- Seuls les imports vers d'autres modules du projet sont comptés (pas les libs externes)

---

## Schema : `type-hierarchy.json`

```json
{
  "schemaVersion": "1.0.0",
  "generatedAt": "<ISO-8601>",
  "lastCommit": "<hash git>",
  "interfaces": [
    {
      "name": "UserPort",
      "file": "src/main/java/com/acme/user/UserPort.java",
      "module": "user",
      "extendsInterfaces": [],
      "implementors": [
        {
          "name": "UserService",
          "file": "src/main/java/com/acme/user/UserService.java",
          "isAbstract": false
        }
      ]
    }
  ],
  "abstractClasses": [
    {
      "name": "BaseEntity",
      "file": "src/main/java/com/acme/shared/BaseEntity.java",
      "module": "shared",
      "subclasses": [
        {
          "name": "User",
          "file": "src/main/java/com/acme/user/domain/User.java"
        }
      ]
    }
  ]
}
```

Notes :
- `extendsInterfaces` : interfaces parentes dans le projet (vide si aucune, ou si les parentes sont externes)
- `isAbstract` : `true` si l'implémenteur est lui-même abstrait
- Seules les interfaces/classes abstraites **avec au moins un implémenteur/sous-classe dans le projet** sont incluses
- Interfaces sans implémenteur interne (ex: `Serializable`) sont ignorées

---

## Schema : `config-env.json`

```json
{
  "schemaVersion": "1.0.0",
  "generatedAt": "<ISO-8601>",
  "lastCommit": "<hash git>",
  "configFiles": [
    "src/main/resources/application.yml",
    "src/main/resources/application-prod.yml"
  ],
  "properties": [
    {
      "key": "app.security.jwt.secret",
      "source": "application.yml",
      "usages": [
        {
          "class": "JwtService",
          "file": "src/main/java/com/acme/security/JwtService.java",
          "fieldName": "jwtSecret",
          "module": "security"
        }
      ]
    }
  ]
}
```

Notes :
- `source` : nom du fichier de config où la clé est définie, ou `"non-définie"` si absente (warning)
- `usages` : tous les endroits dans le code qui lisent cette clé via `@Value("${<clé>}")`
- Les valeurs par défaut `@Value("${clé:valeur-défaut}")` sont incluses — noter `hasDefault: true` si présent

---

## Template : `index.md`

```markdown
# Graphe structurel du codebase

> Dernière mise à jour : <ISO-8601>
> Version schema : <schemaVersion depuis .mahi/config.json>
> Stacks : <liste>

## Graphes disponibles

| Graphe | Entrées | Construit le | État |
|--------|---------|--------------|------|
| endpoint-flow | <N> | <date> | frais / obsolète |
| entity-model | <N> | <date> | frais / obsolète |
| service-call | <N> nœuds, <M> edges | <date> | frais / obsolète |
| module-dep | <N> modules | <date> | frais / obsolète |

## Modules détectés

<liste des modules avec leur path>

## Dépendances inter-modules (ASCII)

<diagramme ASCII du module-dep.json>

## Pour mettre à jour

```
/graph-build --java            # rebuild complet Java
/graph-build --incremental     # rebuild uniquement les graphes obsolètes
/graph-query <question>        # interroger le graphe
```
```
