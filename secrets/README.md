# 🔐 Secrets — isp-zoho-notifier

Ce répertoire contient les **credentials sensibles** d'isp-zoho-notifier, **EN DEHORS de kustomize**.

## Structure

```
secrets/
  dev/
    isp-zoho-notifier-credentials.yaml      # Secrets DEV (Zoho tokens, DB passwords)
  prod/
    isp-zoho-notifier-credentials.yaml      # Secrets PROD (tokens, passwords)
  README.md
```

## Utilisation

### Environnement DEV

```bash
kubectl apply -f secrets/dev/isp-zoho-notifier-credentials.yaml
```

### Environnement PROD

```bash
kubectl apply -f secrets/prod/isp-zoho-notifier-credentials.yaml
```

## 🚫 JAMAIS en Git

- ✅ Fichier `.example` : OK de committer
- ❌ Fichier sans `.example` : NE JAMAIS committer (secrets réels)

```bash
# Exemple : créer depuis template
cp secrets/dev/isp-zoho-notifier-credentials.template.yaml \
   secrets/dev/isp-zoho-notifier-credentials.yaml
# Remplir les valeurs réelles
# JAMAIS committer le fichier sans template
```

## Format

Fichiers YAML K8s Secret (type: Opaque).

Exemple de structure :
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: isp-zoho-notifier-secret
  namespace: isp-app
type: Opaque
data:
  ZOHO_CLIENT_ID: <base64>
  ZOHO_CLIENT_SECRET: <base64>
  ZOHO_REFRESH_TOKEN: <base64>
  RABBITMQ_PASSWORD: <base64>
  DB_PASSWORD: <base64>
```

## Rotation des Secrets

Pour renouveler un secret (rotation) :
1. Mettre à jour `secrets/{env}/isp-zoho-notifier-credentials.yaml`
2. Appliquer : `kubectl apply -f secrets/{env}/isp-zoho-notifier-credentials.yaml`
3. Relancer les pods : `kubectl rollout restart deployment/isp-zoho-notifier -n isp-app`
