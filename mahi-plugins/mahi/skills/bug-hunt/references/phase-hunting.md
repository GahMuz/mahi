# Phase : Hunting

**Objectif** : analyser le code des classes confirmées pour identifier les bugs.
Approche **Grep-first** (ne lire que les zones suspectes) + analyse 6 couches + trace
de scénario pour les bugs de logique et side effects. Le triage est intégré à la
découverte — pas de phase séparée.

---

## Étape 1 — Charger la référence méthodologique

Lire `references/hunting-patterns.md`.
La charger en contexte avant de commencer — elle contient les patterns Grep et
la grille 6 couches utilisés dans les étapes suivantes.

---

## Étape 2 — Sweep Grep (avant toute lecture de code)

Pour chaque fichier de classe confirmé dans le `scope` artifact, lancer **en parallèle**
les patterns Grep définis dans `hunting-patterns.md`.

Collecter pour chaque match : fichier, numéro de ligne, pattern déclenché, contexte
(2 lignes avant/après).

Ces résultats définissent les **zones suspectes** à lire en priorité.

---

## Étape 3 — Charger le code (ciblé)

**Priorité 1 — Docs module si disponibles :**
Pour chaque module des classes confirmées : si `.mahi/docs/modules/<module>/` existe
→ lire le doc module au lieu du code source brut (économie de tokens 80-90%).

**Priorité 2 — Lecture ciblée :**
Pour les classes sans doc : lire uniquement les méthodes / blocs signalés par le sweep
Grep. Utiliser `Read(file_path, offset, limit)` sur les plages de lignes concernées,
pas le fichier entier.

**Priorité 3 — Lecture complète :**
Si le sweep Grep n'a rien trouvé sur une classe core → lire quand même la classe
entière (bugs de logique non détectables par pattern).

Analyser les classes indépendantes **en parallèle** (dispatching via Agent si > 3 classes
pour protéger le contexte principal).

---

## Étape 4 — Analyse 4 couches structurelles

Pour chaque méthode / classe analysée, parcourir les couches 1-4 de `hunting-patterns.md` :

| Couche | Question centrale |
|--------|-------------------|
| **1 — Validation d'entrée** | Les paramètres sont-ils validés avant usage ? |
| **2 — Complétude CRUD** | Tous les cas du domaine sont-ils traités symétriquement ? |
| **3 — Cycle de vie ressources** | Transactions, rollback, lazy loading correctement gérés ? |
| **4 — Sécurité concurrente** | Accès partagé protégé, pas de lecture stale ? |

Pour chaque problème trouvé :
- Attribuer la sévérité immédiatement (CRITIQUE / MAJEUR / MINEUR — critères dans `hunting-patterns.md`)
- Générer un `debug-id` kebab-case : `<composant>-<probleme>`
  ex: `patrimoine-service-npe-adresse`, `webhook-handler-create-manquant`
- Éliminer les faux positifs évidents inline (voir critères dans `hunting-patterns.md`)
- Conserver les cas ambigus avec flag "À confirmer"

---

## Étape 5 — Trace de scénario (couches 5-6 : logique + side effects)

Les couches 5 et 6 ne sont pas détectables par Grep — elles nécessitent de suivre
le flux de bout en bout.

**5a — Identifier le scénario nominal :**
À partir du `scope` artifact : quel est le flux principal de la fonctionnalité ?
"Quand [acteur] fait [action], que se passe-t-il de bout en bout ?"

Nommer les étapes :
```
1. Entrée : [source — HTTP, cron, event, appel direct]
2. Validation : [qui valide quoi, où]
3. Traitement : [transformations, calculs, appels internes]
4. Persistance : [quoi est écrit où, dans quel ordre]
5. Réponse / événement : [retour HTTP, event publié, notification]
```

**5b — Tracer le flux en lisant le code :**
Suivre la call chain depuis le point d'entrée identifié dans le `scope`.
Pour chaque étape, répondre :
- **Précondition supposée** : quelles valeurs/états cette étape suppose-t-elle vrais ?
- **Postcondition produite** : que garantit-elle à sa sortie ?
- **Gap** : si la postcondition de l'étape N ne couvre pas la précondition de N+1 → finding (Couche 5)

**5c — Tester les scénarios alternatifs :**
- Entité non trouvée en base — comment est géré le `Optional.empty()` ou le `null` ?
- Opération rejouée (webhook dupliqué, retry) — résultat cohérent ou doublon créé ?
- Interruption au milieu — quel état reste incohérent si une exception est lancée à l'étape 3/5 ?
- Conditions aux limites — `>` vs `>=`, collection vide, premier vs dernier élément, dates limites ?

**5d — Cartographier les side effects :**
Pour chaque méthode mutante dans le flux :
- Lister toutes les entités modifiées (directement + via cascade JPA)
- Identifier les entités liées qui DEVRAIENT être mises à jour (FK, agrégats, tables de jointure)
- Vérifier que les événements domain sont publiés APRÈS le commit (pas avant)
- Vérifier l'ordre des appels externes par rapport à la transaction DB

Pour chaque item "attendu mais absent" ou "ordre incorrect" → finding (Couche 6).

---

## Étape 6 — Écrire l'artifact `findings`

```
mcp__plugin_mahi_mahi__write_artifact(flowId, "findings", <contenu>)
```

```markdown
# Findings : <titre>

## Résumé
N bugs identifiés : X critiques, Y majeurs, Z mineurs

## Scope analysé
- Classes lues : <liste>
- Docs module utilisées : <liste ou "aucune">

## BUG-001 — [CRITIQUE] <titre court>
- **Id debug** : `<composant-probleme>`
- **Fichier** : `<file>:<line>`
- **Code** :
  ```
  <extrait 1-3 lignes>
  ```
- **Problème** : <explication en une phrase>
- **Couche** : <Validation d'entrée | Complétude CRUD | Ressources | Concurrence | Logique métier | Side effects>
- **Conditions** : <quand ce bug se manifeste>
- **Impact** : <conséquence si non corrigé>
- **Classes à analyser pour le fix** : <liste ciblée>

## BUG-002 — [MAJEUR] ...

## Faux positifs écartés
- `<finding>` — raison : <pourquoi écarté>

## À confirmer avec le développeur
- `<finding>` — question : <ce qui est ambigu>
```

---

Dire au développeur : "Analyse terminée — N bugs trouvés (X critiques, Y majeurs, Z mineurs).
Tapez `/bug-hunt approve` pour valider et créer les sessions debug."
