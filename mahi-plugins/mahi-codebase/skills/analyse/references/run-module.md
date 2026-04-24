# Analyser un module

Procédure pour RUN_MODULE.

## Step 1 : Résoudre le module

Utiliser la détection de SKILL.md pour trouver le chemin source du module.
Si introuvable → "Module '<nom>' introuvable. Vérifier le nom et relancer."

## Step 2 : Charger le contexte documentaire

Si `.mahi/docs/modules/<module>/module-<module>.md` existe :
- Lire ce fichier — il donne une vue synthétique sans scanner tout le code
- Passer son contenu aux agents comme `Contexte doc`

Sinon : passer `Contexte doc: Non disponible`.

## Step 3 : Charger les règles

Glob `**/mahi*/skills/rules/SKILL.md` → exécuter le protocole de chargement (plugin + projet + priorité).

Extraire spécifiquement :
- Règles SOLID + projet → pour l'agent qualité
- Règles RGPD (rules-rgpd.md) → pour l'agent conformité
- Règles DORA (rules-dora.md) → pour l'agent conformité

## Step 4 : Dispatcher les 3 agents en parallèle

Lancer simultanément :

**Agent qualité :**
```
Agent({
  description: "Analyse qualité — <module>",
  subagent_type: "analyse-quality",
  prompt: "
    Module : <nom>
    Chemin : <path>
    Répertoire de sortie : .mahi/analyses/<nom>/

    Contexte doc : <contenu module-<nom>.md ou 'Non disponible'>

    Règles qualité chargées :
    <règles SOLID + règles projet chargées à l'étape 3>

    Générer :
    1. quality.md
    2. candidates.md UNIQUEMENT si nouvelles règles identifiées

    Retourner : score (0-100) + last_commit hash.
  "
})
```

**Agent architecture :**
```
Agent({
  description: "Analyse architecture — <module>",
  subagent_type: "analyse-architecture",
  model: "opus",
  prompt: "
    Module : <nom>
    Chemin : <path>
    Répertoire de sortie : .mahi/analyses/<nom>/

    Contexte doc : <contenu module-<nom>.md ou 'Non disponible'>

    Générer :
    1. architecture.md

    Retourner : score (0-100).
  "
})
```

**Agent conformité :**
```
Agent({
  description: "Analyse conformité — <module>",
  subagent_type: "analyse-compliance",
  prompt: "
    Module : <nom>
    Chemin : <path>
    Répertoire de sortie : .mahi/analyses/<nom>/

    Contexte doc : <contenu module-<nom>.md ou 'Non disponible'>

    Règles RGPD :
    <contenu rules-rgpd.md>

    Règles DORA :
    <contenu rules-dora.md>

    Générer :
    - rgpd.md si données personnelles détectées
    - dora.md si contexte financier/résilience détecté

    Retourner : { rgpd: <score|null>, dora: <score|null> }.
  "
})
```

## Step 5 : Calculer le score global

Après réception des 3 résultats :

```
scores_applicables = [quality, architecture] + [rgpd si non null] + [dora si non null]
score_global = round(mean(scores_applicables))
```

## Step 6 : Écrire summary.md

Lire `references/templates.md` section "Template : summary.md".
Écrire `.mahi/analyses/<module>/summary.md`.

## Step 7 : Mettre à jour le manifest

Lire `.mahi/analyses/manifest.json` (ou initialiser si absent).
Mettre à jour `modules.<module>` :
- `analysedAt` : timestamp ISO courant
- `lastCommit` : hash retourné par l'agent qualité
- `scores` : { quality, architecture, rgpd, dora, global }

Écrire `.mahi/analyses/manifest.json`.

## Step 8 : Reporter

```
Analyse terminée : <module>

## Scores

| Dimension     | Score   |
|---------------|---------|
| Qualité       | XX/100  |
| Architecture  | XX/100  |
| RGPD          | XX/100  | (ou N/A)
| DORA          | XX/100  | (ou N/A)
| **Global**    | **XX/100** |

## Fichiers générés
- .mahi/analyses/<module>/summary.md
- .mahi/analyses/<module>/quality.md
- .mahi/analyses/<module>/architecture.md
- .mahi/analyses/<module>/rgpd.md         (si applicable)
- .mahi/analyses/<module>/dora.md         (si applicable)
- .mahi/analyses/<module>/candidates.md   (si nouvelles règles)
```
