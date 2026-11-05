#!/usr/bin/env bash
# ------------------------------------------------------------
# Copia il database "industriatech" dal MariaDB installato sul
# computer (porta 3306) al MariaDB del container (industriatech-mariadb).
#
# Il database locale viene solo LETTO, non modificato.
# Nel container le tabelle vengono ricreate (DROP + CREATE).
#
# Uso:  ./docker/scripts/importa-dati-mariadb-locale.sh
# ------------------------------------------------------------
set -euo pipefail

LOCAL_USER="${LOCAL_USER:-root}"
LOCAL_DB="industriatech"
CONTAINER="industriatech-mariadb"
DOCKER_USER="${MARIADB_USER:-industriatech}"
DOCKER_PASSWORD="${MARIADB_PASSWORD:-industriatech123}"

echo "1) Esporto $LOCAL_DB dal MariaDB locale..."
# --single-transaction: copia coerente senza bloccare le tabelle
mariadb-dump -u"$LOCAL_USER" --single-transaction "$LOCAL_DB" > /tmp/industriatech-dump.sql

echo "2) Importo nel container $CONTAINER..."
docker exec -i "$CONTAINER" mariadb -u"$DOCKER_USER" -p"$DOCKER_PASSWORD" "$LOCAL_DB" < /tmp/industriatech-dump.sql
rm /tmp/industriatech-dump.sql

echo "3) Verifica:"
docker exec "$CONTAINER" mariadb -u"$DOCKER_USER" -p"$DOCKER_PASSWORD" "$LOCAL_DB" \
  -e "SELECT 'dipendenti' AS tabella, COUNT(*) AS righe FROM dipendenti UNION ALL SELECT 'contratti', COUNT(*) FROM contratti;"
