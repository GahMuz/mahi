# Hunting Patterns — Référence méthodologique

Chargée en phase HUNTING. Contient les patterns Grep pour le sweep initial et
la grille d'analyse 6 couches pour identifier bugs structurels, logiques et side effects.

---

## Patterns Grep — Sweep initial

Exécuter ces patterns sur chaque fichier de classe confirmée (en parallèle) **avant
toute lecture de code**. Objectif : identifier les zones suspectes, pas les analyser.

| Pattern | Regex (ripgrep) | Bug potentiel |
|---------|-----------------|---------------|
| Catch vide | `catch\s*\(\w[\w\s,]*\)\s*\{\s*\}` | Erreur silencieuse — exception avalée |
| Catch avec log only | `catch[^}]*\blog\b[^}]*\}` sans rethrow ni throw | Erreur masquée — pas de propagation |
| Optional.get sans guard | `\.get\(\)` | NPE si Optional vide |
| TODO / FIXME / HACK | `(TODO\|FIXME\|HACK\|XXX)` | Bug connu non corrigé |
| Accès nullable en chaîne | `\w+\.\w+\.\w+` (3 niveaux de déréférencement) | NPE potentiel en chaîne |
| @Transactional manquant | méthodes `void \w+\(` + `save\|delete\|update\|persist` dans le corps | Opération mutante non transactionnelle |
| NullPointerException possible | `.get(0)` sans vérification de taille | IndexOutOfBoundsException |

**Utilisation** : collecter classe, numéro de ligne, pattern déclenché, 2 lignes de contexte.
Ces résultats priorisent les lectures ciblées à l'étape suivante.

---

## Grille d'analyse 6 couches

Pour chaque méthode / classe analysée. Couches 1-4 détectables à la lecture du code ;
couches 5-6 nécessitent la trace de scénario.

---

### Couche 1 — Validation d'entrée

*Question : les données en entrée sont-elles validées avant usage ?*

- Les paramètres nullable sont-ils null-checkés avant déréférencement ?
- Les `Optional` sont-ils vérifiés avant `.get()` (`isPresent()` ou `orElse...`) ?
- Les collections sont-elles vérifiées non-vides avant accès par index ?
- Les données externes (corps HTTP, résultats de requête DB, réponse d'API) sont-elles
  validées (format, plage, présence) ?
- Les `findById()` sont-ils suivis d'un `.orElseThrow()` ou d'un null-check explicite ?

---

### Couche 2 — Complétude CRUD

*Question : tous les cas du domaine sont-ils traités de façon symétrique ?*

- Si CREATE est géré, UPDATE et DELETE le sont-ils de façon cohérente ?
- Si le flux A→B existe (ex: sync vers système externe), le flux B→A est-il symétrique
  et complet (même logique de mapping, même gestion d'erreur) ?
- Tous les cas d'un `switch` / série de `if-else if` sont-ils couverts,
  y compris un `default` ou cas "état inconnu" ?
- Les entités liées (FK, associations JPA) pointent-elles vers la bonne entité ?
  (ex: emprunteur vs co-emprunteur, entité parente vs enfant)
- Si une collection d'éléments est traitée, les cas "collection vide" et
  "collection d'un seul élément" sont-ils corrects ?

---

### Couche 3 — Cycle de vie ressources

*Question : les ressources sont-elles correctement acquises, utilisées et libérées ?*

- Les méthodes qui modifient l'état persistent (DB, file, cache) sont-elles annotées
  `@Transactional` ou appelées dans une transaction active ?
- En cas d'erreur partielle (exception à mi-méthode), y a-t-il rollback ou compensation
  explicite ? Qu'est-ce qui reste en état incohérent ?
- Les collections JPA lazy (`@OneToMany` sans `EAGER`) sont-elles accédées dans une
  session Hibernate ouverte, ou risque-t-il une `LazyInitializationException` ?
- Les ressources externes (streams, connexions, fichiers) sont-elles fermées dans un
  `finally` ou `try-with-resources` ?
- Y a-t-il des `N+1` queries implicites (accès à une collection lazy dans une boucle) ?

---

### Couche 4 — Sécurité concurrente

*Question : l'état partagé est-il protégé contre les accès concurrents ?*

- Les champs partagés entre threads sont-ils protégés (`synchronized`, `volatile`,
  `AtomicReference`, `ConcurrentHashMap`, …) ?
- Y a-t-il des lectures stales possibles — une entité chargée en mémoire qui n'est pas
  rechargée après une modification externe ?
- Les opérations check-then-act sont-elles atomiques (vérifier existence PUIS insérer
  → race condition possible si non protégé) ?
- Y a-t-il un verrou optimiste (`@Version`) sur les entités modifiées concurremment,
  et est-il géré correctement en cas de conflit ?

---

### Couche 5 — Logique métier *(trace de scénario requise)*

*Question : le flux logique est-il correct pour tous les cas ?*

- Les préconditions de chaque étape sont-elles garanties par l'étape précédente ?
  (gap précondition/postcondition = bug de logique)
- Les conditions `if / switch` sont-elles dans le bon sens ?
  **Heuristique** : inverser mentalement la condition — si le code semble toujours
  plausible dans les deux sens, la condition est probablement incorrecte.
- Tous les statuts / états de l'entité sont-ils couverts ?
  (enum avec 5 valeurs mais seulement 3 traitées → 2 cas silencieux)
- L'opération est-elle idempotente si rejouée (webhook reçu deux fois, retry sur erreur) ?
- Les bornes sont-elles correctes ? (`>` vs `>=`, `<` vs `<=`, off-by-one sur index,
  inclusif vs exclusif sur les dates/plages)
- Les comparaisons de String utilisent-elles `.equals()` et non `==` ?

---

### Couche 6 — Side effects *(cartographie requise)*

*Question : tous les effets de bord attendus sont-ils présents et dans le bon ordre ?*

Pour chaque méthode mutante, dresser mentalement la liste :

| Side effect | Présent ? | Attendu ? |
|-------------|-----------|-----------|
| Entité principale sauvegardée | ? | ✓ |
| Entité liée (FK) mise à jour | ? | ? |
| Événement domain publié | ? | ? |
| Cache invalidé / mis à jour | ? | ? |
| Service externe appelé | ? | ? |
| Log d'audit écrit | ? | ? |

**Questions systématiques** :
- Y a-t-il des entités liées (agrégats, entités enfants, tables de jointure) qui
  devraient être mises à jour mais ne le sont pas ?
- Les événements domain (ApplicationEvent, message broker) sont-ils publiés APRÈS
  le commit DB, pas avant ? (publier avant = notification sans persistance garantie)
- Les appels à des services externes (REST, Kafka, SMTP) se font-ils dans le bon ordre
  par rapport à la transaction ? (appel avant commit → pas de rollback si l'appel échoue)
- Si la méthode lève une exception à l'étape N sur 5, quels side effects de 1..N-1
  sont déjà exécutés et non rollbackés ?
- Y a-t-il des side effects parasites — une méthode présentée comme "lecture"
  qui modifie silencieusement l'état ?

---

## Règles de sévérité

| Sévérité | Critères |
|----------|----------|
| **CRITIQUE** | Corruption ou perte de données ; exception non rattrapée en production ; incohérence d'état non rollbackée |
| **MAJEUR** | Comportement incorrect visible par l'utilisateur ; calcul erroné ; cas métier non géré silencieusement |
| **MINEUR** | Cas limite non couvert mais peu probable ; log manquant ; performance dégradée sans impact fonctionnel |

---

## Élimination des faux positifs

Un finding peut être écarté si :
- Le comportement est documenté comme intentionnel (commentaire `// intentional`, ADR, spec)
- La contrainte externe garantit que le cas ne peut pas se produire (schema DB, validation amont)
- Le code est dead code — vérifier via `Grep` sur le nom de méthode/classe avant d'écarter

Si ambigu → conserver avec flag "À confirmer avec le développeur". Ne pas décider seul.
