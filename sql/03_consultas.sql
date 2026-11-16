-- =====================================================================================================
-- 03_consultas.sql · Consultas del sistema
--
-- Cada consulta resuelve una necesidad de las pantallas o de los reportes y lleva su identificador para la
-- matriz de evidencias. Los valores fijos (textos, montos, días) son los parámetros que la aplicación
-- enviaría; con 02_datos.sql cada consulta devuelve al menos una fila.
--
--   Q01–Q05  Filtros con WHERE            Q06–Q07  Funciones de PostgreSQL
--   Q08–Q10  JOIN entre tablas            Q11–Q13  Subconsultas
-- =====================================================================================================

-- Q01 · WHERE · Buscar socios por parte del nombre, sin distinguir mayúsculas (buscador de Socios).
SELECT id, nombre, telefono, correo
FROM socio
WHERE nombre ILIKE '%ana%'
ORDER BY nombre;

-- Q02 · WHERE · Planes activos dentro de un rango de precio (opciones del formulario de cobro).
SELECT id, nombre, duracion_dias, precio
FROM plan_membresia
WHERE activo
  AND precio BETWEEN 300 AND 1500
ORDER BY precio;

-- Q03 · WHERE · Cobros en efectivo de los últimos 30 días (corte de caja).
SELECT p.folio, p.fecha_hora, p.monto, p.efectivo_recibido, p.efectivo_recibido - p.monto AS cambio
FROM pago p
WHERE p.efectivo_recibido IS NOT NULL
  AND p.fecha_hora >= CURRENT_DATE - 30
ORDER BY p.fecha_hora DESC;

-- Q04 · WHERE · Usuarios activos que atienden mostrador (recepción o caja).
SELECT id, nombre, nombre_usuario, rol
FROM usuario
WHERE activo
  AND rol IN ('recepcion', 'caja')
ORDER BY nombre;

-- Q05 · WHERE · Membresías que vencen de hoy a 7 días (aviso de «por vencer» del tablero).
SELECT socio_id, plan_id, fecha_inicio, fecha_vencimiento
FROM membresia
WHERE fecha_vencimiento BETWEEN CURRENT_DATE AND CURRENT_DATE + 7
ORDER BY fecha_vencimiento;

-- Q06 · Funciones · Ingresos por mes y por tipo de cobro (date_trunc, to_char, COUNT, SUM, ROUND).
SELECT to_char(date_trunc('month', fecha_hora), 'YYYY-MM') AS mes,
       tipo_cobro,
       COUNT(*)                     AS cobros,
       SUM(monto)                   AS ingresos,
       ROUND(AVG(monto), 2)         AS ticket_promedio
FROM pago
GROUP BY date_trunc('month', fecha_hora), tipo_cobro
ORDER BY mes, tipo_cobro;

-- Q07 · Funciones · Ficha breve de cada socio: antigüedad, nombre en mayúsculas y correo o aviso
-- (age, EXTRACT, upper, COALESCE, CURRENT_DATE).
SELECT id,
       upper(nombre)                                 AS nombre,
       COALESCE(correo, 'sin correo')                AS correo,
       fecha_alta,
       CURRENT_DATE - fecha_alta                     AS dias_inscrito,
       EXTRACT(MONTH FROM age(CURRENT_DATE, fecha_alta))
           + 12 * EXTRACT(YEAR FROM age(CURRENT_DATE, fecha_alta)) AS meses_inscrito
FROM socio
ORDER BY fecha_alta;

-- Q08 · JOIN · Historial de cobros con socio, plan, periodo, método y quién cobró (ficha del socio y recibos).
SELECT p.folio,
       p.fecha_hora,
       s.nombre       AS socio,
       pl.nombre      AS plan,
       m.fecha_inicio,
       m.fecha_vencimiento,
       mp.nombre      AS metodo_pago,
       u.nombre       AS atendio,
       p.tipo_cobro,
       p.monto
FROM pago p
JOIN socio s           ON s.id = p.socio_id
JOIN membresia m       ON m.pago_id = p.id
JOIN plan_membresia pl ON pl.id = m.plan_id
JOIN metodo_pago mp    ON mp.id = p.metodo_pago_id
JOIN usuario u         ON u.id = p.usuario_id
ORDER BY p.fecha_hora DESC;

-- Q09 · JOIN · Ingresos por método de pago, incluidos los que no se han usado (LEFT JOIN).
SELECT mp.nombre,
       mp.activo,
       COUNT(p.id)                AS cobros,
       COALESCE(SUM(p.monto), 0)  AS ingresos
FROM metodo_pago mp
LEFT JOIN pago p ON p.metodo_pago_id = mp.id
GROUP BY mp.id, mp.nombre, mp.activo
ORDER BY ingresos DESC, mp.nombre;

-- Q10 · JOIN · Cobros registrados por cada usuario y su rol (desempeño de mostrador).
SELECT u.nombre, u.rol, COUNT(p.id) AS cobros, COALESCE(SUM(p.monto), 0) AS total
FROM usuario u
JOIN pago p ON p.usuario_id = u.id
GROUP BY u.id, u.nombre, u.rol
ORDER BY total DESC;

-- Q11 · Subconsulta · Socios que nunca han pagado una membresía (NOT EXISTS).
SELECT s.id, s.nombre, s.telefono, s.fecha_alta
FROM socio s
WHERE NOT EXISTS (SELECT 1 FROM membresia m WHERE m.socio_id = s.id)
ORDER BY s.nombre;

-- Q12 · Subconsulta · Cobros por encima del promedio de todos los cobros.
SELECT folio, fecha_hora, monto
FROM pago
WHERE monto > (SELECT AVG(monto) FROM pago)
ORDER BY monto DESC;

-- Q13 · Subconsulta · Última membresía de cada socio y días que le quedan (subconsulta correlacionada).
SELECT s.nombre,
       pl.nombre                               AS plan,
       m.fecha_vencimiento,
       m.fecha_vencimiento - CURRENT_DATE      AS dias_restantes
FROM socio s
JOIN membresia m       ON m.socio_id = s.id
JOIN plan_membresia pl ON pl.id = m.plan_id
WHERE m.fecha_vencimiento = (SELECT MAX(m2.fecha_vencimiento) FROM membresia m2 WHERE m2.socio_id = s.id)
ORDER BY dias_restantes;
