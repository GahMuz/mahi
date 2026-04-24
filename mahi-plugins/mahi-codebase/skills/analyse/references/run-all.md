# Analyser tous les modules

Procédure pour RUN_ALL.

## Step 1 : Détecter tous les modules

Utiliser la détection décrite dans SKILL.md pour lister tous les modules du projet.

## Step 2 : Lire la configuration

Lire `.mahi/config.json` → `parallelTaskLimit` (0 = illimité).

## Step 3 : Dispatcher par vagues

Si `parallelTaskLimit == 0` ou `parallelTaskLimit >= nombre de modules` :
- Lancer tous les modules simultanément

Sinon :
- Découper en vagues de `parallelTaskLimit` modules
- Traiter chaque vague, attendre la fin avant la suivante

Pour chaque module, exécuter la procédure complète `references/run-module.md`.

## Step 4 : Mettre à jour le manifest

Le manifest est mis à jour par chaque exécution de run-module. Après la dernière vague, vérifier que `manifest.json` reflète tous les modules analysés.

## Step 5 : Reporter

```
Analyse complète — <N> modules

| Module     | Qualité | Architecture | RGPD | DORA | Global |
|------------|---------|--------------|------|------|--------|
| module-a   | 85      | 72           | 90   | N/A  | 82     |
| module-b   | 67      | 78           | N/A  | 65   | 70     |
| ...        | ...     | ...          | ...  | ...  | ...    |

Score global projet : XX/100 (moyenne des scores globaux)

Fichiers générés dans .mahi/analyses/
```
