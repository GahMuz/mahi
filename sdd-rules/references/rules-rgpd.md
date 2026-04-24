# Règles RGPD — Impact développement

Applicable quand les REQ ou le code manipulent des **données à caractère personnel** (DCP) :
nom, prénom, email, téléphone, adresse IP, identifiant technique lié à une personne, données de santé,
données financières, comportements utilisateur, géolocalisation, photos, voix, cookies d'identification.

---

## Minimisation des données

**Règle :** Ne collecter que les données strictement nécessaires à la finalité déclarée.

- [ ] Chaque champ collecté est justifié par une finalité explicite dans les REQ
- [ ] Pas de collecte "au cas où" ou "pour usage futur"
- [ ] Les champs optionnels sont clairement séparés des champs obligatoires
- [ ] Les identifiants techniques (UUID) sont préférés aux identifiants naturels (email, n° SS) comme clé de jointure
- [ ] Les APIs ne retournent que les champs nécessaires au client (pas de select *)

**Red flags :**
- Collecte d'email pour une feature qui n'en a pas besoin
- Champ `birth_date` stocké alors que seul l'âge est utilisé
- `SELECT * FROM users` retourné à un service qui n'a besoin que de l'ID

---

## Limitation des finalités

**Règle :** Les données collectées pour une finalité ne peuvent pas être réutilisées pour une autre sans base légale.

- [ ] La finalité de chaque donnée collectée est documentée dans les REQ
- [ ] Pas de réutilisation inter-features sans analyse de compatibilité
- [ ] Les traitements secondaires (analytics, ML, marketing) sont explicitement listés avec leur base légale
- [ ] Un service n'expose pas plus de DCP qu'il ne reçoit dans ses contrats d'interface

**Red flags :**
- Données d'authentification réutilisées pour du ciblage marketing
- Service B récupère le profil complet alors qu'il n'a besoin que de l'ID

---

## Durée de conservation

**Règle :** Pas de conservation au-delà de la durée nécessaire.

- [ ] Une durée de rétention est définie pour chaque catégorie de DCP
- [ ] Un mécanisme de suppression automatique est implémenté (job de purge, TTL) — pas juste documenté
- [ ] Durées de référence :
  - Logs applicatifs : 90 jours max (sauf obligation légale)
  - Données client actif : durée de la relation + obligation légale
  - Données de prospect sans interaction : 3 ans max
  - Données de transaction financière : 5 à 10 ans (obligation légale)
- [ ] Les backups respectent les mêmes TTL (purge automatique des snapshots)
- [ ] La suppression est effective dans toutes les couches : BDD, cache Redis, CDN, search index

**Red flags :**
- Pas de job de purge implémenté
- Données "supprimées" encore présentes en cache ou index Elasticsearch
- Backups sans TTL

---

## Droits des personnes

**Règle :** Droit d'accès, rectification, suppression, portabilité, opposition — doit être implémenté, pas seulement prévu.

- [ ] Endpoint d'export des données utilisateur prévu dans le design (`GET /users/{id}/data-export`)
- [ ] Flux de suppression de compte : suppression ou anonymisation dans **toutes** les tables concernées
- [ ] Les données exportées sont dans un format structuré et lisible (JSON, CSV)
- [ ] La suppression en cascade (FK) est gérée — les tables liées sont vidées ou anonymisées
- [ ] Le droit d'opposition est implémenté via un flag applicatif (`marketing_opt_out`, `profiling_opt_out`)
- [ ] La rectification est possible via les APIs exposées (pas uniquement via support)

**Red flags :**
- Suppression de compte = soft delete uniquement, données toujours exploitables
- Export uniquement via accès DBA
- Suppression de la table principale sans cascade sur les tables liées

---

## Sécurité des données

**Règle :** Protection contre tout accès non autorisé.

**Chiffrement :**
- [ ] Mots de passe : bcrypt (cost ≥ 12), argon2id, ou scrypt — jamais MD5, SHA1, SHA256 sans sel
- [ ] Données sensibles au repos chiffrées si critiques (santé, données bancaires) — AES-256 minimum
- [ ] HTTPS/TLS 1.2 minimum obligatoire pour tout transit de DCP
- [ ] Clés de chiffrement stockées séparément des données (KMS, Vault) — jamais hardcodées

**Contrôle d'accès :**
- [ ] Chaque endpoint exposant des DCP vérifie authentification ET autorisation (RBAC/ABAC)
- [ ] Principe du moindre privilège : un service n'accède qu'aux DCP dont il a besoin
- [ ] Isolation multi-tenant stricte — un utilisateur ne voit que ses propres données
- [ ] Tokens d'accès avec durée de vie courte (access token ≤ 1h)

**Logs :**
- [ ] Zéro DCP en clair dans les logs (email, nom, téléphone, IP si sensible)
- [ ] Tokens, clés API, mots de passe absents des logs et stack traces
- [ ] Les valeurs des paramètres SQL contenant des DCP ne sont pas loggées

**Red flags :**
- `log.info("Login attempt: {}", user.getEmail())`
- Hash MD5 ou SHA1 pour les mots de passe
- Endpoint sans vérification de rôle
- JWT sans expiration (`exp` manquant)
- Secret hardcodé dans le code source

---

## Données sensibles (catégories spéciales — Art. 9)

Traitement interdit sauf exception légale explicite :
**santé, biométrie, génétique, origine raciale/ethnique, opinions politiques/religieuses, orientation sexuelle, infractions pénales.**

- [ ] Ces données sont identifiées dans le design avec la base légale documentée
- [ ] Chiffrement renforcé au repos obligatoire (AES-256 minimum, clé dédiée)
- [ ] Accès restreint au minimum de composants/utilisateurs — audit trail obligatoire sur chaque accès
- [ ] Pseudonymisation appliquée dès que possible
- [ ] Jamais dans les URLs, query strings, ou headers non chiffrés

**Red flags :**
- Donnée de santé en texte clair dans la même table que des données ordinaires
- Champ `religion` ou `ethnie` collecté sans base légale documentée
- Log d'un diagnostic médical en clair

---

## Pseudonymisation & Anonymisation

- [ ] La pseudonymisation est utilisée quand la valeur directe n'est pas nécessaire (analytics → hash de l'IP, pas l'IP)
- [ ] L'anonymisation irréversible est appliquée pour les données archivées ou utilisées en reporting
- [ ] La re-identification est structurellement impossible (pas de table de correspondance accessible publiquement)
- [ ] Les données de test sont générées (Faker, factories) ou anonymisées — jamais extraites de la prod

**Patterns :**
```python
# Hash de l'IP pour les analytics (sel secret, rotation régulière)
ip_hash = hmac_sha256(secret_salt, ip_address)

# Anonymisation d'un email pour les logs
email_anon = email[:2] + "***@" + email.split("@")[1]
```

---

## Gestion du consentement

Applicable quand la base légale est le consentement (cookies non essentiels, marketing, profiling).

- [ ] Le consentement est enregistré avec : date, version de la politique, scope (quelles finalités)
- [ ] Le retrait du consentement est aussi simple que son octroi (un appel API)
- [ ] Le traitement s'arrête effectivement quand le consentement est retiré
- [ ] Les préférences sont stockées par finalité — pas un seul boolean `accepted_all`
- [ ] Pas de dark patterns : bouton "Refuser" aussi visible que "Accepter"

**Pattern de stockage :**
```json
{
  "user_id": "uuid",
  "consents": {
    "marketing_email": { "granted": true,  "at": "2026-01-15T10:00:00Z", "version": "1.2" },
    "analytics":       { "granted": false, "at": "2026-01-15T10:00:00Z", "version": "1.2" }
  }
}
```

---

## Transferts vers tiers

Applicable quand le code envoie des DCP à des services externes (APIs, SaaS, webhooks).

- [ ] Chaque transfert de DCP vers un tiers est documenté dans le design (destinataire, finalité, données envoyées)
- [ ] Les données envoyées sont minimisées — envoyer seulement ce dont le tiers a besoin
- [ ] Les transferts hors UE sont identifiés dans le design
- [ ] Les webhooks sortants ne transmettent pas plus de DCP que nécessaire

**Red flags :**
- Envoi du profil complet à un service d'emailing qui n'a besoin que de l'adresse
- API d'analytics qui reçoit l'IP réelle et le User-Agent sans pseudonymisation
- Aucune mention des tiers dans le design alors que des DCP leur sont transmises

---

## Environnements non-prod

- [ ] Jamais de données personnelles réelles en base de dev, test ou staging
- [ ] Les jeux de données de test sont générés (Faker, factories) ou anonymisés avant usage
- [ ] Les dumps de prod sont anonymisés par un outil de masquage avant diffusion
- [ ] Les logs de staging ne contiennent pas de DCP issues de la prod
- [ ] Les environnements non-prod ne sont pas accessibles publiquement

**Red flags :**
- `pg_dump prod | psql staging`
- Base de staging avec emails et noms réels
- Clés d'API de prod utilisées en staging

---

## Journalisation des accès aux DCP

- [ ] Les accès aux données sensibles sont tracés : qui, quoi, quand (surtout pour les rôles admin)
- [ ] Les logs d'accès sont séparés des logs applicatifs et conservés plus longtemps
- [ ] Les exports massifs de DCP déclenchent une alerte
- [ ] Les modifications de DCP critiques sont auditées (before/after dans un audit log)

---

## Violation de données — obligations dev (Art. 33-34)

- [ ] Les événements de sécurité critiques (brute-force, accès anormal) sont loggés et alertés
- [ ] Les accès inhabituellement larges à des DCP déclenchent une alerte automatique
- [ ] Les tokens/clés compromis peuvent être révoqués sans redéploiement
- [ ] Un mécanisme d'invalidation de session globale existe (logout all devices)

---

## Checklist design (phase DES)

Pour chaque DES-xxx qui manipule des DCP :
- [ ] Finalité documentée + base légale identifiée
- [ ] Catégorie : DCP ordinaire ou sensible (Art. 9) ?
- [ ] Durée de conservation définie + mécanisme de purge prévu
- [ ] Droits d'accès / suppression / export prévus dans les APIs
- [ ] Chiffrement déclaré pour les données sensibles
- [ ] Zéro DCP dans les logs — explicitement
- [ ] Transferts vers tiers documentés (destinataire + données transmises)
- [ ] Tests : données générées, jamais réelles

## Checklist revue de code

- [ ] Aucune DCP en clair dans les logs
- [ ] Hash des mots de passe : bcrypt ou argon2id — jamais MD5/SHA1
- [ ] Requêtes paramétrées — jamais de concaténation SQL avec des DCP
- [ ] Endpoints exposant des DCP : authentification + autorisation vérifiées
- [ ] Tokens avec expiration courte (≤ 1h pour les access tokens)
- [ ] Données de test générées, pas copiées de la prod
- [ ] Secrets et clés absents du code source
- [ ] Suppression en cascade correcte sur le droit à l'effacement
- [ ] Consentement vérifié avant traitement si base légale = consentement
- [ ] Données sensibles (Art. 9) avec chiffrement renforcé et audit trail
- [ ] Pseudonymisation appliquée dans les analytics et les logs
