# Phase : Transition

All output in French.

## Purpose

Clore l'ADR et orienter vers la suite : implémentation via spec, autre ADR dépendant, ou archivage pur.

## Process

### Step 1: Lire les prochaines étapes

Lire la section "Prochaines étapes" dans `.mahi/work/adr/YYYY/MM/<adr-id>/adr.md`.

### Step 2: Proposer la suite

Présenter les options selon les prochaines étapes identifiées :

**Si une implémentation est nécessaire :**
```
ADR-NNN finalisé ✓

Décision : <option choisie>

Prochaines étapes suggérées :
- `/spec new <titre>` — démarrer l'implémentation (l'ADR servira de contexte pour la phase design)
- `/adr new <titre>` — démarrer un ADR dépendant si une sous-décision est nécessaire
```

**Si aucune implémentation immédiate :**
```
ADR-NNN finalisé ✓

Décision archivée dans .mahi/work/adr/YYYY/MM/<adr-id>/adr.md
Consultable via `/adr open <titre>` dans toute future session.
```

### Step 3: Règles impactées

Lire la section "Règles impactées" dans `adr.md`.

Si des rules sont remises en cause :
```
⚠ Cet ADR remet en cause les règles suivantes :
- <rule> dans <fichier> — <résumé de l'impact>

Ces règles ont dû être mises à jour pendant la rétrospective.
Si ce n'est pas encore fait : `/evolve update` — modifier les rules directement.
```

### Step 4: Lien ADR → Spec

Si l'utilisateur lance `/spec new <titre>` après cet ADR, la phase requirements du spec doit charger `adr.md` comme contexte de référence. Rappeler :
"Lors du `/spec new`, mentionnez l'ADR-NNN dans le titre ou la description — le système chargera automatiquement la décision comme contexte pour la phase de design."

### Step 5: L'item actif est déjà libéré

La transition RETROSPECTIVE→DONE via `fire_event("COMPLETE")` et `update_registry(..., "completed")` ont déjà été effectuées dans SKILL.md APPROVE. Pas d'action supplémentaire sur l'état ici.

L'utilisateur peut maintenant lancer `/clear` pour purger le contexte de cette session.
