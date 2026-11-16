# Diccionario de datos

Generado con `scripts/docs/generar-diccionario.sh` a partir de `sql/01_esquema.sql`: no se edita a mano.
Tipos, nulos, claves, valores predeterminados y restricciones salen del catálogo de PostgreSQL; el significado,
de los `COMMENT` del script. Las reglas que abarcan varias columnas están en [modelo-de-datos.md](modelo-de-datos.md).

## Tipos enumerados

| Tipo | Valores | Significado |
|---|---|---|
| `rol_usuario` | administrador, recepcion, caja | Rol de quien usa el sistema: decide qué secciones ve y qué operaciones puede hacer. |
| `tipo_cobro` | primera, renovacion_anticipada, renovacion_vencida, reactivacion | Qué fue un cobro según el estado del socio en ese momento; decide dónde empieza la vigencia. |

## Tablas

### `configuracion`

Parámetros del gimnasio. Una sola fila.

| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |
|---|---|---|---|---|---|---|
| `id` | smallint | No | PK | 1 | ((id = 1)) | Identificador fijo de la única fila (siempre 1). |
| `nombre_gimnasio` | character varying(100) | No |  |  |  | Nombre que aparece en el acceso, el menú y los recibos. |
| `direccion` | character varying(200) | Sí |  |  |  | Dirección del gimnasio para el recibo. |
| `telefono` | character varying(30) | Sí |  |  |  | Teléfono del gimnasio para el recibo. |
| `logotipo` | bytea | Sí |  |  |  | Imagen PNG o JPG del logotipo (hasta 300 KB). |
| `leyenda_recibo` | character varying(160) | Sí |  |  |  | Texto al pie de los recibos. |
| `moneda` | character(3) | No |  | 'MXN'::bpchar |  | Código ISO 4217 de la moneda de todos los importes; no cambia si ya hay cobros. |
| `dias_aviso` | integer | No |  | 7 | (((dias_aviso >= 1) AND (dias_aviso <= 60))) | Días antes del vencimiento en que un socio pasa a «Por vencer». |
| `dias_baja_automatica` | integer | No |  | 30 | (((dias_baja_automatica >= 1) AND (dias_baja_automatica <= 365))) | Días después del vencimiento sin renovar en que un socio pasa a «Baja». |

### `folio_secuencia`

Contador de folios por serie. Solo existe la serie COBRO.

| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |
|---|---|---|---|---|---|---|
| `serie` | character varying(10) | No | PK |  |  | Nombre de la serie de folios. |
| `prefijo` | character varying(20) | No |  |  | (((prefijo)::text ~ '^[A-Z0-9-]{1,8}$'::text)) | Texto antes del número, por ejemplo GYM-. |
| `ultimo` | bigint | No |  | 0 |  | Último número entregado; el siguiente folio usa ultimo + 1. |
| `ancho` | smallint | No |  | 4 |  | Dígitos del número, rellenados con ceros a la izquierda. |

### `membresia`

Periodo pagado por un socio. Cada pago genera exactamente una.

| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |
|---|---|---|---|---|---|---|
| `id` | bigint | No | PK | identidad |  | Identificador generado por la base. |
| `socio_id` | bigint | No | FK → pago |  |  | Socio cubierto; debe ser el mismo que hizo el pago. |
| `plan_id` | bigint | No | FK → plan_membresia |  |  | Plan cobrado. |
| `pago_id` | bigint | No | FK → pago, UNIQUE |  |  | Pago que generó esta membresía. |
| `fecha_inicio` | date | No |  |  |  | Primer día cubierto. |
| `fecha_vencimiento` | date | No |  |  |  | fecha_inicio + duración del plan; el socio está cubierto mientras hoy <= esta fecha. |

### `metodo_pago`

Catálogo de formas de pago.

| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |
|---|---|---|---|---|---|---|
| `id` | bigint | No | PK | identidad |  | Identificador generado por la base. |
| `nombre` | character varying(40) | No | UNIQUE (sin mayúsculas) |  |  | Nombre del método; único sin distinguir mayúsculas. |
| `es_efectivo` | boolean | No |  | false |  | Si es efectivo se pide lo recibido y se calcula el cambio. |
| `activo` | boolean | No |  | true |  | Un método inactivo ya no se ofrece al cobrar. |

### `pago`

Cobro de una membresía.

| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |
|---|---|---|---|---|---|---|
| `id` | bigint | No | PK, UNIQUE | identidad |  | Identificador generado por la base. |
| `folio` | character varying(30) | No | UNIQUE |  |  | Folio impreso en el recibo, por ejemplo GYM-0001. |
| `socio_id` | bigint | No | FK → socio, UNIQUE |  |  | Socio que pagó. |
| `usuario_id` | bigint | No | FK → usuario |  |  | Usuario que registró el cobro. |
| `metodo_pago_id` | bigint | No | FK → metodo_pago |  |  | Forma de pago. |
| `monto` | numeric(12,2) | No |  |  | ((monto >= (0)::numeric)) | Precio del plan al momento del cobro. |
| `efectivo_recibido` | numeric(12,2) | Sí |  |  |  | Solo en efectivo: lo recibido; el cambio es efectivo_recibido - monto. |
| `fecha_hora` | timestamp with time zone | No |  | now() |  | Momento del cobro. |
| `tipo_cobro` | tipo_cobro | No |  |  |  | Primera, renovación anticipada, renovación vencida o reactivación. |
| `operacion_id` | character varying(64) | Sí | UNIQUE |  |  | Identificador de la operación; un segundo envío igual no cobra dos veces. |

### `plan_membresia`

Catálogo de planes de membresía.

| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |
|---|---|---|---|---|---|---|
| `id` | bigint | No | PK | identidad |  | Identificador generado por la base. |
| `nombre` | character varying(60) | No | UNIQUE (sin mayúsculas) |  |  | Nombre del plan; único sin distinguir mayúsculas. |
| `duracion_dias` | integer | No |  |  | (((duracion_dias >= 1) AND (duracion_dias <= 730))) | Días que cubre el plan (1 a 730). |
| `precio` | numeric(12,2) | No |  |  | ((precio > (0)::numeric)) | Precio actual; los cobros anteriores guardan el suyo. |
| `activo` | boolean | No |  | true |  | Un plan inactivo ya no se ofrece al cobrar. |
| `referencia_mensual_id` | bigint | Sí | FK → plan_membresia |  |  | Plan de 30 días contra el que se calcula el ahorro de este plan. |

### `socio`

Personas inscritas en el gimnasio. Su estado se calcula a partir de sus membresías.

| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |
|---|---|---|---|---|---|---|
| `id` | bigint | No | PK | identidad |  | Identificador generado por la base. |
| `nombre` | character varying(100) | No |  |  |  | Nombre completo del socio. |
| `telefono` | character(10) | No |  |  | ((telefono ~ '^[0-9]{10}$'::text)) | Teléfono a 10 dígitos, sin espacios. |
| `correo` | character varying(120) | Sí | UNIQUE (sin mayúsculas) |  |  | Correo opcional; único sin distinguir mayúsculas. |
| `fecha_alta` | date | No |  |  |  | Día en que se inscribió. |

### `usuario`

Personas que usan el sistema.

| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |
|---|---|---|---|---|---|---|
| `id` | bigint | No | PK | identidad |  | Identificador generado por la base. |
| `nombre` | character varying(100) | No |  |  |  | Nombre completo de la persona. |
| `nombre_usuario` | character varying(50) | No | UNIQUE (sin mayúsculas) |  |  | Nombre con el que entra; único sin distinguir mayúsculas. |
| `contrasena_hash` | character varying(255) | No |  |  |  | Hash Argon2id de la contraseña; la contraseña nunca se guarda. |
| `rol` | rol_usuario | No |  |  |  | Rol del usuario. |
| `activo` | boolean | No |  | true |  | Un usuario inactivo no puede entrar; sus cobros se conservan. |

