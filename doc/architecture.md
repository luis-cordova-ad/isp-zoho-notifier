# Architecture — isp-zoho-notifier

## Séquence principale

```mermaid
sequenceDiagram
    participant P as isp-provisioning-worker
    participant RMQ as RabbitMQ<br/>(provisioning.events)
    participant C as ProvisioningEventConsumer
    participant S as ZohoNotificationService
    participant T as ZohoTokenService
    participant DB as MariaDB (freeradius_db)<br/>isp_zoho_notifier_sent
    participant Z as Zoho CRM API

    P->>RMQ: publish ProvisioningEvent (status=SUCCESS)
    RMQ->>C: deliver message (manual ACK)
    C->>S: processEvent(event)

    S->>DB: existsByRequestIdAndStatus(SENT)?
    alt already sent (idempotence)
        DB-->>S: true
        S-->>C: skip
        C->>RMQ: basicAck
    else not sent
        DB-->>S: false
        S->>T: getAccessToken()
        T-->>S: cached token (or refresh)
        S->>Z: POST /crm/v2/Accounts/{id}/Notes
        alt 2xx success
            Z-->>S: note created (noteId)
            S->>DB: save SENT record
            S-->>C: success
            C->>RMQ: basicAck
        else 4xx client error
            Z-->>S: 4xx response
            S->>DB: save FAILED record
            S-->>C: throw ZohoClientException(4xx)
            C->>RMQ: basicAck + DLQ (pas de retry)
        else 5xx server error (retry 3x)
            Z-->>S: 5xx response
            S->>S: retry backoff 2s/10s/30s
            alt retry success
                Z-->>S: 2xx
                S->>DB: save SENT record
                C->>RMQ: basicAck
            else retry exhausted
                S->>DB: save FAILED record
                C->>RMQ: basicNack → DLQ
            end
        end
    end
```

## Composants

| Composant | Rôle |
|-----------|------|
| `ProvisioningEventConsumer` | Listener AMQP manual ACK, délègue au service |
| `ZohoNotificationService` | Logique métier : idempotence, retry, envoi Zoho |
| `ZohoTokenService` | Cache OAuth2 token (TTL = expires_in - 60s) |
| `ZohoNotificationSentRepository` | Persistance idempotence (JPA + MariaDB via ProxySQL) |
| `RetentionScheduler` | Purge des enregistrements > 90 jours |
| `WebhookController` | Optionnel — endpoint HTTP si `ZOHO_WEBHOOK_ENABLED=true` |
| `CorrelationIdFilter` | Propagation `X-Correlation-ID` dans MDC |

## Flux DLQ

```
provisioning.events queue
        │
        │  4xx Zoho → basicAck (message consommé, pas de retry)
        │  5xx après 3 retry → basicNack (false, false)
        ▼
zoho.notifier.dlq (Dead Letter Queue)
        │
        └── Monitoring Graylog + alerte Prometheus
```

## Idempotence

La table `isp_zoho_notifier_sent` garantit qu'un même `request_id` ne génère
qu'une seule note Zoho, même si le message RabbitMQ est livré plusieurs fois
(at-least-once delivery).

```sql
PRIMARY KEY (request_id)
-- Avant envoi : SELECT status FROM isp_zoho_notifier_sent WHERE request_id = ?
-- Si status = 'SENT' → ACK + skip (pas de double-notification)
```
