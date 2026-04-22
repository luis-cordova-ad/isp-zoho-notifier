# Deployment Guide — isp-zoho-notifier

## Prérequis

- kubectl configuré avec le bon cluster
- kustomize 5.7.1+
- Harbor accessible (`harbor.isp.local`)
- Secret K8s créé (voir ci-dessous)

## Créer le Secret K8s (une seule fois par cluster)

```bash
kubectl create secret generic isp-zoho-notifier-secret \
  --from-literal=ZOHO_CLIENT_ID='your-client-id' \
  --from-literal=ZOHO_CLIENT_SECRET='your-client-secret' \
  --from-literal=ZOHO_REFRESH_TOKEN='your-refresh-token' \
  --from-literal=RABBITMQ_USER='isp_zoho_notifier' \
  --from-literal=RABBITMQ_PASSWORD='your-rabbitmq-password' \
  --from-literal=DB_PASSWORD='your-db-password' \
  -n isp-app
```

## Appliquer les migrations Flyway

```bash
export DB_HOST=svc-data-persistence.isp-app.svc.cluster.local
export DB_PASSWORD=your-db-password
mvn flyway:migrate
```

## Déployer en DEV

```bash
task deploy-dev
# ou via GitHub Actions workflow_dispatch deploy-dev
```

## Déployer en PROD

Déploiement manuel uniquement via GitHub Actions :
1. Aller sur "Actions" > "Deploy PROD"
2. Saisir le `image_tag` (commit SHA ou tag spécifique)
3. Saisir `DEPLOY` comme confirmation

## Vérification post-déploiement

```bash
# Vérifier les pods
kubectl get pods -n isp-app -l app=isp-zoho-notifier

# Vérifier les logs
kubectl logs -n isp-app -l app=isp-zoho-notifier --tail=50

# Vérifier les métriques
kubectl port-forward -n isp-app svc/isp-zoho-notifier 8081:8081
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus | grep zoho
```
