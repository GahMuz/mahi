# Phase : Fixing

**Objectif :** Corriger la cause racine (identifiée en phase ANALYZING) en faisant
passer le test RED. Changement minimal, une tentative à la fois. Escalade après 3 échecs.

---

## Étape 1 : Charger les références

Lire (si pas déjà en contexte) :
- `**/mahi*/skills/process-debugging/references/defense-in-depth.md`
- Règles projet du domaine concerné (ex: `**/skills/rules-references/rules-controller.md` si endpoint)

---

## Étape 2 : Préparer le fix (changement minimal)

**Règle absolue : corriger la cause racine, pas le symptôme.**

Avant d'écrire quoi que ce soit :
1. Relire l'hypothèse dans `root-cause` : "La cause est X parce que Y"
2. Identifier le **plus petit changement** qui corrige X :
   - Ajouter une null-check ? Changer l'ordre d'un appel ? Corriger une condition ? Fixer une valeur par défaut ?
   - Si la correction semble nécessiter > 10 lignes → chercher pourquoi, c'est probablement un symptôme et non la cause
3. Ne pas refactorer autour du fix — faire le minimum

**Compteur de tentatives :** Initialiser à 1 pour cette phase. Incrémenter à chaque tentative échouée.

---

## Étape 3 : Appliquer le fix et tester

Appliquer le changement dans le worktree (`.worktrees/<debug-id>/`).

Exécuter le test RED créé en phase REPRODUCING :
```bash
cd .worktrees/<debug-id> && <commande de test unitaire ciblé>
```

### Si le test passe (GREEN) ✓
→ Continuer à l'étape 4.

### Si le test échoue encore ✗
Incrémenter le compteur de tentatives.

**Tentative 1 → 2 :**
- Annuler le changement (`git checkout <fichier>` dans le worktree)
- Documenter ce qui a été essayé et pourquoi ça n'a pas marché
- Revenir à `root-cause` : l'hypothèse est-elle correcte ? Ou faut-il regarder ailleurs ?
- Tester une nouvelle approche

**Tentative 2 → 3 :**
- Revenir sur la phase ANALYZING si nécessaire (recharger `root-cause`, challenger l'hypothèse)
- Appliquer une correction différente

**Tentative 3 échoue → ESCALADE :**
```
Agent({
  subagent_type: "mahi:spec-deep-dive",
  model: "opus",
  prompt: "Escalade debug : 3 tentatives de fix échouées pour le bug '<titre>'.
    Cause racine identifiée : <contenu root-cause>
    Tentative 1 : <description + pourquoi échoué>
    Tentative 2 : <description + pourquoi échoué>
    Tentative 3 : <description + pourquoi échoué>
    Test RED : <chemin et output actuel>
    Objectif : proposer une approche différente ou remettre en cause l'hypothèse de root-cause."
})
```
Attendre le rapport avant de continuer.

---

## Étape 4 : Defense-in-depth post-fix

Après que le test RED est GREEN, vérifier les 4 couches :

1. **Entry guard** : La donnée incorrecte qui causait le bug est-elle maintenant validée en entrée ? (null-check, format, range)
2. **Business logic** : La logique métier est-elle correcte pour tous les cas ? (cas nominal + cas limites)
3. **Environment guard** : Le fix dépend-il d'une configuration ou d'un état extérieur qui pourrait ne pas être présent en prod ?
4. **Debug logging** : Faut-il ajouter un log pour rendre ce type d'erreur plus visible à l'avenir ? (optionnel — uniquement si pertinent)

---

## Étape 5 : Écrire l'artifact `fix`

```
mcp__plugin_mahi_mahi__write_artifact(flowId, "fix", <contenu>)
```

````markdown
# Fix

## Changement appliqué
`<fichier>:<ligne>` — <description du changement en une phrase>

```diff
- <ligne avant>
+ <ligne après>
```

(Si le diff est plus long, inclure les lignes essentielles uniquement)

## Pourquoi ce changement corrige la cause racine
<explication directe du lien entre le fix et l'hypothèse de root-cause>

## Test RED → GREEN
`<chemin/TestFile.java>#<testMethod>` — PASSING ✓

## Defense-in-depth
- Entry guard : <OK / ajouté / non applicable>
- Business logic : <OK / cas limites vérifiés>
- Environment guard : <OK / non applicable>
- Logging : <ajouté / non nécessaire>

## Tentatives
- Tentative 1 : <description> → <résultat> (si applicable)
- Tentative 2 : <description> → <résultat> (si applicable)
````

---

## Étape 6 : Transition vers VALIDATING

Présenter le fix en français : "Test RED → GREEN ✓ — `<description du fix>`. Phase suivante : validation complète."

Taper `/debug approve` pour passer à la phase VALIDATING.
