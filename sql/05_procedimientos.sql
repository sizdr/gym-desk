-- =====================================================================================================
-- 05_procedimientos.sql · Procedimiento almacenado del módulo de cobro
--
-- Usa las funciones de 04_funciones.sql. La aplicación tiene el mismo procedimiento en la migración
-- V6__funciones_y_procedimientos.sql.
-- =====================================================================================================

-- P01 · Registrar el cobro de una membresía en una sola operación, con las reglas del gimnasio:
--   1. Si la operación ya se registró (mismo p_operacion_id), devuelve ese cobro y no cobra otra vez.
--   2. El socio debe existir; se bloquea su fila para que dos cobros simultáneos no calculen lo mismo.
--   3. El plan y el método de pago deben existir y estar activos.
--   4. En efectivo, lo recibido es obligatorio y debe cubrir el precio; en otro método se ignora.
--   5. El tipo de cobro sale del estado del socio (fn_estado_socio) y decide el inicio de la vigencia:
--        SIN_MEMBRESIA → primera: p_inicio, o hoy; no antes de la fecha de alta.
--        VIGENTE o POR_VENCER → renovación anticipada: empieza el día en que termina la vigente.
--        VENCIDA → renovación vencida: empieza hoy.  BAJA → reactivación: empieza hoy.
--   6. Toma el siguiente folio de la serie COBRO (UPDATE ... RETURNING, nunca MAX + 1).
--   7. Inserta el pago con el precio actual del plan y su membresía (vencimiento con fn_fecha_vencimiento).
-- Devuelve el id y el folio del pago en p_pago_id y p_folio.
CREATE OR REPLACE PROCEDURE sp_registrar_cobro(
    IN p_socio_id          bigint,
    IN p_plan_id           bigint,
    IN p_metodo_pago_id    bigint,
    IN p_usuario_id        bigint,
    IN p_efectivo_recibido numeric,
    IN p_operacion_id      varchar,
    IN p_inicio            date,
    INOUT p_pago_id        bigint,
    INOUT p_folio          varchar)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_hoy         date := CURRENT_DATE;
    v_fecha_alta  date;
    v_precio      numeric(12,2);
    v_plan_activo boolean;
    v_es_efectivo boolean;
    v_metodo_act  boolean;
    v_estado      varchar;
    v_tipo        tipo_cobro;
    v_inicio      date;
    v_efectivo    numeric(12,2);
    v_prefijo     varchar;
    v_numero      bigint;
    v_ancho       integer;
BEGIN
    -- 1. Operación repetida.
    IF p_operacion_id IS NOT NULL THEN
        SELECT id, folio INTO p_pago_id, p_folio FROM pago WHERE operacion_id = p_operacion_id;
        IF FOUND THEN
            RETURN;
        END IF;
    END IF;

    -- 2. Socio.
    SELECT fecha_alta INTO v_fecha_alta FROM socio WHERE id = p_socio_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'El socio no existe.';
    END IF;

    -- 3. Plan y método de pago.
    SELECT precio, activo INTO v_precio, v_plan_activo FROM plan_membresia WHERE id = p_plan_id;
    IF NOT FOUND OR NOT v_plan_activo THEN
        RAISE EXCEPTION 'Ese plan no existe o está inactivo; elige otro.';
    END IF;
    SELECT es_efectivo, activo INTO v_es_efectivo, v_metodo_act FROM metodo_pago WHERE id = p_metodo_pago_id;
    IF NOT FOUND OR NOT v_metodo_act THEN
        RAISE EXCEPTION 'Ese método de pago no existe o ya no está disponible.';
    END IF;

    -- 4. Efectivo.
    IF v_es_efectivo THEN
        IF p_efectivo_recibido IS NULL THEN
            RAISE EXCEPTION 'Escribe cuánto efectivo recibiste.';
        END IF;
        IF p_efectivo_recibido < v_precio THEN
            RAISE EXCEPTION 'El efectivo recibido no cubre el total.';
        END IF;
        v_efectivo := p_efectivo_recibido;
    END IF;

    -- 5. Tipo de cobro e inicio de la vigencia.
    v_estado := fn_estado_socio(p_socio_id, v_hoy);
    CASE v_estado
        WHEN 'SIN_MEMBRESIA' THEN
            v_tipo := 'primera';
            v_inicio := COALESCE(p_inicio, v_hoy);
            IF v_inicio < v_fecha_alta THEN
                RAISE EXCEPTION 'El inicio no puede ser anterior a la fecha de alta (%).', to_char(v_fecha_alta, 'DD/MM/YYYY');
            END IF;
        WHEN 'VIGENTE', 'POR_VENCER' THEN
            v_tipo := 'renovacion_anticipada';
            SELECT MAX(fecha_vencimiento) INTO v_inicio FROM membresia WHERE socio_id = p_socio_id;
        WHEN 'VENCIDA' THEN
            v_tipo := 'renovacion_vencida';
            v_inicio := v_hoy;
        ELSE
            v_tipo := 'reactivacion';
            v_inicio := v_hoy;
    END CASE;

    -- 6. Siguiente folio.
    UPDATE folio_secuencia SET ultimo = ultimo + 1 WHERE serie = 'COBRO'
        RETURNING prefijo, ultimo, ancho INTO v_prefijo, v_numero, v_ancho;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Falta la serie de folios COBRO.';
    END IF;
    p_folio := fn_formatear_folio(v_prefijo, v_numero, v_ancho);

    -- 7. Pago y membresía.
    INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, efectivo_recibido, tipo_cobro, operacion_id)
    VALUES (p_folio, p_socio_id, p_usuario_id, p_metodo_pago_id, v_precio, v_efectivo, v_tipo, p_operacion_id)
    RETURNING id INTO p_pago_id;

    INSERT INTO membresia (socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento)
    VALUES (p_socio_id, p_plan_id, p_pago_id, v_inicio, fn_fecha_vencimiento(v_inicio, p_plan_id));
END;
$$;
