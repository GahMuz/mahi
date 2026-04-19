---
name: commit
description: "This skill should be used when the user invokes '/commit' to generate a structured, purpose-driven commit message with risk assessment and breaking changes. Analyses staged/unstaged changes, categorizes files, and produces a French commit message following conventional commit format."
argument-hint: "[ticket ID, feature name, or notes]"
allowed-tools: ["Read", "Glob", "Grep", "Bash"]
---

# Smart Commit Message Generator

Generate a structured, purpose-driven commit message with risk assessment and breaking changes.

**Additional context**: $ARGUMENTS (optional: ticket ID, feature name, or notes)

---

## Step 1: GATHER CHANGES

Run these git commands:
- `git status` — modified, added, deleted files
- `git diff --staged` — staged changes (if any)
- `git diff` — unstaged changes
- `git diff --stat` — summary per file
- `git log --oneline -5` — recent commits for style context

If nothing is staged, show what will be committed and ask confirmation.

## Step 2: ANALYZE CHANGES

Detect the project stack from `package.json`, `pom.xml`, `composer.json`, `build.gradle`, or file extensions. Adapt file categories accordingly. Examples by stack:

**Java/Spring** : Entity (`**/entity/*.java`), Service (`**/service/**/*.java`), Controller (`**/rest/*.java`), DTO (`**/dto/*.java`), Liquibase (`**/changelog/*.xml`), Test (`**/test/**/*.java`), Config (`*.yml`, `*.xml`, `pom.xml`)

**Node/TypeScript** : Routes/Controllers (`**/routes/**`, `**/controllers/**`), Services (`**/services/**`), Models/Schema (`**/models/**`, `**/*.schema.ts`), Test (`**/*.spec.ts`, `**/*.test.ts`), Config (`*.json`, `*.env*`, `tsconfig.json`)

**PHP/Laravel** : Models (`**/Models/**`), Controllers (`**/Controllers/**`), Migrations (`**/migrations/**`), Tests (`**/Tests/**`), Config (`config/**`, `*.env`)

For unrecognized stacks, group by directory and file type and apply the same Impact assessment logic.

Assess:
- **Purpose**: new feature, bug fix, refactor, chore, schema change, security fix
- **Scope**: which module(s) are affected
- **Breaking changes**: do any changes modify public APIs, entity schemas, or existing behavior?
- **Risk level**:
  - LOW: tests only, docs, config
  - MEDIUM: new code, no schema change
  - HIGH: entity changes, Liquibase, security/@PreAuthorize changes

## Step 3: GENERATE COMMIT MESSAGE

**Langue** : Le message de commit est rédigé en français (sauf le type et le scope qui restent en anglais).
**IMPORTANT** : Ne JAMAIS ajouter de mention Co-Authored-By, Claude, Anthropic, assistant ou IA dans le message de commit.

Format :
```
{type}({scope}): {résumé concis — impératif, < 72 chars}

{1-3 phrases expliquant POURQUOI ce changement a été fait, pas CE QUI a changé}

Changements :
- {changement significatif 1}
- {changement significatif 2}
- {changement significatif 3 si nécessaire}

Ruptures :
- {description — ou "Aucune"}

Risque : {LOW|MEDIUM|HIGH} — {justification en 1 ligne}
```

### Type values
| Type | When to use |
|------|-------------|
| feat | New feature or capability |
| fix | Bug fix |
| refactor | Code restructuring without behavior change |
| schema | Entity + Liquibase migration |
| security | Tenant isolation, @PreAuthorize, auth changes |
| test | Test additions/modifications only |
| chore | Build, config, dependency changes |
| docs | Documentation only |

### Scope values
Derive scope dynamically from the modules/directories touched — do not use hardcoded names. Use the short name of the affected module (e.g. `auth`, `user`, `payment`). If a `manifest.json` or equivalent module index exists, use its module names. Cross-module changes: `multi` or list 2-3 most impacted.

### Règles
- Première ligne < 72 caractères
- Mode impératif : "ajouter", "corriger", "mettre à jour"
- Liste des changements : éléments significatifs uniquement
- Section Ruptures : JAMAIS omise — écrire "Aucune" explicitement
- Risque : schema = HIGH, nouveaux endpoints = MEDIUM, tests seuls = LOW, sécurité = HIGH
- **JAMAIS** de Co-Authored-By, Claude, Anthropic, assistant, IA dans le message

## Step 4: PRESENT AND CONFIRM

Présenter le message de commit. Demander : "On commit ? (oui / modifier / annuler)"

If "oui":
- Stage relevant files — exclude:
  - Build artifacts: `target/`, `dist/`, `build/`, `.next/`, `out/`
  - Dependencies: `vendor/`, `node_modules/`
  - Environment files: `.env`, `.env.*`
  - IDE/OS noise: `.DS_Store`, `*.iml`, `.idea/`
- Commit with the message
- Show commit hash

## Step 5: RAPPELS POST-COMMIT
- si java
  - Si fichiers entity modifiés : "Vérifier que le changelog Liquibase est inclus"
  - Si fichiers controller modifiés : "Vérifier la couverture @PreAuthorize"
  - Si risque HIGH : "Envisager de lancer `/review` avant de pusher"
