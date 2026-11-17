#!/usr/bin/env bash
# Crea la base de DEMOSTRACIÓN «gimnasio_demo» en el PostgreSQL de docker-compose.yml, junto a la base de
# trabajo «gimnasio», que no se toca.
#
#   1. Pide las cuentas para entrar: un administrador y, si quieres, una de Recepción y otra de Caja.
#      Las contraseñas se escriben al correr el script y no se guardan en ningún archivo: solo su hash
#      Argon2id, hecho con el mismo hasher de la app, llega a la base.
#   2. Crea la base y le aplica las migraciones con el mismo código que usa la app al arrancar.
#   3. Carga scripts/demo/datos-demo.sql: socios en todos los estados, planes, métodos y 25 cobros, con
#      fechas relativas a hoy en la zona horaria de este equipo.
#
# Uso, desde la raíz del repositorio y con la base levantada (docker compose up -d):
#   scripts/demo/crear-demo.sh              crea la base; si ya existe, se detiene sin tocarla
#   scripts/demo/crear-demo.sh --rehacer    la borra y la vuelve a crear (para repetir la demostración)
#
# Para usarla, en tu .env: DB_URL=jdbc:postgresql://localhost:5432/gimnasio_demo
# Para volver a tu base:   DB_URL=jdbc:postgresql://localhost:5432/gimnasio  (o borra la línea)
set -euo pipefail

cd "$(dirname "$0")/../.."
BASE="gimnasio_demo"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
REHACER=false
case "${1:-}" in
  "") ;;
  --rehacer) REHACER=true ;;
  *) echo "Uso: scripts/demo/crear-demo.sh [--rehacer]" >&2; exit 2 ;;
esac

# psql dentro del contenedor, como el usuario con el que docker compose creó PostgreSQL.
# Uso: psql_db BASE [opciones de psql…]; el SQL llega por la entrada estándar.
psql_db() {
  local base="$1"; shift
  docker compose exec -T db sh -c 'exec psql -U "$POSTGRES_USER" -v ON_ERROR_STOP=1 -q -d "$@"' psql "$base" "$@"
}

# Los comandos que no leen SQL van con </dev/null: si no, docker compose exec se come lo que se escriba después.
if ! docker compose exec -T db sh -c 'pg_isready -U "$POSTGRES_USER" -q' </dev/null 2>/dev/null; then
  echo "La base no está levantada. Corre primero: docker compose up -d" >&2
  exit 1
fi

EXISTE="$(docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname = '"'$BASE'"'"' </dev/null)"
if [[ "$EXISTE" == "1" && "$REHACER" != true ]]; then
  echo "La base $BASE ya existe. Para borrarla y crearla de nuevo: scripts/demo/crear-demo.sh --rehacer" >&2
  exit 1
fi

# ---------- Cuentas ----------

declare -a NOMBRES=() USUARIOS=() CLAVES=() ROLES=()

pedir_cuenta() {
  local rol="$1" titulo="$2" nombre usuario clave clave2
  echo
  echo "Cuenta de $titulo"
  read -rp "  Nombre completo: " nombre
  read -rp "  Usuario (3 a 20 minúsculas, números, punto o guion): " usuario
  read -rsp "  Contraseña (8 caracteres o más): " clave; echo
  read -rsp "  Repite la contraseña: " clave2; echo
  usuario="$(printf '%s' "$usuario" | tr '[:upper:]' '[:lower:]')"
  if [[ -z "${nombre// }" ]]; then echo "Escribe el nombre." >&2; exit 1; fi
  if [[ ! "$usuario" =~ ^[a-z0-9.-]{3,20}$ ]]; then
    echo "Usa de 3 a 20 letras minúsculas, números, punto o guion, sin espacios." >&2; exit 1
  fi
  for anterior in "${USUARIOS[@]}"; do
    if [[ "$anterior" == "$usuario" ]]; then echo "Ese usuario ya lo usaste en otra cuenta." >&2; exit 1; fi
  done
  if (( ${#clave} < 8 )); then echo "La contraseña debe tener al menos 8 caracteres." >&2; exit 1; fi
  if [[ "$clave" != "$clave2" ]]; then echo "Las contraseñas no coinciden." >&2; exit 1; fi
  NOMBRES+=("$nombre"); USUARIOS+=("$usuario"); CLAVES+=("$clave"); ROLES+=("$rol")
}

echo "Base de demostración: $BASE (tu base de trabajo no se toca)."
pedir_cuenta administrador "Administrador"
echo
read -rp "¿Crear también cuentas de Recepción y Caja para mostrar sus menús? [s/N] " OTRAS
if [[ "$OTRAS" =~ ^[sS] ]]; then
  pedir_cuenta recepcion "Recepción"
  pedir_cuenta caja "Caja"
fi

# ---------- Base ----------

echo
echo "Compilando…"
./mvnw -q compile
CP_FILE="$(mktemp)"
trap 'rm -f "$CP_FILE"' EXIT
./mvnw -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" >/dev/null
CP="target/classes:$(cat "$CP_FILE")"

declare -a HASHES=()
for i in "${!CLAVES[@]}"; do
  HASHES+=("$(CLAVE="${CLAVES[$i]}" "$JAVA" -cp "$CP" scripts/dev/HashPassword.java)")
done
CLAVES=()

if [[ "$EXISTE" == "1" ]]; then
  echo "Borrando la base $BASE anterior…"
  psql_db postgres <<< "DROP DATABASE $BASE WITH (FORCE);"
fi
echo "Creando $BASE y aplicando las migraciones…"
psql_db postgres <<< "CREATE DATABASE $BASE;"
DEMO_URL="jdbc:postgresql://localhost:5432/$BASE" "$JAVA" -cp "$CP" scripts/demo/MigrarDemo.java

for i in "${!USUARIOS[@]}"; do
  psql_db "$BASE" -v nombre="${NOMBRES[$i]}" -v usuario="${USUARIOS[$i]}" -v hash="${HASHES[$i]}" \
    -v rol="${ROLES[$i]}" <<'SQL'
INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol, activo)
VALUES (:'nombre', :'usuario', :'hash', CAST(:'rol' AS rol_usuario), true);
SQL
done

# ---------- Datos ----------

ZONA="$(timedatectl show -p Timezone --value 2>/dev/null || true)"
if [[ -z "$ZONA" && -L /etc/localtime ]]; then ZONA="$(readlink /etc/localtime | sed 's#.*/zoneinfo/##')"; fi
ZONA="${ZONA:-America/Mexico_City}"
echo "Cargando los datos de demostración (fechas de hoy en $ZONA)…"
docker compose exec -T -e PGTZ="$ZONA" db sh -c 'exec psql -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -q' \
  psql "$BASE" < scripts/demo/datos-demo.sql

echo
echo "Listo: $BASE tiene 23 socios, 8 planes y 25 cobros (3 de hoy)."
echo "Para usarla, pon en tu .env:   DB_URL=jdbc:postgresql://localhost:5432/$BASE"
echo "Para volver a tu base, cambia esa línea a .../gimnasio (o bórrala). Entra con: ${USUARIOS[*]}"
