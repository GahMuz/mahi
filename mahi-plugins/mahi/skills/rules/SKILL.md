---
name: rules
description: "Gestionnaire de règles unifié — règles plugin universelles (SOLID, RGPD, DORA) et règles projet. Protocole de chargement, détection de contexte et résolution de conflits. Tout agent délègue le chargement des règles en lisant et exécutant ce protocole."
allowed-tools: ["Read", "Glob"]
---

# Gestionnaire de règles Mahi

Deux niveaux :
- **Règles plugin** : universelles, voyagent avec le plugin (ce répertoire)
- **Règles projet** : spécifiques au projet courant (`.claude/skills/rules-references/`)

**Priorité** : règles projet > règles plugin en cas de conflit.

## Index des règles plugin

| Fichier | Domaine | Charger quand |
|---------|---------|---------------|
| `references/rules-solid.md` | Architecture SOLID | Toujours — design et revue de code |
| `references/rules-rgpd.md` | RGPD / données personnelles | Si DCP détectées dans le contexte |
| `references/rules-dora.md` | DORA / résilience | Si contexte financier ou exigences de résilience |

## Protocole de chargement

### Étape 1 — Détection du contexte

Analyser le contexte disponible (titre du spec, REQ, DES, description de la sous-tâche) pour détecter les domaines applicables.

**Données personnelles → RGPD** : présence de l'un des mots-clés suivants :
`nom`, `email`, `adresse`, `téléphone`, `IP`, `identifiant`, `mot de passe`, `password`, `santé`, `médical`,
`financier`, `comportement`, `géolocalisation`, `cookie`, `RGPD`, `GDPR`, `DCP`, `utilisateur`, `profil`,
`personnel`, `biométrie`, `consentement`

**Contexte financier/résilience → DORA** : présence de l'un des mots-clés suivants :
`paiement`, `payment`, `transaction`, `virement`, `banque`, `finance`, `assurance`, `crédit`, `débit`,
`résilience`, `SLA`, `incident`, `continuité`, `circuit breaker`, `fallback`, `DORA`, `conformité`, `audit`

### Étape 2 — Charger les règles plugin

Utiliser les chemins trouvés par Glob — le chemin exact varie selon la version du plugin installée.

1. `Glob **/mahi*/skills/rules/references/rules-solid.md` → **toujours lire**
2. Si RGPD détecté → `Glob **/mahi*/skills/rules/references/rules-rgpd.md` et lire
3. Si DORA détecté → `Glob **/mahi*/skills/rules/references/rules-dora.md` et lire

### Étape 3 — Charger les règles projet

1. `Glob .claude/skills/rules-references/SKILL.md`
2. Si trouvé → lire l'index, identifier les règles applicables au domaine de la sous-tâche, lire les fichiers correspondants
3. Si non trouvé → continuer sans règles projet

### Étape 4 — Résolution des conflits

- Règles projet > règles plugin sur tout point en conflit
- Conflit trivial (contrainte additionnelle) → appliquer la règle projet, continuer
- Conflit non trivial (deux approches incompatibles) → documenter, remonter à l'utilisateur avant de continuer

## Extension

Pour ajouter une règle universelle au plugin :
1. Créer `references/rules-<domaine>.md`
2. Ajouter une ligne dans le tableau ci-dessus
3. Ajouter les mots-clés de détection dans l'Étape 1
