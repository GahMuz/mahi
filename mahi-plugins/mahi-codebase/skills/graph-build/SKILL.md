---
name: graph-build
description: "Ce skill est utilisé quand l'utilisateur invoque '/graph-build' pour construire ou rafraîchir les graphes structurels du code (flux d'endpoints, modèles d'entités, graphes d'appels de services, dépendances de modules). Supporte Java Spring Boot. Dispatche un agent par module en parallèle. Produit des artifacts JSON dans .mahi/graph/."
argument-hint: "[--java] [--all] [--incremental] [--module <nom>] [--graphs endpoint-flow,entity-model,service-call,module-dep,type-hierarchy,config-env]"
allowed-tools: ["Read", "Write", "Glob", "Grep", "Bash", "Agent"]
---

# Construction des graphes structurels

Pré-calculer les graphes du codebase Java Spring Boot. Pour les codebases volumineux, dispatch **un agent par unité de scan en parallèle** — chaque agent écrit ses partiels dans `.mahi/graph/partial/<scanName>/`, le skill assemble les JSON finaux en agrégeant par module logique.

Les modules avec beaucoup de services reçoivent un **dispatch séparé en batches** pour Pass 3 (service-call), évitant la saturation de contexte haiku.

## Répertoire de sortie

```
.mahi/graph/
├── manifest.json
├── index.md
├── endpoint-flow.json
├── entity-model.json
├── service-call.json
├── module-dep.json
└── partial/
    ├── <scanName>/
    │   ├── endpoints.json
    │   ├── entities.json
    │   ├── service-nodes.json
    │   ├── service-edges.json
    │   ├── module-imports.json
    │   └── services-batch-001/
    │       ├── service-nodes.json
    │       └── service-edges.json
    └── ...
```

## Step 0 : Parser les arguments

- `--java` → BUILD_JAVA (modules : tous)
- `--all` → BUILD_JAVA
- `--module <nom>` → BUILD_MODULE (un seul module ciblé)
- `--incremental` → BUILD_INCREMENTAL (uniquement les modules stale)
- `--graphs <liste>` → limiter aux graphes spécifiés (P2 : `type-hierarchy`, `config-env`)
- aucun argument → détecter les stacks, afficher les modules disponibles, demander confirmation

**Graphes P1** (construits par défaut) : `endpoint-flow`, `entity-model`, `service-call`, `module-dep`
**Graphes P2** (opt-in via `--graphs`) : `type-hierarchy`, `config-env`

## Step 1 : Détecter le stack Java et les modules

### 1a. Confirmer la présence Java

```bash
find . -maxdepth 4 -name "pom.xml" | head -10
find . -maxdepth 4 -name "build.gradle" -o -name "build.gradle.kts" | head -10
```

Si rien trouvé : "Aucun projet Java détecté."

Identifier `sourcePath` :
- Maven mono/multi : `src/main/java`
- Gradle : vérifier `sourceSets`, sinon supposer `src/main/java`

### 1b. Détecter le package racine

```bash
grep -rn "^package " --include="*.java" <sourcePath> | head -50
```

Identifier le préfixe commun (ex: `com.acme`). Ce sera `rootPackage`.

### 1c. Détecter les modules

```bash
find <sourcePath> -mindepth 3 -maxdepth 3 -type d | sort
```

Les répertoires de premier niveau sous le package racine = modules.
Convertir le chemin en nom de module (dernier segment du répertoire).

Afficher : "Modules détectés : user, order, payment, notification, shared (<N> total)"

### 1d. Mesurer la taille des modules et décider du découpage

Lire dans `.mahi/config.json` :
- `graph.moduleThreshold` (défaut : **25**) — seuil pour découper en sous-packages
- `graph.serviceThreshold` (défaut : **30**) — seuil pour batcher Pass 3 séparément

Pour chaque module, compter les fichiers Java avec annotations clés :
```bash
grep -rn "@Entity\|@Service\|@RestController\|@Controller\|@Repository" --include="*.java" -l <modulePath> | wc -l
```

Si le compte dépasse `moduleThreshold` → **découper par sous-package**.
Chaque sous-répertoire direct = un sous-module de scanning.
Nommage : `<module>-<souspackage>` (ex: `core-user`, `core-order`).

Pour chaque unité, si `serviceFileCount > serviceThreshold` → marquer `needsServiceBatch: true`.

### 1e. Évaluer la fraîcheur (mode --incremental)

```bash
git log <lastCommit>..HEAD -- <scanPath> --oneline
```

Si output non vide → unité stale → à reconstruire.
En mode `--java` ou `--module` : ignorer la fraîcheur, toujours reconstruire.

### 1f. Vérifier l'intégrité des partiels existants

Sauter en mode `--java` ou `--module` (rebuild total).
Pour les autres modes : comparer les comptes JSON vs fichiers annotés. Si incomplet → marquer stale.

## Step 2 : Lire le manifest existant

Lire `.mahi/graph/manifest.json` si existant, sinon créer un manifest vide.

## Step 3 : Dispatch parallèle

### 3a. Dispatch principal — passes 1, 2, 4 (entities + endpoints + imports)

Pour **toutes** les unités à (re)construire, dispatcher en parallèle :

```
Agent({
  description: "Graphe <scanName> (entities+endpoints+imports)",
  subagent_type: "graph-builder-java",
  model: <config.models.graph-builder ou "haiku">,
  prompt: "
    module: <moduleName>
    scanName: <scanName>
    modulePath: <scanPath>
    rootPackage: <rootPackage>
    outputPath: .mahi/graph/partial/<scanName>/
    graphs: <liste des graphes>
  "
})
```

### 3b. Dispatch services en batches (unités volumineuses uniquement)

Pour chaque unité avec `needsServiceBatch: true` :
Découper en batches de `serviceThreshold` fichiers et dispatcher en parallèle :

```
Agent({
  description: "Services <scanName> batch-<N>/<total>",
  subagent_type: "graph-builder-java",
  model: <config.models.graph-builder ou "haiku">,
  prompt: "
    module: <moduleName>
    scanName: <scanName>
    modulePath: <scanPath>
    rootPackage: <rootPackage>
    outputPath: .mahi/graph/partial/<scanName>/services-batch-<N>/
    graphs: service-call
    serviceFiles:
      - <chemin/vers/ServiceA.java>
      ...
  "
})
```

## Step 4 : Assembler les JSON finaux depuis les partiels

Voir le protocole détaillé dans `references/scan-java.md`. En résumé :

- **`endpoint-flow.json`** : concaténer tous les `endpoints[]`, renuméroter globalement
- **`entity-model.json`** : concaténer tous les `entities[]`
- **`service-call.json`** : fusionner nœuds (dédupliquer par `id`) + edges de tous les partiels et batches
- **`module-dep.json`** : agréger `importsFrom` par module logique, calculer `dependsOn`/`usedBy`

## Step 5 : Mettre à jour le manifest

```bash
git log -1 --format=%H -- <sourcePath>
```

Mettre à jour `manifest.json` avec `builtAt`, `lastCommit`, `entryCount`, `status: "fresh"` pour chaque graphe.
Générer `.mahi/graph/index.md` (template dans `references/templates.md`).

## Step 6 : Reporter

```
Graphes construits — <N> unités de scan (<M> modules logiques)

- endpoint-flow : <N> endpoints
- entity-model  : <N> entités
- service-call  : <N> nœuds, <M> edges
- module-dep    : <N> modules

Répertoire : .mahi/graph/
Utilisation : /graph-query <question>
```

## Commandes disponibles

| Commande | Description |
|----------|-------------|
| `/graph-build --java` | Scanner tous les modules Java en parallèle |
| `/graph-build --module user` | Scanner uniquement le module `user` |
| `/graph-build --incremental` | Reconstruire uniquement les modules modifiés ou incomplets |
| `/graph-build --graphs type-hierarchy,config-env` | Construire les graphes P2 |
| `/graph-status` | Afficher l'état du manifest |
| `/graph-query <question>` | Interroger les graphes (plugin mahi) |
