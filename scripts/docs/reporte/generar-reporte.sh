#!/usr/bin/env bash
# Genera docs/reporte/Reporte-Gym-Desk.pdf, el reporte técnico del proyecto final.
#
# Levanta un PostgreSQL 16 temporal en Docker (no toca la base de la app), carga los scripts del proyecto
# (sql/01, 02, 04 y 05) y deja que construir.py corra las consultas y las pruebas SQL, arme el HTML de
# docs/reporte/ con sus resultados y lo imprima con Chrome. Así cada resultado del reporte sale de la base.
#
# Las capturas y las evidencias de la app (docs/reporte/img/NN-*.png y docs/reporte/evidencias.md) salen de
# ReporteCapturasTest; para regenerarlas antes:
#   ./mvnw test -Dtest=ReporteCapturasTest -Dreporte.capturas=true
#   cp target/reporte/capturas/*.png docs/reporte/img/ && cp target/reporte/evidencias.md docs/reporte/
#
# Uso, desde la raíz del repositorio (necesita Docker, python3, Google Chrome o Chromium y pdftotext de poppler):
#   scripts/docs/reporte/generar-reporte.sh
set -euo pipefail

cd "$(dirname "$0")/../../.."
NOMBRE="gym-desk-reporte-$$"
CLAVE="reporte-$$"
trap 'docker rm -f "$NOMBRE" >/dev/null 2>&1 || true' EXIT

ZONA="$(timedatectl show -p Timezone --value 2>/dev/null || true)"
if [[ -z "$ZONA" && -L /etc/localtime ]]; then ZONA="$(readlink /etc/localtime | sed 's#.*/zoneinfo/##')"; fi
ZONA="${ZONA:-America/Mexico_City}"

docker run -d --rm --name "$NOMBRE" -e POSTGRES_DB=gimnasio -e POSTGRES_USER=gimnasio \
  -e POSTGRES_PASSWORD="$CLAVE" postgres:16 >/dev/null
until docker exec "$NOMBRE" pg_isready -U gimnasio -d gimnasio >/dev/null 2>&1; do sleep 0.5; done
sleep 1

for archivo in 01_esquema 02_datos 04_funciones 05_procedimientos; do
  docker exec -i -e PGTZ="$ZONA" "$NOMBRE" psql -q -U gimnasio -d gimnasio -v ON_ERROR_STOP=1 < "sql/$archivo.sql"
done

python3 -I scripts/docs/reporte/construir.py "$NOMBRE" "$ZONA" docs/reporte/Reporte-Gym-Desk.pdf
