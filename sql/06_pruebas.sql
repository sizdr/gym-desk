-- =====================================================================================================
-- 06_pruebas.sql · Uso de las funciones, el procedimiento y el trigger con los datos de prueba
--
-- Cada bloque muestra su resultado. Lo que modifica datos corre dentro de una transacción que se deshace
-- al final, así que el archivo se puede ejecutar varias veces.
-- =====================================================================================================

-- F01 · Vencimiento de un plan Mensual que empieza hoy (hoy + 30 días).
SELECT CURRENT_DATE AS inicio,
       fn_fecha_vencimiento(CURRENT_DATE, (SELECT id FROM plan_membresia WHERE nombre = 'Mensual')) AS vencimiento;

-- F02 · Estado de cada socio hoy (módulo de control de vigencias).
SELECT id, nombre, fn_estado_socio(id) AS estado
FROM socio
ORDER BY nombre;

-- F02 · Cuántos socios hay en cada estado.
SELECT fn_estado_socio(id) AS estado, COUNT(*) AS socios
FROM socio
GROUP BY fn_estado_socio(id)
ORDER BY estado;

-- F03 · Formato de folio: con ceros, y sin cortar un número más largo que el ancho.
SELECT fn_formatear_folio('GYM-', 7, 4) AS corto, fn_formatear_folio('GYM-', 123456, 4) AS largo;

BEGIN;

-- P01 · CALL no acepta subconsultas como argumentos: cada bloque busca primero los ids y después llama al
-- procedimiento.

-- Cobrar un Mensual en efectivo a Fernando Gil (sin membresía): primera, empieza hoy.
DO $$
DECLARE
    v_socio   bigint := (SELECT id FROM socio WHERE nombre = 'Fernando Gil');
    v_plan    bigint := (SELECT id FROM plan_membresia WHERE nombre = 'Mensual');
    v_metodo  bigint := (SELECT id FROM metodo_pago WHERE nombre = 'Efectivo');
    v_usuario bigint := (SELECT id FROM usuario WHERE nombre_usuario = 'carlos');
    v_pago    bigint;
    v_folio   varchar;
BEGIN
    CALL sp_registrar_cobro(v_socio, v_plan, v_metodo, v_usuario, 500, 'prueba-p01', NULL, v_pago, v_folio);
    RAISE NOTICE 'Cobro registrado: % (pago %)', v_folio, v_pago;
END;
$$;

-- Renovar a Bruno Díaz (por vencer) con tarjeta: renovación anticipada, empieza al terminar la vigente.
DO $$
DECLARE
    v_socio   bigint := (SELECT id FROM socio WHERE nombre = 'Bruno Díaz');
    v_plan    bigint := (SELECT id FROM plan_membresia WHERE nombre = 'Trimestral');
    v_metodo  bigint := (SELECT id FROM metodo_pago WHERE nombre = 'Tarjeta');
    v_usuario bigint := (SELECT id FROM usuario WHERE nombre_usuario = 'rocio');
    v_pago    bigint;
    v_folio   varchar;
BEGIN
    CALL sp_registrar_cobro(v_socio, v_plan, v_metodo, v_usuario, NULL, 'prueba-p01-b', NULL, v_pago, v_folio);
    RAISE NOTICE 'Cobro registrado: % (pago %)', v_folio, v_pago;
END;
$$;

-- El mismo envío otra vez no cobra dos veces: devuelve el folio del primero.
DO $$
DECLARE
    v_socio   bigint := (SELECT id FROM socio WHERE nombre = 'Fernando Gil');
    v_plan    bigint := (SELECT id FROM plan_membresia WHERE nombre = 'Mensual');
    v_metodo  bigint := (SELECT id FROM metodo_pago WHERE nombre = 'Efectivo');
    v_usuario bigint := (SELECT id FROM usuario WHERE nombre_usuario = 'carlos');
    v_pago    bigint;
    v_folio   varchar;
BEGIN
    CALL sp_registrar_cobro(v_socio, v_plan, v_metodo, v_usuario, 500, 'prueba-p01', NULL, v_pago, v_folio);
    RAISE NOTICE 'Operación repetida, mismo cobro: % (pago %)', v_folio, v_pago;
END;
$$;

-- Resultado: los cobros nuevos, su tipo y su periodo.
SELECT p.folio, s.nombre, p.tipo_cobro, p.monto, p.efectivo_recibido - p.monto AS cambio,
       m.fecha_inicio, m.fecha_vencimiento, fn_estado_socio(s.id) AS estado_ahora
FROM pago p
JOIN socio s ON s.id = p.socio_id
JOIN membresia m ON m.pago_id = p.id
WHERE p.operacion_id LIKE 'prueba-%'
ORDER BY p.folio;

ROLLBACK;

-- T01 · Borrar un cobro se rechaza: «Los registros de pago no se borran: son el historial de cobros.»
-- (Descomenta la línea para ver el error.)
-- DELETE FROM pago WHERE folio = 'GYM-0001';
