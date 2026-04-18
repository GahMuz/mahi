# Règles transversales (vérifiables)

Chaque règle est vérifiable par grep, glob ou revue de code.

## Règles non-négociables

- [ ] Pas de secrets en dur (mots de passe, clés API, tokens)
- [ ] Pas de console.log / var_dump / System.out.println oubliés
- [ ] Imports suivent les conventions du projet
- [ ] Gestion d'erreurs explicite (pas de catch vide)
- [ ] Pas de modification de fichiers générés automatiquement
- [ ] Texte UI dans la langue du projet
- [ ] Pas de dépendances ajoutées sans justification

## Portes de qualité

- [ ] Tests passent
- [ ] Linter passe
- [ ] Typecheck passe (si applicable)
- [ ] Revue de code approuvée par lot
- [ ] Pas de vulnérabilités de sécurité connues

## Contraintes d'architecture

- [ ] Placement des fichiers suit les conventions
- [ ] Séparation des couches respectée
- [ ] Appels API via la couche service

À personnaliser par l'équipe.