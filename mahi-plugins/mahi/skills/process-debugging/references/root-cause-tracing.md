# Trace de cause racine

Les bugs se manifestent souvent profondément dans la call stack. L'instinct est de corriger là où l'erreur apparaît — c'est un fix de symptôme. Remonter en arrière pour trouver le déclencheur original.

## Processus

### 1. Observer le symptôme
Noter exactement ce qui échoue : message d'erreur, fichier, ligne, valeur reçue vs. attendue.

### 2. Trouver la cause immédiate
Quel code produit directement cette erreur ? Le lire.

### 3. Demander : qui a appelé ça avec une mauvaise valeur ?
Remonter d'un niveau dans la call chain. Qu'est-ce qui a passé le mauvais argument ?

### 4. Continuer à remonter
Continuer vers le haut jusqu'à trouver où la mauvaise valeur est **créée**, pas juste transmise. C'est là la cause racine.

### 5. Corriger à la source
Corriger là où la mauvaise valeur est créée, pas là où elle cause l'erreur.

## Ajouter de l'instrumentation

Quand la trace n'est pas évidente, ajouter du logging avant l'opération qui échoue :

```
log("DEBUG: entering <operation>", {
  input_value,
  current_state,
  call_context
})
```

Lancer une fois pour capturer les preuves, puis analyser où la chaîne se brise.

## Trouver quel test cause de la pollution

Si un fichier ou une ressource apparaît de façon inattendue pendant les tests et qu'on ne sait pas quel test le crée :

1. Lancer les tests un par un (bisection)
2. Après chaque test, vérifier si la pollution existe
3. S'arrêter au premier test qui la crée

```bash
# Pseudo-code — adapter à votre test runner
for test in all_tests:
    if pollution_exists: skip (déjà pollué)
    run test
    if pollution_exists: POLLUEUR TROUVÉ → stop
```

## Principe clé

Ne jamais corriger là où l'erreur apparaît. Corriger là où la mauvaise valeur est créée.

Remonter 5 niveaux prend 10 minutes. Fixer le même bug 5 fois par symptôme prend des heures.

## Après avoir trouvé la cause racine

Appliquer `defense-in-depth.md` — ajouter de la validation à chaque couche que la mauvaise valeur traverse, pour rendre le bug structurellement impossible.
