# Génération de documentation module

Procédure pour les sous-commandes GENERATE_MODULE et GENERATE_ALL.

## Step 1 : Lire ou créer le manifest

- Lire `schemaVersion` dans `.mahi/config.json` — c'est la version de référence à stocker dans le manifest
- Lire `.mahi/docs/manifest.json` si existant
- Sinon créer un manifest vide : `{ "skill_version": "<schemaVersion>", "modules": {} }`
- Créer le répertoire `.mahi/docs/modules/` si nécessaire

## Step 2 : Identifier les cibles

### GENERATE_MODULE (un seul module)
- Utiliser le nom fourni en argument
- Vérifier que le module existe dans le codebase via la détection décrite dans SKILL.md
- Si non trouvé : "Module `<nom>` non trouvé. Modules détectés : <liste>"

### GENERATE_ALL
- Exécuter la détection de modules (voir SKILL.md)
- Lister tous les modules détectés avec leur chemin

## Step 3 : Détecter les features par module

Pour chaque module cible :
1. Scanner les sous-répertoires contenant des fichiers source
2. Un répertoire avec >= 2 fichiers source = une feature
3. Nommer la feature d'après le nom du répertoire (kebab-case)
4. Si aucune feature détectée : générer uniquement le doc module sans breakdown

## Step 4 : Vérification incrémentale

Pour chaque feature dans le manifest existant :
1. Lire `last_commit` depuis le manifest
2. Exécuter : `git log <last_commit>..HEAD -- <feature_path>`
3. Si aucun résultat ET `skill_version` correspond ET `generated_at` < 30 jours → **ignorer**
4. Reporter : "Feature `<nom>` : inchangée, ignorée."
5. Si `last_commit` introuvable dans git (rebase) → traiter comme obsolète

Pour les features absentes du manifest : toujours générer.

## Step 5 : Dispatch parallèle

### Mode --all : un agent par module

Pour chaque module, dispatcher en parallèle :
```
Agent({
  description: "Documenter le module <nom>",
  subagent_type: "doc-generator",
  model: <from config.models.doc-generator, default "haiku">,
  prompt: "
    Module : <nom>
    Chemin : <path>
    Features détectées : <liste des features avec chemins>
    Répertoire de sortie : .mahi/docs/modules/<nom>/
    
    Générer :
    1. module-<nom>.md (template module de references/templates.md)
    2. feature-<feature>.md pour chaque feature (template feature)
    
    Retourner : file_count, last_commit, liste features avec file_count par feature.
  "
})
```

### Mode module unique : évaluer la parallélisation

- Si > 5 features OU > 200 fichiers dans le module :
  - D'abord générer `module-<nom>.md` (résumé avec liste features)
  - Puis dispatcher un agent par feature en parallèle :
    ```
    Agent({
      description: "Documenter feature <feature> du module <nom>",
      subagent_type: "doc-generator",
      model: <from config.models.doc-generator, default "haiku">,
      prompt: "
        Feature : <feature>
        Module : <nom>
        Chemin : <feature_path>
        Répertoire de sortie : .mahi/docs/modules/<nom>/
        
        Générer : feature-<feature>.md (template feature)
        Retourner : file_count, last_commit.
      "
    })
    ```
- Sinon : dispatcher un seul agent pour tout le module (séquentiel interne)

## Step 6 : Mettre à jour le manifest

Après réception des résultats de chaque agent :
1. Mettre à jour `modules.<nom>` avec : `generated_at`, `skill_version`, `last_commit`, `file_count`
2. Mettre à jour chaque `features.<feature>` avec les mêmes métadonnées
3. Écrire le manifest mis à jour

## Step 7 : Générer/mettre à jour l'index

Écrire `.mahi/docs/index.md` en suivant le template index de `references/templates.md`.
Inclure tous les modules du manifest avec leur état de fraîcheur.

## Step 8 : Reporter

```
Documentation générée :
- Module(s) : <liste>
- Features : <count total>
- Fichiers documentés : <count total>
- Ignorées (inchangées) : <count>
- Répertoire : `.mahi/docs/`
- Tokens économisés : ~80-90% sur les tâches futures de ces modules.
```
