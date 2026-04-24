# Templates d'analyse

Tous les templates de sortie pour le skill `/analyse`. Toute sortie en **français**.

---

## Template : summary.md

```markdown
# Analyse — <module>

> Analysé le : <ISO-8601>
> Commit : <hash>

## Scores

| Dimension     | Score    |
|---------------|----------|
| Qualité       | XX/100   |
| Architecture  | XX/100   |
| RGPD          | XX/100   |
| DORA          | N/A      |
| **Global**    | **XX/100** |

## Points clés

### Qualité
<1-2 lignes résumant les principaux problèmes ou "Aucun problème critique">

### Architecture
<1-2 lignes résumant les problèmes structurels ou "Structure satisfaisante">

### RGPD
<1-2 lignes ou "Non applicable">

### DORA
<1-2 lignes ou "Non applicable">

## Fichiers détaillés

- [quality.md](quality.md)
- [architecture.md](architecture.md)
- [rgpd.md](rgpd.md) *(si applicable)*
- [dora.md](dora.md) *(si applicable)*
- [candidates.md](candidates.md) *(si nouvelles règles identifiées)*
```

---

## Template : quality.md

```markdown
# Qualité — <module>

> Analysé le : <ISO-8601>
> Score : <N>/100

## Anti-patterns détectés

| Fichier:Ligne | Problème | Correction suggérée |
|---------------|----------|---------------------|
| src/Foo.php:42 | God class (15 méthodes publiques) | Extraire FooReader et FooWriter |

## Approches dépréciées

| Fichier:Ligne | API/Pattern | Remplacement |
|---------------|-------------|--------------|
| src/Bar.php:18 | md5() pour hash | bcrypt / argon2id |

## Violations de règles

| Fichier:Ligne | Règle | Sévérité | Correction |
|---------------|-------|----------|------------|
| src/Baz.php:55 | SRP — deux responsabilités | Critique | Séparer en deux classes |

## Déduction des points

| Problème | Occurrences | Points déduits |
|----------|-------------|----------------|
| Méthode trop longue | 3 | -6 |
| Catch vide | 1 | -3 |
| Total déduit | | -9 |

**Score final : <N>/100**
```

---

## Template : architecture.md

```markdown
# Architecture — <module>

> Analysé le : <ISO-8601>
> Score : <N>/100

## Cartographie

- Packages : <liste>
- Dépendances entrantes : <modules qui dépendent de ce module>
- Dépendances sortantes : <modules dont ce module dépend>

## Couplage

| Type | Détail | Impact |
|------|--------|--------|
| Circulaire | ModuleA ↔ ModuleB | -10 pts |
| Fan-out excessif | Foo dépend de 9 classes | -5 pts |

## Violations d'abstraction

| Fichier:Ligne | Problème | Suggestion |
|---------------|----------|------------|
| Controller.java:88 | Logique métier dans le contrôleur | Extraire dans un service |

## Améliorations suggérées

### Haute priorité
| # | Suggestion | Justification |
|---|------------|---------------|
| 1 | Extraire FooService | Classe Foo a 3 responsabilités distinctes |

### Moyenne priorité
| # | Suggestion | Justification |
|---|------------|---------------|

### Basse priorité
| # | Suggestion | Justification |
|---|------------|---------------|

**Score final : <N>/100**
```

---

## Template : rgpd.md

```markdown
# Conformité RGPD — <module>

> Analysé le : <ISO-8601>
> Score : <N>/100

## Données personnelles détectées

| Champ / Entité | Type DCP | Localisation |
|----------------|----------|--------------|
| User.email | DCP ordinaire | src/entity/User.java |

## Violations détectées

| Fichier:Ligne | Règle RGPD | Sévérité | Correction |
|---------------|------------|----------|------------|
| UserService.java:42 | DCP en clair dans les logs (email) | Critique | Anonymiser avant log |
| AuthController.java:18 | Endpoint sans contrôle d'accès | Critique | Ajouter @PreAuthorize |

## Déduction des points

| Violation | Occurrences | Points déduits |
|-----------|-------------|----------------|
| DCP dans logs | 2 | -20 |
| Total déduit | | -20 |

**Score final : <N>/100**
```

---

## Template : dora.md

```markdown
# Conformité DORA — <module>

> Analysé le : <ISO-8601>
> Score : <N>/100

## Contexte détecté

<Décrire le contexte qui a rendu DORA applicable : service de paiement, nombre de dépendances réseau, etc.>

## Violations détectées

| Fichier:Ligne | Règle DORA | Sévérité | Correction |
|---------------|------------|----------|------------|
| PaymentClient.java:33 | Appel HTTP sans timeout | Haute | Configurer connectTimeout + readTimeout |
| ExternalApiClient.java:55 | Pas de circuit breaker | Haute | Ajouter @CircuitBreaker (Resilience4j) |

## Points forts

- <Ce qui est déjà bien implémenté : circuit breaker présent, logs JSON, etc.>

## Déduction des points

| Violation | Occurrences | Points déduits |
|-----------|-------------|----------------|
| HTTP sans timeout | 2 | -16 |
| Pas de circuit breaker | 1 | -10 |
| Total déduit | | -26 |

**Score final : <N>/100**
```

---

## Template : candidates.md

```markdown
# Candidats aux nouvelles règles — <module>

> Analysé le : <ISO-8601>

Ces patterns récurrents ne sont couverts par aucune règle existante. Ils sont des candidats pour enrichir les règles projet.

| Pattern observé | Occurrences | Règle suggérée | Justification |
|-----------------|-------------|----------------|---------------|
| `@Transactional` sur des méthodes privées | 4 | Interdire @Transactional sur les méthodes privées — Spring l'ignore silencieusement | Spring proxying ne fonctionne pas sur les méthodes privées |
```
