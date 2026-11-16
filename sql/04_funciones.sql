-- =====================================================================================================
-- 04_funciones.sql · Funciones definidas por el usuario y trigger que protege el historial
--
-- La aplicación tiene las mismas funciones: la migración V6__funciones_y_procedimientos.sql contiene este
-- archivo y 05_procedimientos.sql tal cual (SqlScriptsTest lo comprueba).
-- =====================================================================================================

-- F01 · Fecha de vencimiento de una membresía: el inicio más la duración del plan.
-- Un plan de 30 días que empieza el 01/11 vence el 01/12.
CREATE OR REPLACE FUNCTION fn_fecha_vencimiento(p_inicio date, p_plan_id bigint)
    RETURNS date
    LANGUAGE plpgsql
    STABLE
AS $$
DECLARE
    v_duracion integer;
BEGIN
    SELECT duracion_dias INTO v_duracion FROM plan_membresia WHERE id = p_plan_id;
    IF v_duracion IS NULL THEN
        RAISE EXCEPTION 'El plan % no existe.', p_plan_id;
    END IF;
    RETURN p_inicio + v_duracion;
END;
$$;

-- F02 · Estado de un socio en una fecha: SIN_MEMBRESIA, VIGENTE, POR_VENCER, VENCIDA o BAJA.
-- Se calcula con su último vencimiento y los plazos de configuracion; el estado nunca se guarda.
CREATE OR REPLACE FUNCTION fn_estado_socio(p_socio_id bigint, p_fecha date DEFAULT CURRENT_DATE)
    RETURNS varchar
    LANGUAGE plpgsql
    STABLE
AS $$
DECLARE
    v_vencimiento date;
    v_aviso       integer;
    v_baja        integer;
    v_dias        integer;
BEGIN
    SELECT MAX(fecha_vencimiento) INTO v_vencimiento FROM membresia WHERE socio_id = p_socio_id;
    IF v_vencimiento IS NULL THEN
        RETURN 'SIN_MEMBRESIA';
    END IF;
    SELECT dias_aviso, dias_baja_automatica INTO v_aviso, v_baja FROM configuracion WHERE id = 1;
    v_dias := v_vencimiento - p_fecha;
    IF v_dias >= 0 THEN
        RETURN CASE WHEN v_dias <= v_aviso THEN 'POR_VENCER' ELSE 'VIGENTE' END;
    END IF;
    RETURN CASE WHEN -v_dias >= v_baja THEN 'BAJA' ELSE 'VENCIDA' END;
END;
$$;

-- F03 · Folio con su prefijo y el número rellenado con ceros: ('GYM-', 7, 4) → GYM-0007.
-- Un número más largo que el ancho se escribe completo, sin cortarse.
CREATE OR REPLACE FUNCTION fn_formatear_folio(p_prefijo varchar, p_numero bigint, p_ancho integer)
    RETURNS varchar
    LANGUAGE sql
    IMMUTABLE
AS $$
    SELECT p_prefijo || CASE
        WHEN length(p_numero::text) >= p_ancho THEN p_numero::text
        ELSE lpad(p_numero::text, p_ancho, '0')
    END;
$$;

-- T01 · Los cobros y las membresías son el historial del gimnasio: no se borran.
CREATE OR REPLACE FUNCTION fn_impedir_borrado()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de % no se borran: son el historial de cobros.', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_pago_sin_borrado
    BEFORE DELETE ON pago
    FOR EACH ROW EXECUTE FUNCTION fn_impedir_borrado();

CREATE TRIGGER trg_membresia_sin_borrado
    BEFORE DELETE ON membresia
    FOR EACH ROW EXECUTE FUNCTION fn_impedir_borrado();
