# Operations Guide — isp-zoho-notifier

## Runbook : Token Zoho expiré

**Symptôme :** `zoho.notifier.token.refresh.count` élevé, notifications échouent avec 401.

**Diagnostic :**
```bash
kubectl logs -n isp-app -l app=isp-zoho-notifier | grep "token"
# Chercher : "Failed to obtain Zoho access token"
```

**Résolution :**
1. Vérifier que le secret `ZOHO_REFRESH_TOKEN` est valide :
```bash
curl -X POST "https://accounts.zoho.com/oauth/v2/token" \
  -d "grant_type=refresh_token" \
  -d "client_id=${ZOHO_CLIENT_ID}" \
  -d "client_secret=${ZOHO_CLIENT_SECRET}" \
  -d "refresh_token=${ZOHO_REFRESH_TOKEN}"
```
2. Si le refresh token est révoqué : regénérer via [Zoho API Console](https://api-console.zoho.com/)
3. Mettre à jour le Secret K8s :
```bash
kubectl patch secret isp-zoho-notifier-secret -n isp-app \
  -p '{"data":{"ZOHO_REFRESH_TOKEN":"<base64-encoded-new-token>"}}'
kubectl rollout restart deployment/isp-zoho-notifier -n isp-app
```

---

## Runbook : Zoho API down

**Symptôme :** Alertes `ZohoNotifierHighFailureRate`, DLQ qui grossit.

**Diagnostic :**
```bash
# Vérifier la connectivité depuis le pod
kubectl exec -n isp-app deployment/isp-zoho-notifier -- \
  curl -sv https://www.zohoapis.com/crm/v2/Accounts 2>&1 | head -20

# Vérifier le statut Zoho
# https://status.zoho.com/
```

**Résolution :**
- Si outage Zoho : attendre le rétablissement. Les messages en DLQ devront être réinjectés.
- Si NetworkPolicy bloquante : vérifier `allow-egress-zoho-internet` est bien appliqué.

```bash
kubectl get networkpolicy allow-egress-zoho-internet -n isp-app -o yaml
```

**Réinjecter les messages DLQ après rétablissement :**
```bash
# Via RabbitMQ Management UI
# Dead Letter Queue: zoho.notifier.dlq
# "Move messages" vers: provisioning.events
```

---

## Runbook : DLQ pleine

**Symptôme :** Alerte `ZohoNotifierHighDlqRate`, messages s'accumulent.

**Diagnostic :**
```bash
# Compter les messages en DLQ (via RabbitMQ Management)
curl -u guest:guest http://rabbitmq.isp-messaging.svc:15672/api/queues/%2F/zoho.notifier.dlq
```

**Actions :**

1. **Analyser les messages DLQ** : identifier la cause (4xx = données invalides, 5xx = Zoho down)

2. **Si 4xx (données invalides)** : purger les messages incorrects après investigation :
```bash
# Purger via Management API
curl -u guest:guest -X DELETE \
  "http://rabbitmq.isp-messaging.svc:15672/api/queues/%2F/zoho.notifier.dlq/contents"
```

3. **Si 5xx (Zoho était down)** : réinjecter après rétablissement Zoho.

---

## Runbook : Idempotence — double consommation

**Symptôme :** Messages RabbitMQ livrés deux fois (at-least-once).

**Comportement attendu :** La deuxième livraison est ignorée grâce à la table
`isp_zoho_notifier_sent`. Aucune double note créée sur Zoho.

**Vérification :**
```sql
SELECT * FROM isp_zoho_notifier_sent
WHERE request_id = 'the-request-id';
-- status = SENT → OK, second message ignoré
```

**Si des doublons sont constatés sur Zoho CRM :**
1. Vérifier que la table `isp_zoho_notifier_sent` est accessible (ProxySQL OK)
2. Vérifier que l'application n'a pas de race condition (plusieurs instances simultanées)
3. La clé primaire `PRIMARY KEY (request_id)` garantit l'unicité en base — un INSERT dupliqué lèverait une exception capturée.
