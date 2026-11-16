-- =====================================================================================================
-- 02_datos.sql · Datos de prueba
--
-- Suficientes para comprobar los CRUD, las relaciones, los filtros, los JOIN, las subconsultas, las
-- funciones y el procedimiento: 3 usuarios, 3 métodos de pago, 5 planes y 12 socios con membresías en
-- todos los estados posibles. Las fechas son relativas a CURRENT_DATE, así que los estados se conservan
-- sin importar el día en que se carguen (con 7 días de aviso y 30 de plazo de baja).
--
-- Las cuentas de usuario de este archivo no pueden entrar a la aplicación: su contrasena_hash no es un
-- hash válido. Para entrar, crea un administrador con scripts/dev/crear-admin-dev.sh.
-- =====================================================================================================

-- ---------- Configuración y folios ----------

INSERT INTO configuracion (nombre_gimnasio, direccion, telefono, leyenda_recibo, moneda, dias_aviso, dias_baja_automatica)
VALUES ('Gimnasio Fuerza', 'Av. Juárez 120, Centro', '555 010 2233', 'Gracias por entrenar con nosotros.', 'MXN', 7, 30);

INSERT INTO folio_secuencia (serie, prefijo, ultimo, ancho) VALUES ('COBRO', 'GYM-', 0, 4);

-- ---------- Catálogos ----------

INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES
    ('Ana Martínez', 'ana', 'sin-acceso: cuenta de datos de prueba', 'administrador'),
    ('Rocío Méndez', 'rocio', 'sin-acceso: cuenta de datos de prueba', 'recepcion'),
    ('Carlos Ruiz', 'carlos', 'sin-acceso: cuenta de datos de prueba', 'caja');
INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol, activo)
VALUES ('Pedro Salas', 'pedro', 'sin-acceso: cuenta de datos de prueba', 'caja', false);

INSERT INTO metodo_pago (nombre, es_efectivo) VALUES
    ('Efectivo', true),
    ('Tarjeta', false),
    ('Transferencia', false);
INSERT INTO metodo_pago (nombre, es_efectivo, activo) VALUES ('Vales', false, false);

INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES
    ('Semanal', 7, 150.00),
    ('Mensual', 30, 450.00),
    ('Estudiante', 30, 350.00);
INSERT INTO plan_membresia (nombre, duracion_dias, precio, referencia_mensual_id) VALUES
    ('Trimestral', 90, 1200.00, (SELECT id FROM plan_membresia WHERE nombre = 'Mensual')),
    ('Anual', 365, 4200.00, (SELECT id FROM plan_membresia WHERE nombre = 'Mensual'));
INSERT INTO plan_membresia (nombre, duracion_dias, precio, activo) VALUES ('Promoción verano', 60, 700.00, false);

-- ---------- Socios ----------

INSERT INTO socio (nombre, telefono, correo, fecha_alta) VALUES
    ('Ana López',      '5512345678', 'ana.lopez@correo.mx',   CURRENT_DATE - 200),
    ('Bruno Díaz',     '5523456789', 'bruno.diaz@correo.mx',  CURRENT_DATE - 120),
    ('Carla Ruiz',     '5534567890', NULL,                    CURRENT_DATE - 90),
    ('Daniel Soto',    '5545678901', 'daniel.soto@correo.mx', CURRENT_DATE - 60),
    ('Elena Mora',     '5556789012', 'elena.mora@correo.mx',  CURRENT_DATE - 150),
    ('Fernando Gil',   '5567890123', NULL,                    CURRENT_DATE - 3),
    ('Gabriela Paz',   '5578901234', 'gaby.paz@correo.mx',    CURRENT_DATE - 110),
    ('Héctor Ríos',    '5589012345', 'hector.rios@correo.mx', CURRENT_DATE - 80),
    ('Irene Vega',     '5590123456', NULL,                    CURRENT_DATE - 40),
    ('Jorge Luna',     '5501234567', 'jorge.luna@correo.mx',  CURRENT_DATE - 20),
    ('Karla Núñez',    '5511223344', 'karla.nunez@correo.mx', CURRENT_DATE - 30),
    ('Lucía Pérez',    '5522334455', 'lucia.perez@correo.mx', CURRENT_DATE - 60);

-- ---------- Cobros y membresías ----------
-- Cada fila: socio, plan, método, quién cobró, inicio, días atrás del cobro, tipo y efectivo recibido.
-- El folio y el monto se calculan aquí; el vencimiento es inicio + duración del plan.

WITH cobro (orden, socio, plan, metodo, usuario, inicio, dias_atras, tipo, efectivo) AS (
    VALUES
        ( 1, 'Ana López',    'Mensual',    'Efectivo',      'rocio',  CURRENT_DATE - 20,  20, 'renovacion_anticipada'::tipo_cobro, 500.00),
        ( 2, 'Bruno Díaz',   'Mensual',    'Tarjeta',       'carlos', CURRENT_DATE - 25,  25, 'primera'::tipo_cobro,               NULL),
        ( 3, 'Carla Ruiz',   'Trimestral', 'Transferencia', 'rocio',  CURRENT_DATE - 60,  60, 'primera'::tipo_cobro,               NULL),
        ( 4, 'Daniel Soto',  'Mensual',    'Efectivo',      'carlos', CURRENT_DATE - 40,  40, 'primera'::tipo_cobro,               450.00),
        ( 5, 'Elena Mora',   'Mensual',    'Tarjeta',       'rocio',  CURRENT_DATE - 80,  80, 'primera'::tipo_cobro,               NULL),
        ( 6, 'Gabriela Paz', 'Anual',      'Tarjeta',       'ana',    CURRENT_DATE - 100, 100, 'primera'::tipo_cobro,              NULL),
        ( 7, 'Héctor Ríos',  'Mensual',    'Efectivo',      'carlos', CURRENT_DATE - 70,  70, 'primera'::tipo_cobro,               1000.00),
        ( 8, 'Lucía Pérez',  'Mensual',    'Tarjeta',       'rocio',  CURRENT_DATE - 45,  45, 'primera'::tipo_cobro,               NULL),
        ( 9, 'Irene Vega',   'Mensual',    'Efectivo',      'rocio',  CURRENT_DATE - 29,  29, 'primera'::tipo_cobro,               450.00),
        (10, 'Jorge Luna',   'Semanal',    'Efectivo',      'carlos', CURRENT_DATE - 10,  10, 'primera'::tipo_cobro,               200.00),
        (11, 'Karla Núñez',  'Estudiante', 'Transferencia', 'carlos', CURRENT_DATE - 15,  15, 'primera'::tipo_cobro,               NULL),
        (12, 'Lucía Pérez',  'Mensual',    'Efectivo',      'rocio',  CURRENT_DATE - 15,  16, 'renovacion_anticipada'::tipo_cobro, 500.00),
        (13, 'Héctor Ríos',  'Mensual',    'Tarjeta',       'rocio',  CURRENT_DATE - 5,   5,  'reactivacion'::tipo_cobro,          NULL),
        (14, 'Ana López',    'Mensual',    'Efectivo',      'carlos', CURRENT_DATE - 50,  50, 'primera'::tipo_cobro,               450.00)
),
pagos AS (
    INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, efectivo_recibido, fecha_hora, tipo_cobro, operacion_id)
    -- Los folios siguen el orden en que se cobró: el más antiguo es GYM-0001.
    SELECT 'GYM-' || lpad((row_number() OVER (ORDER BY c.dias_atras DESC, c.orden))::text, 4, '0'),
           s.id, u.id, m.id, p.precio,
           CASE WHEN m.es_efectivo THEN c.efectivo END,
           (CURRENT_DATE - c.dias_atras) + time '10:30',
           c.tipo,
           'datos-prueba-' || c.orden
    FROM cobro c
    JOIN socio s ON s.nombre = c.socio
    JOIN plan_membresia p ON p.nombre = c.plan
    JOIN metodo_pago m ON m.nombre = c.metodo
    JOIN usuario u ON u.nombre_usuario = c.usuario
    RETURNING id, socio_id, operacion_id
)
INSERT INTO membresia (socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento)
SELECT pg.socio_id, p.id, pg.id, c.inicio, c.inicio + p.duracion_dias
FROM pagos pg
JOIN cobro c ON pg.operacion_id = 'datos-prueba-' || c.orden
JOIN plan_membresia p ON p.nombre = c.plan;

UPDATE folio_secuencia SET ultimo = (SELECT COUNT(*) FROM pago) WHERE serie = 'COBRO';

-- Estados resultantes (hoy): Ana, Carla, Gabriela, Héctor, Karla y Lucía vigentes; Bruno e Irene por
-- vencer; Daniel y Jorge vencidos; Elena en baja; Fernando sin membresía.
