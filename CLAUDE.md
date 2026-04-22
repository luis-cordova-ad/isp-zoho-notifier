# CLAUDE.md — isp-zoho-notifier

Type de projet : **k8s-spring-cloud-app**

## Skills à charger (dans cet ordre)

1. `k8s-project-core` — structure commune + guardrails K8s
2. `k8s-spring-cloud-app` — microservice Spring Cloud 2025.1.x / Spring Boot 4.0.x / Java 25
3. `taskfile-isp` — standard Taskfile plateforme ISP

> Les skills `spring-cloud-isp/skill-01` à `skill-15` sont disponibles comme référence
> détaillée dans `d:/VScode-workspace/AI-optimization/.claude/skills/spring-cloud-isp/`.
>
> | Skill | Pertinence pour ce repo |
> |-------|------------------------|
> | `skill-01` | Structure projet, `@RestController` slim, `ProblemDetail`, retry backoff Resilience4j |
> | `skill-02` | Security headers, CORS, vérification signature webhook Zoho, règle PII JWT |
> | `skill-06` | Tests JUnit : Test Data Builder, mocks API Zoho, `@ParameterizedTest` |
> | `skill-12` | Pipeline pré-PR 6 phases : compile → lint → build → Semgrep → E2E → diff |
> | `skill-14` | Conventions REST : codes HTTP, ProblemDetail, scopes Keycloak |

---

## Agents disponibles — quel agent pour quelle tâche

| Besoin | Agent à activer |
|--------|----------------|
| Coder un handler de webhook Zoho, mapping événement → notification ISP | `@isp-spring-developer` |
| Modifier le Dockerfile, les manifests Kustomize, le Taskfile, le pipeline CI/CD | `@isp-devops` |
| Vérifier la couverture JUnit (≥80%), valider les scénarios Cucumber BDD | `@isp-qa` |
| Documenter l'architecture, couplage Zoho ↔ ISP | `@isp-architect` |
| Conformité CRTC, transfert de données personnelles vers Zoho (hors Canada) | `@isp-crtc` |
| Revue de code Java avant PR (signature webhook non vérifiée, PII loggé, secrets Zoho) | `@isp-java-reviewer` |
| Audit OWASP — SSRF via URL webhook, injection dans les payloads Zoho | `@isp-security-reviewer` |
| `task compile` ou `task build` échoue — correction chirurgicale sans refacto | `@isp-build-resolver` |

> **Sécurité :** les webhooks entrants de Zoho doivent être vérifiés (signature HMAC ou token
> secret). `@isp-java-reviewer` doit valider ce point sur tout PR modifiant le handler webhook.
>
> **CRTC :** si des données personnelles d'abonnés sont transmises à Zoho (CRM hors Canada),
> la conformité au CRTC et à la LPRPDE doit être validée par `@isp-crtc`.

---

## Rôle du projet

`isp-zoho-notifier` est le **connecteur entre la plateforme ISP et Zoho CRM** :
- Reçoit les événements ISP (provisioning, facturation, incidents) et les pousse vers Zoho
- Reçoit les webhooks entrants de Zoho et les traduit en actions ISP
- Gère le retry avec backoff exponentiel sur les appels Zoho API
- Namespace : `isp-app`

---

## Stack technique

- Spring Boot 4.0.x / Spring Cloud 2025.1.x / Java 25
- Resilience4j retry + circuit-breaker pour l'API Zoho
- Keycloak JWT (`iam.korlu.com/realms/isp`) pour l'authentification interne
- Zoho OAuth2 pour l'authentification sortante vers Zoho API
- Déployé via Argo CD + Kustomize

---

## Statut

Repo en cours de création — utiliser le skill `k8s-spring-cloud-app` pour scaffolder la structure complète.

---

## Tests BDD obligatoires

| Feature | Obligatoire |
|---------|-------------|
| `security.feature` | 401 (token absent), 403 (scope insuffisant), 200 (token valide) |
| `webhook.feature` | Webhook Zoho valide traité, signature invalide rejetée (401) |
| `zoho-sync.feature` | Push événement vers Zoho, retry sur timeout Zoho |

---

## Architecture — dépendances

### Dépendances ENTRANTES
| Client | Via | Notes |
|--------|-----|-------|
| Services ISP internes | HTTP ClusterIP | Événements à pousser vers Zoho |
| Zoho CRM | Webhook HTTPS | Signature HMAC obligatoire |

### Dépendances SORTANTES
| Service | Protocole | Criticité |
|---------|-----------|-----------|
| Zoho CRM API | HTTPS externe | Métier — retry + circuit-breaker |
| `isp-iam-keycloak` | JWKS HTTPS | Critique |

---

## Validation avant commit

```bash
task lint && task semgrep:scan && task build
```

### Pipeline pré-PR complet (skill-12)

```
Phase 1 : task compile          → compilation rapide, 0 erreur
Phase 2 : task lint             → Checkstyle + kustomize build
Phase 3 : task build            → JaCoCo ≥70% lignes / ≥60% branches (objectif QA 80%/75%)
Phase 4 : task semgrep:scan     → 0 finding CRITICAL ou HIGH
Phase 5 : task e2e-test         → Cucumber : 401 / 403 / 200 / webhook / retry
Phase 6 : git diff              → vérifier secrets Zoho OAuth2, signature webhook validée
```
