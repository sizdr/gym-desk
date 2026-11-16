#!/usr/bin/env bash
# Dibuja docs/diagrama-modelo-de-datos.svg a partir de la base real: levanta un PostgreSQL 16 temporal en
# Docker, aplica las migraciones de src/main/resources/db/migration en orden, lee el esquema con esquema.sql
# y lo dibuja con diagrama.py. Así el diagrama siempre corresponde con los scripts.
#
# Uso, desde la raíz del repositorio (necesita Docker y python3):
#   scripts/docs/generar-diagrama.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
NOMBRE="gym-desk-diagrama-$$"
CLAVE="diagrama-$$"
trap 'docker rm -f "$NOMBRE" >/dev/null 2>&1 || true' EXIT

docker run -d --rm --name "$NOMBRE" -e POSTGRES_DB=gimnasio -e POSTGRES_USER=gimnasio \
  -e POSTGRES_PASSWORD="$CLAVE" postgres:16 >/dev/null
until docker exec "$NOMBRE" pg_isready -U gimnasio -d gimnasio >/dev/null 2>&1; do sleep 0.5; done
sleep 1

for archivo in $(ls src/main/resources/db/migration/V*__*.sql | sort -V); do
  docker exec -i "$NOMBRE" psql -q -U gimnasio -d gimnasio -v ON_ERROR_STOP=1 < "$archivo"
done

docker exec -i "$NOMBRE" psql -q -At -U gimnasio -d gimnasio -v ON_ERROR_STOP=1 < scripts/docs/esquema.sql \
  | python3 -I scripts/docs/diagrama.py > docs/diagrama-modelo-de-datos.svg
echo "Listo: docs/diagrama-modelo-de-datos.svg"
