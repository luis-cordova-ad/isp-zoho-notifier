-- grants-radius.sql — Droits minimaux pour isp_zoho_notifier sur freeradius_db
-- A exécuter sur le master MariaDB en tant que DBA avant le premier déploiement

CREATE USER IF NOT EXISTS 'isp_zoho_notifier'@'%' IDENTIFIED BY '${DB_PASSWORD}';

GRANT SELECT, INSERT ON freeradius_db.isp_zoho_notifier_sent TO 'isp_zoho_notifier'@'%';

-- Droit DELETE uniquement pour la tâche de rétention (purge 90j)
GRANT DELETE ON freeradius_db.isp_zoho_notifier_sent TO 'isp_zoho_notifier'@'%';

FLUSH PRIVILEGES;
