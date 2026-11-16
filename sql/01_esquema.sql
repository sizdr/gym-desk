-- =====================================================================================================
-- 01_esquema.sql · Sistema de gestión de gimnasio · Proyecto Final Integrador de Bases de Datos (303-ISW)
--
-- Crea el esquema completo en una base vacía: tipos enumerados, tablas, restricciones e índices.
-- Es el resultado de las migraciones V1 a V5 de la aplicación (src/main/resources/db/migration) en un solo
-- archivo; la prueba SqlScriptsTest comprueba que ambos caminos dejan exactamente el mismo esquema.
--
-- Orden de ejecución: 01_esquema → 02_datos → 03_consultas → 04_funciones → 05_procedimientos → 06_pruebas.
-- =====================================================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------- Tipos enumerados ----------

CREATE TYPE rol_usuario AS ENUM ('administrador', 'recepcion', 'caja');
CREATE TYPE tipo_cobro AS ENUM ('primera', 'renovacion_anticipada', 'renovacion_vencida', 'reactivacion');

-- ---------- Configuración ----------

CREATE TABLE configuracion (
    id                   smallint     PRIMARY KEY DEFAULT 1,
    nombre_gimnasio      varchar(100) NOT NULL,
    direccion            varchar(200),
    telefono             varchar(30),
    logotipo             bytea,
    leyenda_recibo       varchar(160),
    moneda               char(3)      NOT NULL DEFAULT 'MXN',
    dias_aviso           integer      NOT NULL DEFAULT 7,
    dias_baja_automatica integer      NOT NULL DEFAULT 30,
    CONSTRAINT configuracion_una_fila CHECK (id = 1),
    CONSTRAINT configuracion_dias_aviso_check CHECK (dias_aviso BETWEEN 1 AND 60),
    CONSTRAINT configuracion_dias_baja_check CHECK (dias_baja_automatica BETWEEN 1 AND 365)
);

CREATE TABLE folio_secuencia (
    serie   varchar(10) PRIMARY KEY,
    prefijo varchar(20) NOT NULL,
    ultimo  bigint      NOT NULL DEFAULT 0,
    ancho   smallint    NOT NULL DEFAULT 4,
    CONSTRAINT folio_secuencia_prefijo_check CHECK (prefijo ~ '^[A-Z0-9-]{1,8}$')
);

-- ---------- Usuarios ----------

CREATE TABLE usuario (
    id              bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          varchar(100) NOT NULL,
    nombre_usuario  varchar(50)  NOT NULL,
    contrasena_hash varchar(255) NOT NULL,
    rol             rol_usuario  NOT NULL,
    activo          boolean      NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX usuario_nombre_usuario_key ON usuario (lower(nombre_usuario));

-- ---------- Catálogos ----------

CREATE TABLE plan_membresia (
    id                    bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre                varchar(60)   NOT NULL,
    duracion_dias         integer       NOT NULL,
    precio                numeric(12,2) NOT NULL,
    activo                boolean       NOT NULL DEFAULT true,
    referencia_mensual_id bigint        REFERENCES plan_membresia (id),
    CONSTRAINT plan_membresia_duracion_check CHECK (duracion_dias BETWEEN 1 AND 730),
    CONSTRAINT plan_membresia_precio_check CHECK (precio > 0),
    CONSTRAINT plan_membresia_referencia_check CHECK (referencia_mensual_id <> id)
);
CREATE UNIQUE INDEX plan_membresia_nombre_key ON plan_membresia (lower(nombre));

CREATE TABLE metodo_pago (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre      varchar(40) NOT NULL,
    es_efectivo boolean     NOT NULL DEFAULT false,
    activo      boolean     NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX metodo_pago_nombre_key ON metodo_pago (lower(nombre));

-- ---------- Socios, cobros y membresías ----------

CREATE TABLE socio (
    id         bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre     varchar(100) NOT NULL,
    telefono   char(10)     NOT NULL,
    correo     varchar(120),
    fecha_alta date         NOT NULL,
    CONSTRAINT socio_telefono_check CHECK (telefono ~ '^[0-9]{10}$')
);
CREATE UNIQUE INDEX socio_correo_key ON socio (lower(correo)) WHERE correo IS NOT NULL;
CREATE INDEX socio_nombre_trgm_idx ON socio USING gin (nombre gin_trgm_ops);
CREATE INDEX socio_telefono_idx ON socio (telefono);

CREATE TABLE pago (
    id                bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio             varchar(30)   NOT NULL UNIQUE,
    socio_id          bigint        NOT NULL REFERENCES socio (id),
    usuario_id        bigint        NOT NULL REFERENCES usuario (id),
    metodo_pago_id    bigint        NOT NULL REFERENCES metodo_pago (id),
    monto             numeric(12,2) NOT NULL,
    efectivo_recibido numeric(12,2),
    fecha_hora        timestamptz   NOT NULL DEFAULT now(),
    tipo_cobro        tipo_cobro    NOT NULL,
    operacion_id      varchar(64)   UNIQUE,
    CONSTRAINT pago_id_socio_key UNIQUE (id, socio_id),
    CONSTRAINT pago_monto_check CHECK (monto >= 0),
    CONSTRAINT pago_efectivo_check CHECK (efectivo_recibido >= monto)
);
CREATE INDEX pago_socio_fecha_idx ON pago (socio_id, fecha_hora);
CREATE INDEX pago_fecha_idx ON pago (fecha_hora);

CREATE TABLE membresia (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    socio_id          bigint NOT NULL,
    plan_id           bigint NOT NULL REFERENCES plan_membresia (id),
    pago_id           bigint NOT NULL UNIQUE,
    fecha_inicio      date   NOT NULL,
    fecha_vencimiento date   NOT NULL,
    CONSTRAINT membresia_vigencia_check CHECK (fecha_vencimiento > fecha_inicio),
    CONSTRAINT membresia_pago_fkey FOREIGN KEY (pago_id, socio_id) REFERENCES pago (id, socio_id)
);
CREATE INDEX membresia_socio_vencimiento_idx ON membresia (socio_id, fecha_vencimiento);

-- ---------- Diccionario de datos: significado de cada tabla y atributo ----------

COMMENT ON TYPE rol_usuario IS 'Rol de quien usa el sistema: decide qué secciones ve y qué operaciones puede hacer.';
COMMENT ON TYPE tipo_cobro IS 'Qué fue un cobro según el estado del socio en ese momento; decide dónde empieza la vigencia.';

COMMENT ON TABLE configuracion IS 'Parámetros del gimnasio. Una sola fila.';
COMMENT ON COLUMN configuracion.id IS 'Identificador fijo de la única fila (siempre 1).';
COMMENT ON COLUMN configuracion.nombre_gimnasio IS 'Nombre que aparece en el acceso, el menú y los recibos.';
COMMENT ON COLUMN configuracion.direccion IS 'Dirección del gimnasio para el recibo.';
COMMENT ON COLUMN configuracion.telefono IS 'Teléfono del gimnasio para el recibo.';
COMMENT ON COLUMN configuracion.logotipo IS 'Imagen PNG o JPG del logotipo (hasta 300 KB).';
COMMENT ON COLUMN configuracion.leyenda_recibo IS 'Texto al pie de los recibos.';
COMMENT ON COLUMN configuracion.moneda IS 'Código ISO 4217 de la moneda de todos los importes; no cambia si ya hay cobros.';
COMMENT ON COLUMN configuracion.dias_aviso IS 'Días antes del vencimiento en que un socio pasa a «Por vencer».';
COMMENT ON COLUMN configuracion.dias_baja_automatica IS 'Días después del vencimiento sin renovar en que un socio pasa a «Baja».';

COMMENT ON TABLE folio_secuencia IS 'Contador de folios por serie. Solo existe la serie COBRO.';
COMMENT ON COLUMN folio_secuencia.serie IS 'Nombre de la serie de folios.';
COMMENT ON COLUMN folio_secuencia.prefijo IS 'Texto antes del número, por ejemplo GYM-.';
COMMENT ON COLUMN folio_secuencia.ultimo IS 'Último número entregado; el siguiente folio usa ultimo + 1.';
COMMENT ON COLUMN folio_secuencia.ancho IS 'Dígitos del número, rellenados con ceros a la izquierda.';

COMMENT ON TABLE usuario IS 'Personas que usan el sistema.';
COMMENT ON COLUMN usuario.id IS 'Identificador generado por la base.';
COMMENT ON COLUMN usuario.nombre IS 'Nombre completo de la persona.';
COMMENT ON COLUMN usuario.nombre_usuario IS 'Nombre con el que entra; único sin distinguir mayúsculas.';
COMMENT ON COLUMN usuario.contrasena_hash IS 'Hash Argon2id de la contraseña; la contraseña nunca se guarda.';
COMMENT ON COLUMN usuario.rol IS 'Rol del usuario.';
COMMENT ON COLUMN usuario.activo IS 'Un usuario inactivo no puede entrar; sus cobros se conservan.';

COMMENT ON TABLE plan_membresia IS 'Catálogo de planes de membresía.';
COMMENT ON COLUMN plan_membresia.id IS 'Identificador generado por la base.';
COMMENT ON COLUMN plan_membresia.nombre IS 'Nombre del plan; único sin distinguir mayúsculas.';
COMMENT ON COLUMN plan_membresia.duracion_dias IS 'Días que cubre el plan (1 a 730).';
COMMENT ON COLUMN plan_membresia.precio IS 'Precio actual; los cobros anteriores guardan el suyo.';
COMMENT ON COLUMN plan_membresia.activo IS 'Un plan inactivo ya no se ofrece al cobrar.';
COMMENT ON COLUMN plan_membresia.referencia_mensual_id IS 'Plan de 30 días contra el que se calcula el ahorro de este plan.';

COMMENT ON TABLE metodo_pago IS 'Catálogo de formas de pago.';
COMMENT ON COLUMN metodo_pago.id IS 'Identificador generado por la base.';
COMMENT ON COLUMN metodo_pago.nombre IS 'Nombre del método; único sin distinguir mayúsculas.';
COMMENT ON COLUMN metodo_pago.es_efectivo IS 'Si es efectivo se pide lo recibido y se calcula el cambio.';
COMMENT ON COLUMN metodo_pago.activo IS 'Un método inactivo ya no se ofrece al cobrar.';

COMMENT ON TABLE socio IS 'Personas inscritas en el gimnasio. Su estado se calcula a partir de sus membresías.';
COMMENT ON COLUMN socio.id IS 'Identificador generado por la base.';
COMMENT ON COLUMN socio.nombre IS 'Nombre completo del socio.';
COMMENT ON COLUMN socio.telefono IS 'Teléfono a 10 dígitos, sin espacios.';
COMMENT ON COLUMN socio.correo IS 'Correo opcional; único sin distinguir mayúsculas.';
COMMENT ON COLUMN socio.fecha_alta IS 'Día en que se inscribió.';

COMMENT ON TABLE pago IS 'Cobro de una membresía.';
COMMENT ON COLUMN pago.id IS 'Identificador generado por la base.';
COMMENT ON COLUMN pago.folio IS 'Folio impreso en el recibo, por ejemplo GYM-0001.';
COMMENT ON COLUMN pago.socio_id IS 'Socio que pagó.';
COMMENT ON COLUMN pago.usuario_id IS 'Usuario que registró el cobro.';
COMMENT ON COLUMN pago.metodo_pago_id IS 'Forma de pago.';
COMMENT ON COLUMN pago.monto IS 'Precio del plan al momento del cobro.';
COMMENT ON COLUMN pago.efectivo_recibido IS 'Solo en efectivo: lo recibido; el cambio es efectivo_recibido - monto.';
COMMENT ON COLUMN pago.fecha_hora IS 'Momento del cobro.';
COMMENT ON COLUMN pago.tipo_cobro IS 'Primera, renovación anticipada, renovación vencida o reactivación.';
COMMENT ON COLUMN pago.operacion_id IS 'Identificador de la operación; un segundo envío igual no cobra dos veces.';

COMMENT ON TABLE membresia IS 'Periodo pagado por un socio. Cada pago genera exactamente una.';
COMMENT ON COLUMN membresia.id IS 'Identificador generado por la base.';
COMMENT ON COLUMN membresia.socio_id IS 'Socio cubierto; debe ser el mismo que hizo el pago.';
COMMENT ON COLUMN membresia.plan_id IS 'Plan cobrado.';
COMMENT ON COLUMN membresia.pago_id IS 'Pago que generó esta membresía.';
COMMENT ON COLUMN membresia.fecha_inicio IS 'Primer día cubierto.';
COMMENT ON COLUMN membresia.fecha_vencimiento IS 'fecha_inicio + duración del plan; el socio está cubierto mientras hoy <= esta fecha.';
