#!/usr/bin/env bash
# Crea un administrador en la base de DESARROLLO de docker-compose.yml, para poder entrar a la app.
#
# Las credenciales se escriben al correr el script y no se guardan en ningún archivo. La contraseña
# se convierte en un hash Argon2id con el mismo hasher de la app y solo el hash llega a la base.
# Si el usuario ya existe, se actualizan su nombre y contraseña, y queda activo como administrador.
#
# Uso, desde la raíz del repositorio y con la base levantada (docker compose up -d):
#   scripts/dev/crear-admin-dev.sh
#
# No es el alta del primer administrador de una instalación real: eso sigue pendiente en
# docs/contratos.md («Primer arranque»).
set -euo pipefail

cd "$(dirname "$0")/../.."
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

read -rp "Nombre completo: " NOMBRE
read -rp "Usuario (3 a 20 minúsculas, números, punto o guion): " USUARIO
read -rsp "Contraseña (8 caracteres o más): " CLAVE; echo
read -rsp "Repite la contraseña: " CLAVE2; echo

USUARIO="$(printf '%s' "$USUARIO" | tr '[:upper:]' '[:lower:]')"
if [[ -z "${NOMBRE// }" ]]; then echo "Escribe el nombre." >&2; exit 1; fi
if [[ ! "$USUARIO" =~ ^[a-z0-9.-]{3,20}$ ]]; then
  echo "Usa de 3 a 20 letras minúsculas, números, punto o guion, sin espacios." >&2; exit 1
fi
if (( ${#CLAVE} < 8 )); then echo "La contraseña debe tener al menos 8 caracteres." >&2; exit 1; fi
if [[ "$CLAVE" != "$CLAVE2" ]]; then echo "Las contraseñas no coinciden." >&2; exit 1; fi

echo "Compilando…"
./mvnw -q compile
CP_FILE="$(mktemp)"
trap 'rm -f "$CP_FILE"' EXIT
./mvnw -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" >/dev/null
HASH="$(CLAVE="$CLAVE" "$JAVA" -cp "target/classes:$(cat "$CP_FILE")" scripts/dev/HashPassword.java)"
unset CLAVE CLAVE2

docker compose exec -T db psql -U gimnasio -d gimnasio -v ON_ERROR_STOP=1 -q \
  -v nombre="$NOMBRE" -v usuario="$USUARIO" -v hash="$HASH" <<'SQL'
INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol, activo)
VALUES (:'nombre', :'usuario', :'hash', 'administrador', true)
ON CONFLICT (lower(nombre_usuario)) DO UPDATE
   SET nombre = EXCLUDED.nombre,
       contrasena_hash = EXCLUDED.contrasena_hash,
       rol = 'administrador',
       activo = true;
SQL

echo "Listo: «$USUARIO» puede entrar como administrador en la base de desarrollo."
