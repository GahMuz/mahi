---
name: evolve
description: "This skill should be used when the user invokes '/evolve' to improve the project's .claude/ configuration, add project-specific skills or rules, optimize token budget, audit configuration quality, or import a skill from another project."
argument-hint: "<action: add | optimize | audit | import <path>>"
allowed-tools: ["Read", "Write", "Edit", "Glob", "Grep"]
---

# Évolution de la configuration projet

Gère la configuration `.claude/` du projet comme un artefact vivant. Tout output en français.

**Périmètre d'écriture : `.claude/` uniquement.**
Les règles plugin (`mahi-plugins/mahi/skills/rules/`) sont en lecture seule — elles voyagent avec le plugin et ne sont pas modifiables ici.

## Actions

| Action | Description |
|--------|-------------|
| `add <description>` | Ajouter un skill ou une règle projet dans `.claude/` |
| `optimize` | Réduire le budget tokens, éliminer doublons, resynchroniser l'index des règles |
| `audit` | Rapport complet : sécurité, granularité, cohérence, budget tokens |
| `import <path>` | Importer un skill depuis un autre projet ou un plugin installé |

---

## Process

### Step 1 : Inventaire

**Règles projet** — `Glob .claude/skills/rules-references/references/rules*.md` :
- Lister chaque fichier avec nb de lignes et domaine

**Skills projet** — `Glob .claude/skills/*/SKILL.md` :
- Lister avec nb de lignes

**Hooks** — `Glob .claude/hooks/**` :
- Lister si présents

**Règles plugin (référence, lecture seule)** — `Glob mahi-plugins/mahi/skills/rules/references/rules-*.md` :
- Lister pour détecter les doublons potentiels — ne pas modifier

Afficher : `"Configuration projet : X skills, Y règles projet, Z hooks. Budget tokens estimé : ~N tokens. Règles plugin disponibles (lecture seule) : W fichiers."`

### Step 2 : Scan sécurité

Pour tout fichier nouveau ou modifié, vérifier :
- Pas d'injection shell (eval, exec, backtick)
- Pas d'appels réseau sans consentement explicite
- Pas de secrets ou credentials dans le contenu
- Writes strictement limités à `.claude/` — jamais ailleurs
- `settings.json` et `settings.local.json` en lecture seule — ne jamais proposer de les modifier

Afficher : `"Scan sécurité : X problèmes"` ou `"Aucun problème"`

### Step 3 : Vérification doublons

Pour un nouveau skill ou règle projet :
- Comparer avec les skills et règles projet existants
- Comparer avec les règles plugin (lecture seule) — si doublon exact avec une règle plugin, signaler : `"Cette règle existe déjà dans les règles plugin universelles — inutile de la dupliquer en projet."`
- Chevauchement > 50% avec une règle projet existante → proposer d'étendre l'existante

Afficher : `"Chevauchement détecté avec <nom>"` ou `"Aucun doublon"`

### Step 4 : Vérification granularité

**Règles projet :**
- Chaque fichier de règles < 200 lignes — sinon proposer de diviser
- Index (`.claude/skills/rules-references/SKILL.md`) synchronisé avec les fichiers réels dans `references/`
- Règles domaine-spécifiques dans `rules.md` transversal → proposer extraction dans `rules-<domaine>.md`

**Skills projet :**
- Un skill = une préoccupation — sinon proposer de diviser
- Chargement paresseux respecté (contenu lourd dans `references/`, pas inline)
- `SKILL.md` > 3000 mots → proposer extraction dans `references/`

### Step 5 : Plan

**Pour `add` :**
- Règle projet → `.claude/skills/rules-references/references/rules-<domaine>.md` + mise à jour index projet
- Skill projet local → `.claude/skills/<nom>/SKILL.md`
- Proposer : nom, emplacement, contenu (frontmatter complet : `name`, `description`, `argument-hint`, `allowed-tools`), impact tokens estimé

**Pour `optimize` :**
- Skills > 3000 mots : proposer extraction références
- Règles doublons avec les règles plugin : proposer suppression du doublon projet
- Index désynchronisé : lister entrées manquantes ou obsolètes
- Règles domaine-spécifiques dans `rules.md` : proposer extraction

**Pour `audit` :**
- Rapport complet des étapes 1 à 4
- Recommandations prioritaires

**Pour `import <path>` :**
- Valider `<path>` : doit exister — sinon `"Chemin introuvable : <path>"` et stopper
- Lire le skill source
- Exécuter les vérifications sécurité + doublons + granularité
- Proposer l'adaptation au contexte projet dans `.claude/`

### Step 6 : Attente approbation

Présenter le plan. `"Voulez-vous appliquer ces modifications ?"`
**Ne jamais auto-modifier** — attendre l'approbation explicite.

### Step 7 : Exécution

Après approbation :
- Créer ou modifier les fichiers dans `.claude/`
- Mettre à jour l'index des règles projet si nécessaire
- Afficher : `"Modifications appliquées : <résumé>"`

### Step 8 : Validation

Relancer la vérification granularité sur les fichiers modifiés.
