# Developer Guide — isp-zoho-notifier

## Obtenir les credentials Zoho OAuth2 en dev

### 1. Créer une application Zoho

1. Aller sur [Zoho API Console](https://api-console.zoho.com/)
2. Créer une application "Self Client" (server-to-server)
3. Copier `Client ID` et `Client Secret`

### 2. Obtenir un Refresh Token

```bash
# Générer l'URL d'autorisation
CLIENT_ID=your-client-id
REDIRECT_URI=http://localhost:8080/callback

echo "https://accounts.zoho.com/oauth/v2/auth?scope=ZohoCRM.modules.Notes.CREATE&client_id=${CLIENT_ID}&response_type=code&access_type=offline&redirect_uri=${REDIRECT_URI}"

# Ouvrir l'URL dans un navigateur, autoriser, copier le code de la redirection
CODE=the-authorization-code

# Échanger le code contre un refresh token
curl -X POST "https://accounts.zoho.com/oauth/v2/token" \
  -d "code=${CODE}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "redirect_uri=${REDIRECT_URI}" \
  -d "grant_type=authorization_code"

# Copier refresh_token dans .env
```

### 3. Configurer .env local

```bash
cp .env.example .env
# Remplir ZOHO_CLIENT_ID, ZOHO_CLIENT_SECRET, ZOHO_REFRESH_TOKEN
```

### 4. Démarrer les dépendances locales (Docker Compose)

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3.13-management

docker run -d --name mariadb \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=freeradius_db \
  -e MYSQL_USER=isp_zoho_notifier \
  -e MYSQL_PASSWORD=test-only \
  -p 3306:3306 \
  mariadb:11.4
```

### 5. Démarrer le service

```bash
task run
```

## Envoyer un message de test

```bash
# Via RabbitMQ Management UI (http://localhost:15672)
# Queue: provisioning.events
# Payload:
{
  "requestId": "test-uuid-1234",
  "action": "ACTIVATE",
  "status": "SUCCESS",
  "zohoAccountId": "your-zoho-account-id",
  "message": "Service activé avec succès",
  "correlationId": "corr-uuid-5678"
}
```

## Tester le webhook (si activé)

```bash
# Activer le webhook
export ZOHO_WEBHOOK_ENABLED=true
task run

# Envoyer une requête webhook (nécessite un token JWT valide)
curl -X POST http://localhost:8080/api/v1/notify/webhook \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "requestId": "webhook-test-1",
    "zohoAccountId": "your-account-id",
    "action": "MANUAL_NOTE",
    "message": "Note manuelle via webhook"
  }'
```
