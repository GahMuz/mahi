---
name: sdd-rules
description: "Unified rules manager — handles loading of both plugin-level universal rules (SOLID, RGPD, DORA) and project-specific rules, with conflict priority resolution. Any agent delegates the full rules system by reading and executing the loading protocol below."
argument-hint: ""
allowed-tools: ["Read", "Glob"]
---

# Gestionnaire de règles sdd-spec

Gère le chargement et la priorité des règles pour tous les agents. Deux niveaux :
- **Règles plugin** : universelles, applicables à tout projet (ce répertoire)
- **Règles projet** : spécifiques au projet courant (`.claude/skills/rules-references/`)

**Priorité** : règles projet > règles plugin en cas de conflit.

## Index des règles plugin

| Fichier | Domaine | Charger quand |
|---------|---------|---------------|
| `references/rules-solid.md` | Architecture | Toujours — design et revue de code |
| `references/rules-rgpd.md` | Conformité RGPD | Si données personnelles dans les REQ ou le code |
| `references/rules-dora.md` | Conformité DORA | Si contexte financier, résilience ou métriques de livraison |

## Protocole de chargement (délégation depuis les agents)

Tout agent délègue le chargement des règles en lisant ce fichier et en exécutant les étapes ci-dessous.

### Étape 1 — Règles plugin (universelles)

- Glob `**/sdd-rules/references/rules-solid.md` → **toujours charger**
- Si données personnelles dans le contexte (nom, email, IP, identifiant, mot de passe, comportement, santé, finances...) → Glob `**/sdd-rules/references/rules-rgpd.md` et charger
- Si contexte financier ou exigences de résilience opérationnelle → Glob `**/sdd-rules/references/rules-dora.md` et charger

### Étape 2 — Règles projet

- Glob `.claude/skills/rules-references/SKILL.md`
- Si trouvé → lire l'index, charger les règles applicables au contexte courant
- Si non trouvé → continuer sans règles projet

### Étape 3 — Priorité et conflits

- Règles projet > règles plugin sur tout point en conflit
- Conflit trivial (contrainte additionnelle) : appliquer la règle projet, continuer
- Conflit non trivial (deux approches incompatibles) : documenter, remonter à l'utilisateur avant de continuer

## Extension

Pour ajouter une règle universelle :
1. Créer ou mettre à jour `references/rules-<domaine>.md`
2. Ajouter une ligne dans le tableau ci-dessus
3. Ajouter la condition de chargement dans l'Étape 1 du protocole
