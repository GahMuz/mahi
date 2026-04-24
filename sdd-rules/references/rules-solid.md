# Règles SOLID

Principes d'architecture orientée objet applicables à tout projet. Vérifiés en phase design (DES-xxx) et en revue de code.

---

## S — Responsabilité Unique (Single Responsibility)

**Règle :** Chaque classe, module ou composant a **une seule raison de changer**.

**Indicateurs de conformité :**
- Le nom du composant décrit exactement ce qu'il fait (sans "And", "Manager", "Helper", "Utils")
- On peut décrire sa responsabilité en une phrase sans utiliser "et"
- Ses tests couvrent une seule préoccupation fonctionnelle

**Red flags :**
- Classe > 200-300 lignes avec des méthodes sans lien entre elles
- Un service qui fait à la fois la logique métier, la validation, la persistance et l'envoi d'emails
- Un controller qui contient de la logique métier
- Noms : `UserManager`, `DataHelper`, `ApplicationService`

**Correction type :** Extraire la responsabilité secondaire dans un composant dédié.

---

## O — Ouvert/Fermé (Open/Closed)

**Règle :** Un composant est **ouvert à l'extension, fermé à la modification**. Ajouter un comportement ne doit pas nécessiter de modifier le code existant.

**Indicateurs de conformité :**
- Les nouveaux cas sont gérés via héritage, composition ou injection de dépendance
- Pas de `if/switch` sur un type ou une chaîne pour choisir un comportement variant
- Les points d'extension sont identifiés explicitement (interface, abstract class, strategy pattern)

**Red flags :**
- `switch (type) { case "A": ... case "B": ... }` pour dispatcher vers des comportements différents
- Modification d'une classe existante à chaque nouveau cas métier
- Enumération de types dans la logique applicative

**Correction type :** Introduire une interface et déplacer chaque cas dans une implémentation dédiée.

---

## L — Substitution de Liskov (Liskov Substitution)

**Règle :** Un sous-type doit être **utilisable partout où son type de base est attendu**, sans altérer le comportement attendu.

**Indicateurs de conformité :**
- Les sous-classes n'affaiblissent pas les préconditions ni ne renforcent les postconditions
- Aucune méthode héritée ne lève `UnsupportedOperationException` ou équivalent
- Les tests du type de base passent sans modification sur le sous-type

**Red flags :**
- Méthodes héritées qui lèvent une exception car "non applicables dans ce sous-type"
- Override qui retourne `null` là où le parent retourne un objet valide
- Sous-classe qui ignore silencieusement le contrat du parent

**Correction type :** Revoir la hiérarchie — favoriser la composition sur l'héritage si la substitution ne tient pas.

---

## I — Ségrégation des interfaces (Interface Segregation)

**Règle :** Un client ne doit pas dépendre de méthodes qu'il n'utilise pas. **Préférer plusieurs interfaces spécifiques à une interface générale.**

**Indicateurs de conformité :**
- Chaque interface couvre un contrat cohérent et minimal
- Les implémentations n'ont pas de méthodes vides ou stub
- Un client qui utilise l'interface n'importe que les méthodes dont il a besoin

**Red flags :**
- Interface avec > 5-7 méthodes couvrant plusieurs préoccupations
- Implémentations qui laissent des méthodes vides (`{}`) ou lèvent `NotImplementedException`
- Un repository qui expose à la fois des méthodes de lecture et d'écriture à des clients qui n'ont besoin que de l'un

**Correction type :** Diviser l'interface en deux interfaces spécialisées (ex: `IReader` et `IWriter`).

---

## D — Inversion des dépendances (Dependency Inversion)

**Règle :** Les modules de haut niveau ne doivent pas dépendre des modules de bas niveau. **Les deux doivent dépendre d'abstractions.** Les abstractions ne doivent pas dépendre des détails.

**Indicateurs de conformité :**
- Les dépendances sont injectées (constructeur, paramètre) plutôt qu'instanciées en dur (`new ConcreteClass()`)
- Les types déclarés sont des interfaces ou classes abstraites, pas des implémentations
- Les tests peuvent substituer des doubles de test sans modifier le code de production

**Red flags :**
- `new ServiceImpl()` ou `new Repository()` dans la logique métier
- Import direct d'une classe d'infrastructure dans un domaine métier
- Impossible de tester sans lancer la base de données ou un service externe

**Correction type :** Introduire une interface, injecter la dépendance via le constructeur, enregistrer l'implémentation dans le conteneur IoC.

---

## Application en phase design

Pour chaque DES-xxx, vérifier :
- [ ] S : responsabilité du composant décrite en une phrase sans "et"
- [ ] O : points d'extension identifiés si comportement variable attendu
- [ ] L : hiérarchies d'héritage justifiées, substitution honorée
- [ ] I : interfaces minimales, pas de méthodes imposées inutiles
- [ ] D : dépendances sur abstractions, injection déclarée
