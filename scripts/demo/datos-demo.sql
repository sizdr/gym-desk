-- =====================================================================================================
-- datos-demo.sql · Datos para la demostración de la aplicación
--
-- Se carga sobre una base ya migrada por la aplicación (la crea scripts/demo/crear-demo.sh) y con al menos
-- un administrador. Los cobros se atribuyen a las cuentas que existan: el primer usuario activo de cada rol
-- y, si falta ese rol, el primer administrador. No crea cuentas: sus contraseñas se piden al crear la base.
--
-- Las fechas son relativas a CURRENT_DATE (la sesión usa la zona horaria del equipo), así que el día en que
-- se cargue siempre habrá socios por vencer, vencidos y cobros de hoy. Con 7 días de aviso y 30 de baja:
--   vigentes (9):     Ana, Carla, Gabriela, Héctor, Karla, Lucía, Mario, Nora, Óscar
--   por vencer (6):   Bruno (mañana), Irene (3 días), Pablo (5), Rosa (6), Sergio (hoy), Yolanda (mañana)
--   vencidas (4):     Ulises (ayer), Jorge (3 días), Daniel (10), Teresa (25: pasa a baja en 5 días)
--   en baja (2):      Elena, Víctor
--   sin membresía (2): Fernando, Wendy
--   25 cobros, 3 de hoy (Nora, Óscar y Yolanda), con los cuatro tipos de cobro.
--
-- Se niega a cargarse en una base que ya tiene cobros o que no parece de demostración: los cobros no se
-- pueden borrar (trg_pago_sin_borrado), así que cargarlos por error en la base real no tendría vuelta atrás.
-- Todo va en una sola transacción: se carga completo o no se carga nada.
-- =====================================================================================================

BEGIN;

DO $$
BEGIN
    IF current_database() NOT LIKE '%demo%' AND current_setting('gimnasio.demo', true) IS DISTINCT FROM 'si' THEN
        RAISE EXCEPTION 'La base «%» no es de demostración; estos datos solo se cargan en gimnasio_demo.',
            current_database();
    END IF;
    IF EXISTS (SELECT 1 FROM pago) THEN
        RAISE EXCEPTION 'La base ya tiene cobros; los datos de demostración van en una base nueva.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM usuario WHERE rol = 'administrador' AND activo) THEN
        RAISE EXCEPTION 'Falta un administrador activo: los cobros necesitan quién los registró.';
    END IF;
END
$$;

-- ---------- Configuración y folios ----------

INSERT INTO configuracion (id, nombre_gimnasio, direccion, telefono, leyenda_recibo, moneda, dias_aviso,
                           dias_baja_automatica)
VALUES (1, 'Gimnasio Fuerza', 'Av. Juárez 120, Centro', '555 010 2233', 'Gracias por entrenar con nosotros.',
        'MXN', 7, 30)
ON CONFLICT (id) DO UPDATE
    SET nombre_gimnasio = EXCLUDED.nombre_gimnasio, direccion = EXCLUDED.direccion,
        telefono = EXCLUDED.telefono, leyenda_recibo = EXCLUDED.leyenda_recibo, moneda = EXCLUDED.moneda,
        dias_aviso = EXCLUDED.dias_aviso, dias_baja_automatica = EXCLUDED.dias_baja_automatica;

INSERT INTO folio_secuencia (serie, prefijo, ultimo, ancho) VALUES ('COBRO', 'GYM-', 0, 4)
ON CONFLICT (serie) DO NOTHING;

-- ---------- Catálogos ----------

INSERT INTO metodo_pago (nombre, es_efectivo) VALUES
    ('Efectivo', true),
    ('Tarjeta', false),
    ('Transferencia', false)
ON CONFLICT (lower(nombre)) DO NOTHING;
INSERT INTO metodo_pago (nombre, es_efectivo, activo) VALUES ('Vales', false, false)
ON CONFLICT (lower(nombre)) DO NOTHING;

INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES
    ('Visita', 1, 60.00),
    ('Semanal', 7, 150.00),
    ('Mensual', 30, 450.00),
    ('Estudiante', 30, 350.00);
INSERT INTO plan_membresia (nombre, duracion_dias, precio, referencia_mensual_id)
SELECT v.nombre, v.dias, v.precio, (SELECT id FROM plan_membresia WHERE nombre = 'Mensual')
FROM (VALUES ('Trimestral', 90, 1200.00), ('Semestral', 180, 2300.00), ('Anual', 365, 4200.00))
    AS v (nombre, dias, precio);
INSERT INTO plan_membresia (nombre, duracion_dias, precio, activo) VALUES ('Promoción verano', 60, 700.00, false);

-- ---------- Socios ----------

INSERT INTO socio (nombre, telefono, correo, fecha_alta) VALUES
    ('Ana López',        '5512345678', 'ana.lopez@correo.mx',     CURRENT_DATE - 60),
    ('Bruno Díaz',       '5523456789', 'bruno.diaz@correo.mx',    CURRENT_DATE - 35),
    ('Carla Ruiz',       '5534567890', NULL,                      CURRENT_DATE - 30),
    ('Daniel Soto',      '5545678901', 'daniel.soto@correo.mx',   CURRENT_DATE - 45),
    ('Elena Mora',       '5556789012', 'elena.mora@correo.mx',    CURRENT_DATE - 90),
    ('Fernando Gil',     '5567890123', NULL,                      CURRENT_DATE - 3),
    ('Gabriela Paz',     '5578901234', 'gaby.paz@correo.mx',      CURRENT_DATE - 110),
    ('Héctor Ríos',      '5589012345', 'hector.rios@correo.mx',   CURRENT_DATE - 85),
    ('Irene Vega',       '5590123456', NULL,                      CURRENT_DATE - 27),
    ('Jorge Luna',       '5501234567', 'jorge.luna@correo.mx',    CURRENT_DATE - 12),
    ('Karla Núñez',      '5511223344', 'karla.nunez@correo.mx',   CURRENT_DATE - 20),
    ('Lucía Pérez',      '5522334455', 'lucia.perez@correo.mx',   CURRENT_DATE - 50),
    ('Mario Castro',     '5533445566', NULL,                      CURRENT_DATE - 40),
    ('Nora Silva',       '5544556677', 'nora.silva@correo.mx',    CURRENT_DATE),
    ('Óscar Ibarra',     '5555667788', 'oscar.ibarra@correo.mx',  CURRENT_DATE - 40),
    ('Pablo Reyes',      '5566778899', NULL,                      CURRENT_DATE - 2),
    ('Rosa Jiménez',     '5577889900', 'rosa.jimenez@correo.mx',  CURRENT_DATE - 24),
    ('Sergio Vargas',    '5588990011', NULL,                      CURRENT_DATE - 31),
    ('Teresa Campos',    '5599001122', 'teresa.campos@correo.mx', CURRENT_DATE - 60),
    ('Ulises Mejía',     '5500112233', NULL,                      CURRENT_DATE - 31),
    ('Víctor Hernández', '5510203040', 'victor.hdz@correo.mx',    CURRENT_DATE - 160),
    ('Wendy Torres',     '5520304050', 'wendy.torres@correo.mx',  CURRENT_DATE),
    ('Yolanda Ortiz',    '5530405060', NULL,                      CURRENT_DATE);

-- ---------- Cobros y membresías ----------
-- Cada fila: socio, plan, método, rol de quien cobró, días atrás del cobro, inicio (días desde hoy), tipo,
-- efectivo recibido y minutos (los de hoy: cuánto antes de ahora; los demás: después de las 9:00).
-- El tipo sigue las reglas de la app: sin membresía anterior, PRIMERA; con la anterior sin vencer,
-- RENOVACION_ANTICIPADA (empieza al terminar); vencida menos de 30 días, RENOVACION_VENCIDA; 30 o más,
-- REACTIVACION (estas dos empiezan el día del cobro).

CREATE TEMPORARY TABLE demo_cobro (
    orden      int,
    socio      text,
    plan       text,
    metodo     text,
    rol        rol_usuario,
    dias_atras int,
    inicio     int,
    tipo       tipo_cobro,
    efectivo   numeric(12,2),
    minutos    int
) ON COMMIT DROP;

INSERT INTO demo_cobro VALUES
    ( 1, 'Víctor Hernández', 'Trimestral', 'Tarjeta',       'administrador', 150, -150, 'primera',                NULL,  35),
    ( 2, 'Gabriela Paz',     'Anual',      'Tarjeta',       'administrador', 100, -100, 'primera',                NULL,  80),
    ( 3, 'Héctor Ríos',      'Mensual',    'Efectivo',      'caja',           80,  -80, 'primera',             1000.00,  50),
    ( 4, 'Elena Mora',       'Mensual',    'Tarjeta',       'recepcion',      80,  -80, 'primera',                NULL, 140),
    ( 5, 'Teresa Campos',    'Mensual',    'Tarjeta',       'recepcion',      55,  -55, 'primera',                NULL,  20),
    ( 6, 'Lucía Pérez',      'Mensual',    'Tarjeta',       'recepcion',      45,  -45, 'primera',                NULL,  65),
    ( 7, 'Ana López',        'Mensual',    'Tarjeta',       'recepcion',      40,  -40, 'primera',                NULL,  15),
    ( 8, 'Daniel Soto',      'Mensual',    'Efectivo',      'caja',           40,  -40, 'primera',              450.00,  95),
    ( 9, 'Mario Castro',     'Semestral',  'Transferencia', 'administrador',  40,  -40, 'primera',                NULL, 190),
    (10, 'Óscar Ibarra',     'Mensual',    'Efectivo',      'caja',           35,  -35, 'primera',              500.00,  45),
    (11, 'Ulises Mejía',     'Mensual',    'Transferencia', 'recepcion',      31,  -31, 'primera',                NULL, 110),
    (12, 'Sergio Vargas',    'Mensual',    'Tarjeta',       'caja',           30,  -30, 'primera',                NULL,  70),
    (13, 'Carla Ruiz',       'Trimestral', 'Transferencia', 'recepcion',      30,  -30, 'primera',                NULL, 160),
    (14, 'Bruno Díaz',       'Mensual',    'Tarjeta',       'caja',           29,  -29, 'primera',                NULL,  25),
    (15, 'Irene Vega',       'Mensual',    'Efectivo',      'recepcion',      27,  -27, 'primera',              450.00,  55),
    (16, 'Rosa Jiménez',     'Estudiante', 'Transferencia', 'recepcion',      24,  -24, 'primera',                NULL, 130),
    (17, 'Lucía Pérez',      'Mensual',    'Efectivo',      'recepcion',      16,  -15, 'renovacion_anticipada', 500.00,  40),
    (18, 'Karla Núñez',      'Estudiante', 'Transferencia', 'caja',           15,  -15, 'primera',                NULL,  90),
    (19, 'Héctor Ríos',      'Mensual',    'Tarjeta',       'recepcion',      12,  -12, 'reactivacion',           NULL, 120),
    (20, 'Jorge Luna',       'Semanal',    'Efectivo',      'caja',           10,  -10, 'primera',              200.00,  10),
    (21, 'Ana López',        'Mensual',    'Efectivo',      'caja',           10,  -10, 'renovacion_anticipada', 500.00, 150),
    (22, 'Pablo Reyes',      'Semanal',    'Efectivo',      'caja',            2,   -2, 'primera',              200.00,  60),
    (23, 'Nora Silva',       'Mensual',    'Efectivo',      'caja',            0,    0, 'primera',              500.00,  95),
    (24, 'Óscar Ibarra',     'Trimestral', 'Tarjeta',       'recepcion',       0,    0, 'renovacion_vencida',     NULL,  50),
    (25, 'Yolanda Ortiz',    'Visita',     'Efectivo',      'caja',            0,    0, 'primera',              100.00,  15);

-- Quién cobró: el primer usuario activo del rol; si no hay, el primer administrador activo.
CREATE TEMPORARY TABLE demo_cajero ON COMMIT DROP AS
SELECT r.rol,
       COALESCE((SELECT id FROM usuario WHERE rol = r.rol AND activo ORDER BY id LIMIT 1),
                (SELECT id FROM usuario WHERE rol = 'administrador' AND activo ORDER BY id LIMIT 1)) AS usuario_id
FROM unnest(enum_range(NULL::rol_usuario)) AS r (rol);

WITH cobro AS (
    SELECT c.*,
           CASE WHEN c.dias_atras = 0
                -- Hoy: un rato antes de ahora, pero nunca de ayer (si se carga poco después de medianoche).
                THEN GREATEST(CURRENT_DATE::timestamptz + interval '1 minute', now() - make_interval(mins => c.minutos))
                ELSE (CURRENT_DATE - c.dias_atras) + time '09:00' + make_interval(mins => c.minutos)
           END AS cuando
    FROM demo_cobro c
),
pagos AS (
    INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, efectivo_recibido, fecha_hora, tipo_cobro,
                      operacion_id)
    -- Los folios siguen el orden en que se cobró: el más antiguo es GYM-0001.
    SELECT 'GYM-' || lpad((row_number() OVER (ORDER BY c.cuando, c.orden))::text, 4, '0'),
           s.id, k.usuario_id, m.id, p.precio,
           CASE WHEN m.es_efectivo THEN c.efectivo END,
           c.cuando, c.tipo, 'demo-' || c.orden
    FROM cobro c
    JOIN socio s ON s.nombre = c.socio
    JOIN plan_membresia p ON p.nombre = c.plan
    JOIN metodo_pago m ON m.nombre = c.metodo
    JOIN demo_cajero k ON k.rol = c.rol
    RETURNING id, socio_id, operacion_id
)
INSERT INTO membresia (socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento)
SELECT pg.socio_id, p.id, pg.id, CURRENT_DATE + c.inicio, CURRENT_DATE + c.inicio + p.duracion_dias
FROM pagos pg
JOIN demo_cobro c ON pg.operacion_id = 'demo-' || c.orden
JOIN plan_membresia p ON p.nombre = c.plan;

UPDATE folio_secuencia SET ultimo = (SELECT COUNT(*) FROM pago) WHERE serie = 'COBRO';

COMMIT;
