# isp-zoho-notifier

Worker AMQP qui consomme `provisioning.events` depuis RabbitMQ et publie des notes automatiques
sur les comptes Zoho CRM correspondants.

## Architecture

```
RabbitMQ (provisioning.events)
        │
        ▼
ProvisioningEventConsumer
        │  status=SUCCESS only
        ▼
Idempotence check (isp_zoho_notifier_sent)
        │  not already sent
        ▼
ZohoNotificationService
        │  retry 3x backoff exponentiel (5xx/timeout)
        │  4xx → DLQ direct (pas de retry)
        ▼
Zoho CRM API (POST /crm/v2/Accounts/{id}/Notes)
        │
        ▼
Persist result + Graylog audit
```

## Démarrage local

```bash
cp .env.example .env
# Remplir ZOHO_CLIENT_ID, ZOHO_CLIENT_SECRET, ZOHO_REFRESH_TOKEN dans .env
task run
```

## Build et tests

```bash
task build      # compile + tests + JaCoCo
task test       # tests uniquement
task lint       # Checkstyle
task semgrep:scan
```

## Déploiement

```bash
task deploy-dev
task deploy-prod
```

## Secrets GitHub requis

| Secret | Description |
|--------|-------------|
| `HARBOR_USERNAME` | Login Harbor registry |
| `HARBOR_PASSWORD` | Mot de passe Harbor |
| `KUBECONFIG_DEV` | kubeconfig base64 cluster DEV |
| `KUBECONFIG_PROD` | kubeconfig base64 cluster PROD |

## K8s Secret requis (à créer manuellement)

```bash
kubectl create secret generic isp-zoho-notifier-secret \
  --from-literal=ZOHO_CLIENT_ID='...' \
  --from-literal=ZOHO_CLIENT_SECRET='...' \
  --from-literal=ZOHO_REFRESH_TOKEN='...' \
  --from-literal=RABBITMQ_USER='...' \
  --from-literal=RABBITMQ_PASSWORD='...' \
  --from-literal=DB_PASSWORD='...' \
  -n isp-app
```

## Métriques Prometheus

| Métrique | Description |
|----------|-------------|
| `zoho.notifier.events.received` | Événements reçus |
| `zoho.notifier.notifications.sent{action,status}` | Notifications envoyées |
| `zoho.notifier.notifications.failed{action,reason}` | Notifications échouées |
| `zoho.notifier.api.duration{action}` | Durée appels Zoho API |
| `zoho.notifier.dlq.count` | Événements envoyés en DLQ |
| `zoho.notifier.token.refresh.count` | Renouvellements token OAuth2 |
