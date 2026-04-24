---
name: process-debugging
description: Ce skill doit être utilisé quand des tests échouent après l'implémentation, un bug apparaît pendant l'exécution d'une spec, ou un comportement inattendu est rencontré. Injecté par spec-orchestrator lors du retry de sous-tâches `[!]`. Couvre l'investigation systématique de cause racine, le test d'hypothèses, et le seuil d'escalade vers spec-deep-dive.
allowed-tools: ["Read", "Glob", "Grep", "Bash"]
---

# Process Debugging

Approche systématique pour trouver et corriger les bugs pendant l'implémentation spec. Les fixes aléatoires font perdre du temps et masquent les causes racines.

**Règle fondamentale :** Trouver la cause racine avant toute tentative de fix. Fixer un symptôme est un échec.

## La règle de fer

Ne jamais proposer un fix sans avoir complété la Phase 1. Sans cause racine identifiée, c'est de la chance, pas du debug.

## Phase 1 — Investigation de la cause racine

Avant tout fix :

1. **Lire la sortie d'erreur complète** — stack trace, numéros de ligne, codes d'erreur. Ne pas passer les warnings en vitesse.
2. **Reproduire de façon constante** — peut-on le déclencher de façon fiable ? Sinon, collecter plus de données avant de faire des hypothèses.
3. **Vérifier les changements récents** — qu'est-ce qui a changé et pourrait causer ça ? Revoir le git diff et les modifications récentes.
4. **Tracer le flux de données** — pour les erreurs profondes dans la call chain, remonter en arrière : où est-ce que la mauvaise valeur est **créée** ? Continuer à remonter jusqu'à trouver la source.

## Phase 2 — Analyse par comparaison

1. Trouver des exemples fonctionnels dans le même codebase, similaires à ce qui est cassé.
2. Comparer fonctionnel vs. cassé — lister chaque différence, même minime.
3. Comprendre les dépendances — quelle config, quel environnement, quel état le code assume-t-il ?

## Phase 3 — Hypothèse et test

1. **Former une seule hypothèse** — énoncer clairement : "Je pense que X est la cause racine parce que Y."
2. **Faire le changement le plus petit possible** pour la tester — une variable à la fois.
3. Ça marche → Phase 4. Sinon → former une **nouvelle** hypothèse. Ne jamais empiler des fixes.

## Phase 4 — Fix et vérification

1. Corriger la cause racine, pas le symptôme.
2. Lancer le test ciblé — confirmer qu'il passe.
3. Lancer la suite de tests liés complète — confirmer qu'il n'y a pas de régression.
4. Si le fix ne fonctionne pas : retourner en Phase 1 avec les nouvelles informations.

## Seuil d'escalade

**Après 3 tentatives de fix échouées : s'arrêter.**

Ne pas tenter un 4ème fix. Marquer la sous-tâche `[!]` et reporter à l'orchestrateur avec :
- Ce qui a été tenté (chaque hypothèse et résultat)
- Ce qui est maintenant compris sur la cause racine
- Ce qui devrait changer pour le résoudre

Trois échecs indiquent généralement une hypothèse architecturale incorrecte, pas un bug simple. L'orchestrateur dispatche `spec-deep-dive` pour une investigation approfondie avant tout nouveau retry.

## Intégration Mahi

Quand ce skill est injecté par l'orchestrateur dans le prompt d'un retry `[!]` :

**Statut attendu en sortie :**
- `DONE` — bug corrigé, tous les tests passent, cause racine comprise
- `DONE_WITH_CONCERNS` — corrigé mais avec un doute sur la portée ou des effets secondaires potentiels
- `BLOCKED` — 3 tentatives épuisées, escalade requise — décrire les hypothèses testées et ce qui reste incompris

**Ne jamais déclarer DONE** sans avoir lancé la suite de tests complète.

**Si une violation SOLID est découverte** dans le code débugué (et non introduite par ce fix), la reporter comme `DONE_WITH_CONCERNS` — ne pas corriger silencieusement hors scope.

## Références

- **`references/root-cause-tracing.md`** — technique de trace arrière dans la call chain pour trouver l'origine d'une mauvaise valeur ; inclut la recherche de test polluter par bisection
- **`references/defense-in-depth.md`** — après avoir trouvé la cause racine, ajouter de la validation aux 4 couches (entry, business logic, environment guard, debug logging) pour rendre le bug structurellement impossible

## Anti-patterns

| Pattern | Pourquoi ça échoue |
|---------|-------------------|
| "Essayons X et on verra" | Supposer sans hypothèse — impossible d'apprendre du résultat |
| Plusieurs changements simultanés | Impossible d'isoler ce qui a fonctionné ou causé de nouvelles erreurs |
| Corriger le symptôme | La cause racine resurface ailleurs ou en production |
| Passer la Phase 1 sous pression | Le systématique est plus rapide que le tâtonnement — toujours |
| 4ème tentative de fix | À ce stade c'est architectural — escalader, pas persister |
