# Règles DORA — Impact développement

Ce fichier couvre deux dimensions :
- **DORA DevOps** (DevOps Research and Assessment) — pratiques de livraison performantes
- **DORA EU** (Règlement 2022/2554) — exigences de résilience pour entités financières et prestataires ICT critiques

---

## Partie 1 — DORA DevOps : pratiques de livraison

### Fréquence de déploiement

**Objectif elite :** Plusieurs déploiements par jour.

- [ ] Le design favorise des changements petits et indépendants — une spec = une fonctionnalité isolée
- [ ] Les feature flags découplent le déploiement de l'activation (deploy sans release)
- [ ] Les migrations de BDD sont additive-only en phase active : `ADD COLUMN` autorisé, `DROP`/`RENAME` en phase de transition uniquement
- [ ] Pas de dépendances entre specs qui forcent un déploiement groupé
- [ ] Les specs sont dimensionnées pour être livrées en moins d'une semaine

**Pattern feature flag :**
```java
if (featureFlags.isEnabled("new-payment-flow", userId)) {
    return newPaymentService.process(request);
}
return legacyPaymentService.process(request);
```

**Red flags :**
- Spec qui touche > 10 modules sans isolation
- `ALTER TABLE ... RENAME COLUMN` sans phase de transition à deux colonnes
- Deux features couplées ne pouvant pas être déployées séparément

---

### Lead time (délai idée → production)

**Objectif elite :** < 1 heure.

- [ ] Les tâches atomiques (TASK-xxx.y) sont indépendamment testables et déployables
- [ ] La suite de tests unitaires tourne en < 10 minutes
- [ ] Les tests unitaires ne démarrent pas de serveur et n'ouvrent pas de connexion BDD réelle
- [ ] Les tests d'intégration sont parallélisables (isolation par schéma ou conteneur)
- [ ] Les pipelines CI ont des étapes parallèles et un cache des dépendances

**Red flags :**
- Tâches de > 1 jour de travail
- Suite de tests > 30 minutes
- Tests unitaires qui nécessitent un serveur d'application

---

### Change Failure Rate (taux d'échec des changements)

**Objectif elite :** < 5%.

- [ ] Chaque déploiement est couvert par des tests automatisés (baseline-tests.json)
- [ ] Les breaking changes sont détectés avant le merge : tests de contrat, tests de non-régression
- [ ] Les migrations de BDD sont réversibles — chaque migration a un script `down`
- [ ] Les feature flags sont désactivables sans redéploiement
- [ ] Les changements à haut risque utilisent blue/green ou canary deploy

**Pattern migration réversible :**
```sql
-- V20260415__add_phone_verified.sql (up)
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN DEFAULT FALSE;

-- V20260415__add_phone_verified__down.sql (down)
ALTER TABLE users DROP COLUMN phone_verified;
```

**Red flags :**
- Migrations Liquibase/Flyway sans script de rollback
- Déploiement sans possibilité de retour arrière
- Pas de tests de non-régression sur les endpoints existants
- Changement d'API sans versioning (`/v1/` → `/v2/`)

---

### MTTR — Mean Time To Restore

**Objectif elite :** < 1 heure.

**Observabilité :**
- [ ] Health check endpoint implémenté et exposé : `GET /health` → `{ "status": "up", "checks": { ... } }`
- [ ] Liveness probe et readiness probe distincts pour les orchestrateurs (Kubernetes)
- [ ] Logs structurés en JSON avec champs standards à chaque log (voir pattern ci-dessous)
- [ ] Métriques exposées : `requests_total`, `request_duration_seconds`, `errors_total`, `db_pool_active`
- [ ] Alertes définies sur : taux d'erreur > seuil, latence > seuil, saturation mémoire/CPU

**Pattern logs structurés :**
```json
{
  "timestamp": "2026-04-15T10:30:00.000Z",
  "level": "ERROR",
  "service": "payment-service",
  "trace_id": "abc123def456",
  "span_id": "789xyz",
  "message": "Payment processing failed",
  "error_type": "InsufficientFundsException",
  "duration_ms": 245,
  "http_status": 422
}
```

**Résilience :**
- [ ] Circuit breaker sur toutes les dépendances externes (Resilience4j, Polly, Hystrix)
- [ ] Timeout explicite sur tous les appels réseau — jamais de timeout infini
- [ ] Retry avec exponential backoff + jitter sur les appels idempotents uniquement (pas les mutations)
- [ ] Fallback défini pour chaque dépendance critique : que retourner si elle est indisponible ?
- [ ] Graceful shutdown : le service draine ses requêtes en cours avant de s'arrêter (SIGTERM → drain → SIGKILL)
- [ ] Les runbooks de restauration sont référencés dans les ADR des composants critiques

**Pattern circuit breaker avec fallback :**
```java
@CircuitBreaker(name = "paymentGateway", fallbackMethod = "paymentFallback")
@TimeLimiter(name = "paymentGateway")
public CompletableFuture<PaymentResult> processPayment(PaymentRequest request) {
    return CompletableFuture.supplyAsync(() -> gateway.process(request));
}

public CompletableFuture<PaymentResult> paymentFallback(PaymentRequest req, Exception ex) {
    log.warn("Payment gateway unavailable, queuing", ex);
    return CompletableFuture.completedFuture(PaymentResult.queued(req.getId()));
}
```

**Pattern retry avec backoff :**
```java
Retry retry = Retry.of("externalApi", RetryConfig.custom()
    .maxAttempts(3)
    .waitDuration(Duration.ofMillis(500))
    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(500, 2.0, 0.5))
    .retryOnException(e -> e instanceof NetworkException)
    .build());
```

**Red flags :**
- Appel HTTP sans timeout : `restTemplate.getForObject(url, Type.class)` sans timeout configuré
- Pas de health check → l'orchestrateur ne sait pas si le service est prêt
- Logs texte libre non structuré : `System.out.println("erreur: " + e.getMessage())`
- Pas de fallback sur une dépendance externe critique
- Retry sans backoff (tempête de requêtes sur une dépendance déjà en surcharge)
- Retry sur des mutations (idempotence non garantie → double traitement)

---

## Partie 2 — DORA EU (Règlement 2022/2554)

Applicable aux **entités financières et prestataires ICT critiques**. Seules les exigences ayant un impact direct sur les choix de développement sont listées ici.

### Cartographie et criticité des composants (Art. 5-16)

- [ ] Les composants critiques sont identifiés dans le design avec leur niveau de criticité (P1/P2/P3)
- [ ] Les dépendances sur des tiers ICT (cloud, SaaS, APIs externes) sont listées dans le design
- [ ] Chaque dépendance critique a une stratégie de continuité dans le design : fallback, mode dégradé, cache local
- [ ] Pas de single point of failure sur un composant P1

**Red flags :**
- Design sans mention des dépendances externes
- Composant P1 sans mode dégradé ni stratégie de continuité

---

### Tests de résilience (Art. 24-27)

- [ ] Les tests de résilience (chaos, bascule, injection de pannes) sont planifiés dans le plan d'implémentation
- [ ] Les scénarios de panne des dépendances critiques sont couverts par des tests automatisés
- [ ] Les tests de résilience sont reproductibles, versionnés, exécutés en CI

**Pattern test de résilience :**
```java
@Test
void should_queue_payment_when_gateway_is_down() {
    gateway.simulateUnavailable();
    PaymentResult result = paymentService.process(validRequest());
    assertThat(result.getStatus()).isEqualTo(QUEUED);
    assertThat(result.getQueueId()).isNotNull();
}
```

---

### Classification des incidents (Art. 17-23)

- [ ] Le code implémente une classification des erreurs : P1 (service indisponible), P2 (dégradé), P3 (mineure)
- [ ] Les alertes sont configurées par niveau : P1 → notification immédiate, P2 → alerte équipe, P3 → ticket
- [ ] Les erreurs P1 et P2 sont loggées avec assez de contexte pour le diagnostic post-incident (trace_id, payload anonymisé, état des dépendances)

---

### Risque de concentration (Art. 28-44)

- [ ] Pas de dépendance unique sur un tiers pour un service P1 si une alternative existe
- [ ] Les SLAs des dépendances critiques sont référencés dans le design
- [ ] Une stratégie de sortie (circuit breaker + fallback local) est documentée pour chaque dépendance critique

---

## Checklist design (phase DES)

Pour chaque DES-xxx :
- [ ] Observabilité : health check, logs structurés JSON, métriques exposées définis
- [ ] Résilience : circuit breaker, timeout, retry avec backoff, fallback documentés
- [ ] Déploiement : changement réversible, feature flag si risque élevé, migration avec script down
- [ ] Dépendances : listées avec criticité (P1/P2/P3) et stratégie de continuité

## Checklist revue de code

- [ ] Tous les appels réseau ont un timeout explicite
- [ ] Circuit breaker configuré sur les dépendances externes
- [ ] Retry avec backoff + jitter sur les appels idempotents seulement
- [ ] Logs JSON structurés avec trace_id — pas de `System.out.println` ni log texte libre
- [ ] Health check endpoint présent et testé
- [ ] Migrations avec script down — aucune migration irréversible
- [ ] Feature flag désactivable sans redéploiement
- [ ] Graceful shutdown implémenté
- [ ] Pas de secrets hardcodés dans la logique de fallback/résilience
- [ ] Tests de résilience (injection de panne) présents pour les composants P1
