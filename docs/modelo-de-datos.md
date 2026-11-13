# Modelo de datos

Base de datos PostgreSQL 16. La fuente del modelo es [`modelo-de-datos.dbml`](modelo-de-datos.dbml), que puede abrirse en [dbdiagram.io](https://dbdiagram.io) para ver el diagrama completo. Este documento explica las decisiones que el DBML no alcanza a mostrar.

## Esquema relacional

Notación: **negrita** = clave primaria; *cursiva* = clave foránea.

- CONFIGURACION (**id**, nombre_gimnasio, direccion, telefono, logotipo, leyenda_recibo, moneda, dias_aviso, dias_baja_automatica)
- FOLIO_SECUENCIA (**serie**, prefijo, ultimo, ancho)
- USUARIO (**id**, nombre, nombre_usuario, contrasena_hash, rol, activo)
- PLAN_MEMBRESIA (**id**, nombre, duracion_dias, precio, activo)
- METODO_PAGO (**id**, nombre, es_efectivo, activo)
- MOTIVO_MOVIMIENTO (**id**, nombre, es_sistema, activo)
- CATEGORIA_PRODUCTO (**id**, nombre, activo)
- SOCIO (**id**, nombre, telefono, correo, fecha_alta)
- PAGO (**id**, folio, *socio_id*, *usuario_id*, *metodo_pago_id*, monto, efectivo_recibido, fecha_hora)
- MEMBRESIA (**id**, *socio_id*, *plan_id*, *pago_id*, fecha_inicio, fecha_vencimiento)
- PRODUCTO (**id**, clave, nombre, *categoria_id*, precio, minimo, existencia, activo)
- VENTA (**id**, folio, fecha_hora, *metodo_pago_id*, total, efectivo_recibido, *usuario_id*)
- DETALLE_VENTA (**id**, *venta_id*, *producto_id*, cantidad, precio_unitario, importe)
- MOVIMIENTO_INVENTARIO (**id**, fecha_hora, *producto_id*, tipo, cantidad, existencia_resultante, *motivo_movimiento_id*, *detalle_venta_id*, *usuario_id*)

### Claves foráneas

| Relación | Atributos | Referencia |
|---|---|---|
| PAGO | socio_id | SOCIO(id) |
| PAGO | usuario_id | USUARIO(id) |
| PAGO | metodo_pago_id | METODO_PAGO(id) |
| MEMBRESIA | plan_id | PLAN_MEMBRESIA(id) |
| MEMBRESIA | (pago_id, socio_id) | PAGO(id, socio_id) |
| PRODUCTO | categoria_id | CATEGORIA_PRODUCTO(id) |
| VENTA | metodo_pago_id | METODO_PAGO(id) |
| VENTA | usuario_id | USUARIO(id) |
| DETALLE_VENTA | venta_id | VENTA(id) |
| DETALLE_VENTA | producto_id | PRODUCTO(id) |
| MOVIMIENTO_INVENTARIO | producto_id | PRODUCTO(id) |
| MOVIMIENTO_INVENTARIO | motivo_movimiento_id | MOTIVO_MOVIMIENTO(id) |
| MOVIMIENTO_INVENTARIO | detalle_venta_id | DETALLE_VENTA(id) |
| MOVIMIENTO_INVENTARIO | usuario_id | USUARIO(id) |

`MEMBRESIA.socio_id` no apunta directo a SOCIO: llega por la clave compuesta hacia PAGO, que obliga a que la membresía sea del mismo socio que pagó. Ninguna clave foránea borra en cascada, porque los registros referenciados nunca se borran.

### Claves candidatas

| Relación | Clave alterna |
|---|---|
| USUARIO | lower(nombre_usuario) |
| PLAN_MEMBRESIA, METODO_PAGO, MOTIVO_MOVIMIENTO, CATEGORIA_PRODUCTO | nombre |
| SOCIO | lower(correo), solo entre los que tienen correo |
| PAGO | folio |
| MEMBRESIA | pago_id |
| PRODUCTO | lower(clave) |
| VENTA | folio |
| DETALLE_VENTA | (venta_id, producto_id) |

PAGO también declara `(id, socio_id)` como único, aunque `id` ya lo es, solo para que MEMBRESIA pueda referenciar el par.

### Restricciones de integridad

| Tipo | Restricción |
|---|---|
| Dominio | `telefono` de SOCIO: 10 dígitos; `duracion_dias` de 1 a 730; `precio` de PLAN_MEMBRESIA `> 0`; los demás precios, montos y totales `>= 0`; `minimo >= 0`; `cantidad > 0` en DETALLE_VENTA; `dias_aviso` de 1 a 60; `dias_baja_automatica` de 1 a 365; `prefijo` de FOLIO_SECUENCIA de 1 a 8 mayúsculas, números o guiones |
| Tupla | `fecha_vencimiento > fecha_inicio`; `efectivo_recibido >= monto` (PAGO) y `>= total` (VENTA); en MOVIMIENTO_INVENTARIO, `cantidad <> 0`, `entrada` con cantidad positiva y `salida` con negativa, y al menos uno de `motivo_movimiento_id` o `detalle_venta_id` |
| Relación | CONFIGURACION tiene una sola tupla (`id = 1`) |
| Operación | PAGO, MEMBRESIA, VENTA, DETALLE_VENTA y MOVIMIENTO_INVENTARIO no admiten `DELETE` (trigger) |

### Normalización

Las relaciones están en tercera forma normal: cada atributo no clave depende solo de la clave completa. Las excepciones son atributos derivados que se guardan a propósito:

| Atributo | Se deriva de | Por qué se guarda |
|---|---|---|
| PRODUCTO.existencia | la suma de sus movimientos | consultar la existencia sin recorrer el historial en cada venta |
| MOVIMIENTO_INVENTARIO.existencia_resultante | la existencia antes del movimiento más su cantidad | mostrar el historial y detectar si `existencia` se desalineó |
| VENTA.total | la suma de los importes de su detalle | el total se cobra y se imprime; guardarlo evita recalcularlo |
| DETALLE_VENTA.importe | cantidad × precio_unitario | columna generada por la base: no puede quedar inconsistente |

`PAGO.monto` y `DETALLE_VENTA.precio_unitario` no son redundancia: guardan el precio vigente en el momento de la operación, que puede cambiar después en el catálogo.

## Diagrama

![Diagrama del modelo de datos](diagrama-modelo-de-datos.svg)

`configuracion` y `folio_secuencia` no se relacionan con otras tablas: las consultan los procesos de cobro y venta.

## Convenciones

- **Nombres** en español, en singular y en `snake_case`.
- **Claves primarias** `bigint` generadas por la base (`GENERATED ALWAYS AS IDENTITY`).
- **Importes** en `numeric(12,2)`, nunca en `float`. La moneda es una sola para todo el sistema y vive en `configuracion`.
- **Fechas**: `date` para vigencias y altas; `timestamptz` para cobros, ventas y movimientos.
- **Catálogos** (`plan_membresia`, `metodo_pago`, `motivo_movimiento`, `categoria_producto`, `producto`, `usuario`) se desactivan con `activo = false` para conservar el historial; solo se borra lo que nunca se usó (un plan sin membresías ni planes que lo tomen como referencia, un método de pago sin cobros ni ventas).
- **Registros de operación** (`pago`, `membresia`, `venta`, `detalle_venta`, `movimiento_inventario`) nunca se borran; un trigger lo impide.
- **Unicidad sin mayúsculas** en `usuario.nombre_usuario`, `socio.correo` y `producto.clave`: se indexa `lower(columna)`.

## Lo que no se guarda

El **estado del socio** (Sin membresía, Vigente, Por vencer, Vencida, Baja) y sus **bajas** se calculan a partir de sus membresías, de `dias_aviso` y de `dias_baja_automatica`. Guardarlos obligaría a mantenerlos al día con un proceso programado, y cambiar `dias_baja_automatica` no recalcularía el pasado.

## Tipos enumerados

| Tipo | Valores |
|---|---|
| `rol_usuario` | `administrador`, `recepcion`, `caja` |
| `tipo_movimiento` | `entrada`, `salida`, `ajuste` |

## Configuración

### `configuracion`

Una sola fila (`CHECK (id = 1)`) con los parámetros del gimnasio.

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `smallint` | PK, siempre 1 |
| `nombre_gimnasio` | `varchar(100)` | obligatorio |
| `direccion` | `varchar(200)` | |
| `telefono` | `varchar(30)` | |
| `logotipo` | `bytea` | se imprime en recibos y tickets |
| `leyenda_recibo` | `varchar(160)` | pie de recibos y tickets |
| `moneda` | `char(3)` | obligatorio, `MXN` por defecto; no se cambia si ya hay cobros o ventas |
| `dias_aviso` | `integer` | obligatorio, 7 por defecto, de 1 a 60 |
| `dias_baja_automatica` | `integer` | obligatorio, 30 por defecto, de 1 a 365; cambiarlo recalcula las bajas anteriores |

### `folio_secuencia`

Un contador por serie de folios.

| Columna | Tipo | Reglas |
|---|---|---|
| `serie` | `varchar(10)` | PK: `COBRO` o `VENTA` |
| `prefijo` | `varchar(20)` | obligatorio, de 1 a 8 letras mayúsculas, números o guiones, por ejemplo `GYM-` |
| `ultimo` | `bigint` | último número entregado, 0 al inicio |
| `ancho` | `smallint` | dígitos con ceros a la izquierda, 4 por defecto; un número más largo se escribe completo, sin cortarse |

El siguiente folio se toma con `UPDATE folio_secuencia SET ultimo = ultimo + 1 WHERE serie = ... RETURNING ...`, dentro de la misma transacción del cobro o la venta (función `siguiente_folio`). Así dos cajas nunca reciben el mismo número. Nunca se calcula con `MAX(folio) + 1`.

Para usar una sola numeración en cobros y ventas, ambos procesos piden la misma serie. En la fase 1 solo existe la serie `COBRO`; su prefijo es el que se edita en Configuración.

## Usuarios

### `usuario`

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `bigint` | PK |
| `nombre` | `varchar(100)` | obligatorio |
| `nombre_usuario` | `varchar(50)` | obligatorio, único y buscado sin distinguir mayúsculas; con él se entra, no con el correo |
| `contrasena_hash` | `varchar(255)` | obligatorio; hash Argon2id, nunca la contraseña |
| `rol` | `rol_usuario` | obligatorio |
| `activo` | `boolean` | `true` por defecto; un usuario inactivo no entra, pero sus cobros y ventas se conservan |

## Catálogos

| Tabla | Columnas propias | Reglas |
|---|---|---|
| `plan_membresia` | `nombre varchar(60)`, `duracion_dias integer`, `precio numeric(12,2)`, `referencia_mensual_id bigint` | nombre único sin distinguir mayúsculas; `duracion_dias` de 1 a 730; `precio > 0`; `referencia_mensual_id` es FK opcional a otro plan (no a sí mismo), el de 30 días contra el que se calcula el ahorro. Los descuentos son planes propios, por ejemplo Estudiante |
| `metodo_pago` | `nombre varchar(40)`, `es_efectivo boolean` | nombre único sin distinguir mayúsculas (índice sobre `lower(nombre)`). Con `es_efectivo` se pide lo recibido y se calcula el cambio |
| `motivo_movimiento` | `nombre varchar(60)`, `es_sistema boolean` | nombre único. Los de sistema, como "Existencia inicial", los usa el propio sistema |
| `categoria_producto` | `nombre varchar(60)` | nombre único |

Todas tienen `id bigint` como PK y `activo boolean` (`true` por defecto).

## Datos iniciales

La migración `V2__configuracion_inicial.sql` deja la base lista para usarse; el Administrador los cambia después.

| Tabla | Datos |
|---|---|
| `configuracion` | nombre «Mi gimnasio», `MXN`, 7 días de aviso, 30 de plazo de baja |
| `folio_secuencia` | serie `COBRO`, prefijo `GYM-`, ancho 4 |
| `metodo_pago` | Efectivo (`es_efectivo`), Tarjeta, Transferencia |

## Socios y membresías

### `socio`

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `bigint` | PK |
| `nombre` | `varchar(100)` | obligatorio |
| `telefono` | `char(10)` | obligatorio, `CHECK (telefono ~ '^[0-9]{10}$')` |
| `correo` | `varchar(120)` | opcional; único sin distinguir mayúsculas entre los que lo tienen |
| `fecha_alta` | `date` | obligatoria |

Índices: `UNIQUE (lower(correo)) WHERE correo IS NOT NULL`, búsqueda parcial por `nombre` con GIN y `pg_trgm`, y `telefono`. Los tres sirven a la búsqueda rápida desde cualquier pantalla.

### `pago`

Cobro de una membresía.

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `bigint` | PK |
| `folio` | `varchar(30)` | obligatorio, único; texto ya formado, por ejemplo `GYM-0001` |
| `socio_id` | `bigint` | FK a `socio` |
| `usuario_id` | `bigint` | FK a `usuario`: quién atendió |
| `metodo_pago_id` | `bigint` | FK a `metodo_pago` |
| `monto` | `numeric(12,2)` | obligatorio, `>= 0`; precio del plan en ese momento |
| `efectivo_recibido` | `numeric(12,2)` | solo en efectivo, `>= monto`; el cambio es `efectivo_recibido - monto` |
| `fecha_hora` | `timestamptz` | `now()` por defecto |
| `tipo_cobro` | `tipo_cobro` | obligatorio: `primera`, `renovacion_anticipada`, `renovacion_vencida` o `reactivacion`; lo que fue el cobro en su momento (V4) |
| `operacion_id` | `varchar(64)` | único; lo genera la UI al abrir el formulario para que un doble envío no cobre dos veces (V4) |

Índices: `UNIQUE (id, socio_id)` para la clave compuesta de `membresia`, `(socio_id, fecha_hora)` para el historial del socio y `fecha_hora` para el resumen del día.

### `membresia`

Un periodo pagado. Es historial: se crea al cobrar y no se modifica ni se borra.

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `bigint` | PK |
| `socio_id` | `bigint` | obligatorio |
| `plan_id` | `bigint` | FK a `plan_membresia` |
| `pago_id` | `bigint` | obligatorio, único: un pago genera una membresía |
| `fecha_inicio` | `date` | obligatoria |
| `fecha_vencimiento` | `date` | obligatoria, `> fecha_inicio` |

La clave foránea compuesta `(pago_id, socio_id) → pago (id, socio_id)` garantiza que la membresía pertenezca al mismo socio que hizo el pago.

La membresía vigente de un socio es la de `fecha_vencimiento` más reciente; el índice `(socio_id, fecha_vencimiento)` la encuentra sin recorrer el historial. Al cobrar se bloquea la fila del socio con `SELECT ... FOR UPDATE`, para que dos cobros simultáneos no calculen la misma renovación.

## Productos, ventas e inventario

### `producto`

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `bigint` | PK |
| `clave` | `varchar(20)` | obligatoria, única sin distinguir mayúsculas |
| `nombre` | `varchar(100)` | obligatorio, indexado para la búsqueda |
| `categoria_id` | `bigint` | FK a `categoria_producto` |
| `precio` | `numeric(12,2)` | obligatorio, `>= 0` |
| `minimo` | `integer` | `>= 0`, 0 por defecto |
| `existencia` | `integer` | 0 por defecto; se actualiza en la misma transacción que su movimiento |
| `activo` | `boolean` | `true` por defecto |

Un producto está bajo cuando `existencia <= minimo`. La existencia inicial no se escribe directo: se registra como primer movimiento, con el motivo de sistema "Existencia inicial".

### `venta` y `detalle_venta`

| Tabla | Columna | Tipo | Reglas |
|---|---|---|---|
| `venta` | `folio` | `varchar(30)` | obligatorio, único |
| | `fecha_hora` | `timestamptz` | `now()` por defecto, indexada |
| | `metodo_pago_id` | `bigint` | FK a `metodo_pago` |
| | `total` | `numeric(12,2)` | obligatorio, `>= 0` |
| | `efectivo_recibido` | `numeric(12,2)` | solo en efectivo, `>= total` |
| | `usuario_id` | `bigint` | FK a `usuario`: quién vendió |
| `detalle_venta` | `venta_id` | `bigint` | FK a `venta` |
| | `producto_id` | `bigint` | FK a `producto`; único junto con `venta_id` |
| | `cantidad` | `integer` | `> 0` |
| | `precio_unitario` | `numeric(12,2)` | precio del producto en ese momento |
| | `importe` | `numeric(12,2)` | columna generada: `cantidad * precio_unitario` |

Un producto aparece una sola vez por venta: si se agrega de nuevo, se suma a su cantidad.

### `movimiento_inventario`

| Columna | Tipo | Reglas |
|---|---|---|
| `id` | `bigint` | PK |
| `fecha_hora` | `timestamptz` | `now()` por defecto |
| `producto_id` | `bigint` | FK a `producto` |
| `tipo` | `tipo_movimiento` | obligatorio |
| `cantidad` | `integer` | con signo, `<> 0`: positiva suma, negativa resta. `entrada` exige `> 0` y `salida` exige `< 0` |
| `existencia_resultante` | `integer` | existencia del producto después del movimiento |
| `motivo_movimiento_id` | `bigint` | FK a `motivo_movimiento` |
| `detalle_venta_id` | `bigint` | FK a `detalle_venta`, si lo provocó una venta |
| `usuario_id` | `bigint` | FK a `usuario`: quién lo registró |

Cada movimiento tiene un motivo o una venta que lo explique (al menos uno de los dos). Las ventas generan su movimiento de salida solas, en la misma transacción que la venta.

`existencia_resultante` permite mostrar el historial de un producto sin recalcularlo y detectar si `producto.existencia` se desalineó.
