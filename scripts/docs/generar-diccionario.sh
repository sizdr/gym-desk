#!/usr/bin/env bash
# Escribe docs/diccionario-de-datos.md a partir de una base creada con sql/01_esquema.sql en un PostgreSQL 16
# temporal de Docker: tipos, nulos, claves, valores predeterminados y restricciones salen del catálogo, y el
# significado de cada atributo, de los COMMENT del script. Así el diccionario corresponde con el esquema.
#
# Uso, desde la raíz del repositorio (necesita Docker):
#   scripts/docs/generar-diccionario.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
NOMBRE="gym-desk-diccionario-$$"
trap 'docker rm -f "$NOMBRE" >/dev/null 2>&1 || true' EXIT

docker run -d --rm --name "$NOMBRE" -e POSTGRES_DB=gimnasio -e POSTGRES_USER=gimnasio \
  -e POSTGRES_PASSWORD="diccionario-$$" postgres:16 >/dev/null
until docker exec "$NOMBRE" pg_isready -U gimnasio -d gimnasio >/dev/null 2>&1; do sleep 0.5; done
sleep 1
docker exec -i "$NOMBRE" psql -q -U gimnasio -d gimnasio -v ON_ERROR_STOP=1 < sql/01_esquema.sql

{
  echo "# Diccionario de datos"
  echo
  echo "Generado con \`scripts/docs/generar-diccionario.sh\` a partir de \`sql/01_esquema.sql\`: no se edita a mano."
  echo "Tipos, nulos, claves, valores predeterminados y restricciones salen del catálogo de PostgreSQL; el significado,"
  echo "de los \`COMMENT\` del script. Las reglas que abarcan varias columnas están en [modelo-de-datos.md](modelo-de-datos.md)."
  echo
  echo "## Tipos enumerados"
  echo
  echo "| Tipo | Valores | Significado |"
  echo "|---|---|---|"
  docker exec -i "$NOMBRE" psql -q -At -U gimnasio -d gimnasio -c "
    SELECT '| \`' || t.typname || '\` | ' || string_agg(e.enumlabel, ', ' ORDER BY e.enumsortorder) || ' | '
           || COALESCE(obj_description(t.oid, 'pg_type'), '') || ' |'
    FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid
    WHERE t.typnamespace = 'public'::regnamespace GROUP BY t.oid, t.typname ORDER BY t.typname"
  echo
  echo "## Tablas"
  echo
  docker exec -i "$NOMBRE" psql -q -At -U gimnasio -d gimnasio -v ON_ERROR_STOP=1 < scripts/docs/diccionario.sql
} > docs/diccionario-de-datos.md
echo "Listo: docs/diccionario-de-datos.md"
