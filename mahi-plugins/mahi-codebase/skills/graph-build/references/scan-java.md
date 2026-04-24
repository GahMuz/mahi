# Protocole de scanning Java Spring Boot — scope module unique

> **Note** : Ce fichier est de la documentation de référence. Le protocole exécutable est embarqué directement dans `agents/graph-builder-java.md`.

Procédure pour l'agent `graph-builder-java`. Un agent = une unité de scan. Toutes les sorties en **JSON** dans `outputPath`.

## Règles d'extraction — non négociables

- **Exhaustivité** : si grep détecte N fichiers, le tableau JSON doit contenir N objets (minimum). Jamais de troncature.
- **Pas de `_note`** : aucun champ hors schéma (`_note`, `_summary`, `_count`…). Tout est dans les tableaux définis.
- **Pas d'invention** : extraire uniquement ce qui est dans le code. Champ absent → `null` ou `[]`.

## Pré-requis

L'agent reçoit :
- `module` : nom du module (ex: `user`)
- `modulePath` : chemin vers les sources du module (ex: `src/main/java/com/acme/user`)
- `rootPackage` : package racine du projet (ex: `com.acme`)
- `outputPath` : `.mahi/graph/partial/<module>/`
- `graphs` : liste des graphes à construire

Créer le répertoire de sortie :
```bash
mkdir -p <outputPath>
```

Initialiser les structures de données en mémoire :
- `endpoints = []`
- `entities = []`
- `serviceNodes = []`, `serviceEdges = []`
- `moduleImports = {}` (clé = module cible, valeur = poids)

---

## Pass 1 : Extraction des entités JPA

Si `entity-model` absent de la liste `graphs` → sauter cette passe.

### 1a. Lister les fichiers entity du module

```bash
grep -rn "@Entity" --include="*.java" -l <modulePath>
```

### 1b. Pour chaque fichier entity : lire et extraire

Lire le fichier. Extraire :

**Nom de la classe** :
```bash
grep "^public class \|^public abstract class \|^class " <fichier>
```

**Table** :
```bash
grep "@Table" <fichier>
```
→ extraire `name=` si présent. Sinon convertir le nom de classe en snake_case.

**Héritage** :
```bash
grep "extends " <fichier>
```
→ si `extends <NomEntite>`, noter `parentEntity`.

**Stratégie d'héritage** :
```bash
grep "@Inheritance" <fichier>
```
→ extraire `strategy=`.

**Champs** :
Lire le fichier et identifier les lignes avec `@Id`, `@Column`, `@OneToMany`, `@ManyToOne`, `@ManyToMany`, `@OneToOne`, `@JoinColumn`.
Pour chaque annotation : lire la ligne suivante pour obtenir le nom et type du champ.
Extraire l'annotation complète (avec paramètres).
Pour les relations : extraire `mappedBy`, `fetch`, `cascade` si présents.

### 1c. Construire l'objet entity

Suivre le schéma `entities[]` de `references/templates.md` section `entity-model.json`.
Champ `module` = `<module>` reçu en paramètre.

Ajouter à `entities[]`.

---

## Pass 2 : Extraction des endpoints

Si `endpoint-flow` absent de la liste `graphs` → sauter cette passe.

### 2a. Lister les fichiers controller du module

```bash
grep -rn "@RestController\|@Controller" --include="*.java" -l <modulePath>
```

### 2b. Pour chaque fichier controller : extraire

**Path de base** :
```bash
grep "@RequestMapping" <fichier>
```
→ extraire la valeur. Peut être absent.

**Pour chaque méthode avec mapping HTTP** :
```bash
grep -n "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping" <fichier>
```
Pour chaque occurrence :
- Extraire le path de l'annotation
- Construire le path complet : `basePath + methodPath`
- Lire les lignes suivantes pour la signature de la méthode
- Extraire `@RequestBody` param → `requestDto`
- Extraire le type de retour → `responseDto`
- Extraire `@PreAuthorize` ou `@Secured` → `security`

**Services injectés dans le controller** (voir Stratégies A et B en Pass 3) :
→ Identifier les types `*Service` injectés

**Pour chaque service injecté** :
- Glob `**/<ServiceName>.java` pour localiser le fichier
- Extraire les repositories injectés dans ce service (types `*Repository`)
- Pour chaque repository : déduire l'entité (ex: `UserRepository` → `User`)

### 2c. Construire l'objet endpoint

Suivre le schéma `endpoints[]` de `references/templates.md`.
Champ `module` = `<module>`.
Ne pas attribuer d'`id` global — l'`id` sera `"<module>_ep_<index>"` (ex: `user_ep_001`).
Le skill réassignera les IDs globaux lors de l'assemblage.

Ajouter à `endpoints[]`.

---

## Pass 3 : Extraction du graphe de services

Si `service-call` absent de la liste `graphs` → sauter cette passe.

### 3a. Lister les fichiers service et repository du module

Services :
```bash
grep -rn "@Service" --include="*.java" -l <modulePath>
```

Repositories :
```bash
grep -rn "@Repository\|extends JpaRepository\|extends CrudRepository\|extends PagingAndSortingRepository" --include="*.java" -l <modulePath>
```

### 3b. Pour chaque service : extraire les dépendances injectées

Lire le fichier. Appliquer **les deux stratégies** :

---

**Stratégie A — Injection par champ (`@Autowired` field)**

```bash
grep -n "@Autowired" <fichier>
```
Pour chaque occurrence :
1. Lire la ligne suivante : `private <Type> <nom>`
2. Si `Type` finit par `Service`, `Repository`, `Component`, `Manager` ou `Client` → dépendance détectée
3. `injectionType: "field"`

---

**Stratégie B — Injection par constructeur**

```bash
grep -n "public <NomClasse>(" <fichier>
```
Pour chaque occurrence :
1. Lire les lignes de la signature jusqu'au `)` ou `{`
2. Extraire chaque paramètre de type `<Type> <nom>`
3. Si `Type` finit par `Service`, `Repository`, `Component`, `Manager` ou `Client` → dépendance détectée
4. `injectionType: "constructor"`

---

**Priorité en cas de doublon** : même type détecté par A et B → un seul edge, `injectionType: "constructor"`.

### 3c. Construire les nœuds et edges

**Nœud** : un nœud par service/repository unique du module.
```json
{
  "id": "svc_UserService",
  "name": "UserService",
  "file": "<chemin relatif depuis racine>",
  "module": "<module>",
  "type": "service"
}
```
Pour les repositories : `"id": "repo_UserRepository"`, `"type": "repository"`.

**Edge** : une edge par dépendance injectée.
```json
{
  "from": "svc_UserService",
  "to": "svc_EmailService",
  "callSite": "<fichier>:<ligne>",
  "injectionType": "constructor"
}
```

Note : si le type injecté appartient à un **autre module** (son fichier est hors `modulePath`), créer quand même l'edge — l'ID du nœud cible sera `svc_<NomType>` et le skill le résoudra lors de l'assemblage cross-module.

Ajouter nœuds à `serviceNodes[]` et edges à `serviceEdges[]`.

---

## Pass 4 : Extraction des dépendances inter-modules

Si `module-dep` absent de la liste `graphs` → sauter cette passe.

### 4a. Lister les imports cross-module du module courant

```bash
grep -rhn "^import <rootPackage>\." --include="*.java" <modulePath>
```

Pour chaque import :
- Extraire le sous-package cible (segment après `rootPackage`)
- Si ce sous-package est différent du module courant → c'est un import cross-module
- Incrémenter `moduleImports[<sous-package-cible>]`

Exemple : `import com.acme.notification.EmailService` dans le module `user` → `moduleImports["notification"]++`

Ignorer les imports vers des packages qui ne commencent pas par `rootPackage` (libs externes).

---

## Pass 5 : Extraction de la hiérarchie de types

Si `type-hierarchy` absent de la liste `graphs` → sauter cette passe.

### 5a. Interfaces du module

```bash
grep -rn "^public interface \|^interface " --include="*.java" -l <modulePath>
```

Pour chaque interface : extraire nom, interfaces parentes (filtrer au `rootPackage`).

Trouver les implémenteurs dans le **projet entier** (pas seulement le module) :
```bash
grep -rn "implements.*<NomInterface>" --include="*.java" <sourcePath>
```
où `<sourcePath>` = parent de `modulePath` au niveau du package racine.

### 5b. Classes abstraites du module

```bash
grep -rn "^public abstract class \|^abstract class " --include="*.java" -l <modulePath>
```

Pour chaque classe abstraite : trouver les sous-classes dans le projet entier :
```bash
grep -rn "extends <NomClasseAbstraite>" --include="*.java" <sourcePath>
```

---

## Pass 6 : Extraction de la configuration

Si `config-env` absent de la liste `graphs` → sauter cette passe.

### 6a. @Value dans le module

```bash
grep -rn "@Value" --include="*.java" <modulePath>
```

Pour chaque occurrence : extraire la clé `${...}` et le contexte (classe, champ).

### 6b. Fichiers de configuration (une seule fois, module partagé)

Si ce module est le premier à être scanné pour `config-env`, lire les fichiers de config :
```bash
find . -maxdepth 4 -name "application.yml" -o -name "application.properties" | head -5
```
Extraire toutes les clés définies.

Note : cette passe est légère — les configs sont généralement petites. Le skill dédupliquera les clés lors de l'assemblage.

---

## Pass 7 : Écriture des partiels

Créer le répertoire de sortie si nécessaire.

Écrire les fichiers partiels selon les graphes construits :

| Fichier | Contenu | Graphe |
|---------|---------|--------|
| `<outputPath>/endpoints.json` | `{ "module": "<nom>", "endpoints": [...] }` | endpoint-flow |
| `<outputPath>/entities.json` | `{ "module": "<nom>", "entities": [...] }` | entity-model |
| `<outputPath>/service-nodes.json` | `{ "module": "<nom>", "nodes": [...] }` | service-call |
| `<outputPath>/service-edges.json` | `{ "module": "<nom>", "edges": [...] }` | service-call |
| `<outputPath>/module-imports.json` | `{ "module": "<nom>", "importsFrom": {...} }` | module-dep |
| `<outputPath>/type-nodes.json` | `{ "module": "<nom>", "interfaces": [...], "abstractClasses": [...] }` | type-hierarchy |
| `<outputPath>/config-usages.json` | `{ "module": "<nom>", "usages": [...] }` | config-env |

Ne créer que les fichiers pour les graphes présents dans la liste `graphs`.

### Retourner les métadonnées

```
module: <nom>
lastCommit: <git log -1 --format=%H -- <modulePath>>
counts:
  endpoints: <N>
  entities: <N>
  serviceNodes: <N>
  serviceEdges: <N>
  moduleImports: <N clés>
warnings:
  - <cas ambigus>
```

---

## Cas limites et conventions

| Situation | Comportement |
|-----------|-------------|
| Classe `@Service` sans constructeur public ni `@Autowired` | Nœud créé, aucun edge |
| Même type détecté par @Autowired ET constructeur | Un seul edge, `injectionType: "constructor"` |
| Constructeur avec paramètres sur plusieurs lignes | Lire jusqu'au `)` ou `{` pour obtenir tous les params |
| Service injecté depuis un autre module | Edge créé avec `id` cible hypothétique — le skill résout lors du merge |
| Path `@RequestMapping` avec variable (`${app.base-path}`) | Conserver la valeur littérale, noter en warning |
| Entité sans `@Table` | Utiliser le nom de classe en snake_case comme table |
| Repository sans entité détectable | Nœud créé sans entité associée |
| Import vers lib externe (non-`rootPackage`) | Ignorer pour `module-dep` |
| Interface sans implémenteur dans le projet | Ne pas inclure dans `type-nodes.json` |
| Clé `@Value` non définie dans `application.yml` | Inclure avec `source: "non-définie"`, ajouter warning |
| Module > 200 fichiers Java | Traiter quand même — l'agent scanne un seul module à la fois |
