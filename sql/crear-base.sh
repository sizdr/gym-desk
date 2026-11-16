#!/usr/bin/env bash
# Reconstruye la base del proyecto final con los scripts de esta carpeta, en orden, dentro del PostgreSQL de
# docker-compose.yml. Usa su propia base (gimnasio_proyecto por defecto): no toca la base de la aplicación.
#
# Uso, desde la raíz del repositorio y con la base levantada (docker compose up -d):
#   sql/crear-base.sh                 # crea gimnasio_proyecto desde cero y corre 01 a 06
#   sql/crear-base.sh otra_base       # con otro nombre
set -euo pipefail

cd "$(dirname "$0")/.."
BASE="${1:-gimnasio_proyecto}"
if [[ ! "$BASE" =~ ^[a-z_][a-z0-9_]*$ ]]; then
  echo "Nombre de base no válido: usa minúsculas, números y guion bajo." >&2; exit 1
fi
if [[ "$BASE" == "gimnasio" ]]; then
  echo "«gimnasio» es la base de la aplicación; elige otro nombre." >&2; exit 1
fi

docker compose exec -T db psql -q -U gimnasio -d gimnasio -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS $BASE" -c "CREATE DATABASE $BASE"

for archivo in 01_esquema 02_datos 03_consultas 04_funciones 05_procedimientos 06_pruebas; do
  echo "== $archivo.sql"
  docker compose exec -T db psql -q -U gimnasio -d "$BASE" -v ON_ERROR_STOP=1 -P pager=off < "sql/$archivo.sql"
done

echo "Listo: base «$BASE». Para consultarla: docker compose exec db psql -U gimnasio -d $BASE"
