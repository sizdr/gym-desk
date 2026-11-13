package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.CHECK_VIOLATION;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.FOREIGN_KEY_VIOLATION;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.INVALID_ENUM_VALUE;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.STRING_TOO_LONG;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.UNIQUE_VIOLATION;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.assertRejected;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class SchemaTest {
    private Jdbi jdbi;
    private long usuario;
    private long socio;
    private long otroSocio;
    private long plan;
    private long efectivo;
    private long producto;
    private long motivo;

    @BeforeEach
    void setUp() {
        jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Ana', 'ana', 'h', 'caja')");
        socio = id("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Luis', '5512345678', '2026-11-01')");
        otroSocio = id("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Eva', '5587654321', '2026-11-01')");
        plan = id("INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES ('Mensual', 30, 450)");
        efectivo = id("INSERT INTO metodo_pago (nombre, es_efectivo) VALUES ('Efectivo', true)");
        long categoria = id("INSERT INTO categoria_producto (nombre) VALUES ('Bebidas')");
        producto = id("INSERT INTO producto (clave, nombre, categoria_id, precio, minimo, existencia) "
                + "VALUES ('AGUA-1L', 'Agua 1 L', " + categoria + ", 18.50, 5, 40)");
        motivo = id("INSERT INTO motivo_movimiento (nombre) VALUES ('Compra')");
    }

    private long id(String insert) {
        return jdbi.withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    private void execute(String sql) {
        jdbi.useHandle(handle -> handle.execute(sql));
    }

    private long pago(long socioId, String folio) {
        return id("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, tipo_cobro) VALUES ('" + folio + "', "
                + socioId + ", " + usuario + ", " + efectivo + ", 450, 'primera')");
    }

    // ---------- Configuración ----------

    @Test
    void configuracion_admiteUnaSolaFila() {
        execute("INSERT INTO configuracion (nombre_gimnasio) VALUES ('Gym')");
        assertRejected(UNIQUE_VIOLATION, () -> execute("INSERT INTO configuracion (nombre_gimnasio) VALUES ('Otro')"));
        assertRejected(CHECK_VIOLATION, () -> execute("INSERT INTO configuracion (id, nombre_gimnasio) VALUES (2, 'Otro')"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 61})
    void configuracion_diasDeAvisoFueraDe1a60_seRechazan(int dias) {
        assertRejected(CHECK_VIOLATION, () -> execute("INSERT INTO configuracion (nombre_gimnasio, dias_aviso) VALUES ('Gym', " + dias + ")"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 366})
    void configuracion_plazoDeBajaFueraDe1a365_seRechaza(int dias) {
        assertRejected(CHECK_VIOLATION, () -> execute("INSERT INTO configuracion (nombre_gimnasio, dias_baja_automatica) VALUES ('Gym', " + dias + ")"));
    }

    @Test
    void configuracion_admiteLosLimites() {
        assertDoesNotThrow(() -> execute("INSERT INTO configuracion (nombre_gimnasio, dias_aviso, dias_baja_automatica) VALUES ('Gym', 60, 365)"));
        assertDoesNotThrow(() -> execute("UPDATE configuracion SET dias_aviso = 1, dias_baja_automatica = 1"));
    }

    @Test
    void configuracion_leyendaDeMasDe160Caracteres_seRechaza() {
        assertRejected(STRING_TOO_LONG, () -> execute("INSERT INTO configuracion (nombre_gimnasio, leyenda_recibo) VALUES ('Gym', '" + "x".repeat(161) + "')"));
        assertDoesNotThrow(() -> execute("INSERT INTO configuracion (nombre_gimnasio, leyenda_recibo) VALUES ('Gym', '" + "x".repeat(160) + "')"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "gym-", "GYM_", "GYM-COBRO"})
    void prefijoDeFolioInvalido_seRechaza(String prefijo) {
        assertRejected(CHECK_VIOLATION, () -> execute("INSERT INTO folio_secuencia (serie, prefijo) VALUES ('COBRO', '" + prefijo + "')"));
    }

    @Test
    void metodoDePagoRepetido_aunqueCambienLasMayusculas_seRechaza() {
        assertRejected(UNIQUE_VIOLATION, () -> execute("INSERT INTO metodo_pago (nombre) VALUES ('EFECTIVO')"));
    }

    // ---------- Planes ----------

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void planConPrecioCeroONegativo_seRechaza(String precio) {
        assertRejected(CHECK_VIOLATION, () -> execute(
                "INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES ('Gratis', 30, " + precio + ")"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 731})
    void planConDuracionFueraDe1a730_seRechaza(int dias) {
        assertRejected(CHECK_VIOLATION, () -> execute(
                "INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES ('Raro', " + dias + ", 100)"));
    }

    @Test
    void planDe730Dias_seAcepta() {
        assertDoesNotThrow(() -> execute("INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES ('Bianual', 730, 7000)"));
    }

    @Test
    void planQueSeReferenciaASiMismo_seRechaza() {
        assertRejected(CHECK_VIOLATION, () -> execute(
                "UPDATE plan_membresia SET referencia_mensual_id = id WHERE id = " + plan));
    }

    @Test
    void referenciaAUnPlanInexistente_seRechaza_yAUnoExistenteSeAcepta() {
        assertRejected(FOREIGN_KEY_VIOLATION, () -> execute(
                "INSERT INTO plan_membresia (nombre, duracion_dias, precio, referencia_mensual_id) VALUES ('Anual', 365, 3800, 999)"));
        assertDoesNotThrow(() -> execute(
                "INSERT INTO plan_membresia (nombre, duracion_dias, precio, referencia_mensual_id) VALUES ('Anual', 365, 3800, " + plan + ")"));
    }

    @Test
    void planRepetido_aunqueCambienLasMayusculas_seRechaza() {
        assertRejected(UNIQUE_VIOLATION, () -> execute(
                "INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES ('MENSUAL', 30, 500)"));
    }

    // ---------- Socios ----------

    @ParameterizedTest
    @ValueSource(strings = {"551234567", "55-1234567", "55123456ab"})
    void telefonoQueNoSonDiezDigitos_seRechaza(String telefono) {
        assertRejected(CHECK_VIOLATION, () -> execute(
                "INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('X', '" + telefono + "', '2026-11-01')"));
    }

    @Test
    void correoRepetido_aunqueCambienLasMayusculas_seRechaza_peroVariosSinCorreoSi() {
        execute("UPDATE socio SET correo = 'luis@gym.mx' WHERE id = " + socio);
        assertRejected(UNIQUE_VIOLATION, () -> execute("UPDATE socio SET correo = 'LUIS@gym.mx' WHERE id = " + otroSocio));
        assertDoesNotThrow(() -> execute("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Sin correo', '5500000000', '2026-11-01')"));
    }

    // ---------- Pagos y membresías ----------

    @Test
    void elMismoOperationId_noRegistraDosCobros_peroVariosSinIdSi() {
        execute("UPDATE pago SET operacion_id = 'op-1' WHERE id = " + pago(socio, "GYM-1"));
        long otro = pago(socio, "GYM-2");
        assertRejected(UNIQUE_VIOLATION, () -> execute("UPDATE pago SET operacion_id = 'op-1' WHERE id = " + otro));
        assertDoesNotThrow(() -> pago(socio, "GYM-3"));
    }

    @Test
    void unTipoDeCobroDesconocido_seRechaza() {
        assertRejected(INVALID_ENUM_VALUE, () -> execute("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, tipo_cobro) "
                + "VALUES ('GYM-9', " + socio + ", " + usuario + ", " + efectivo + ", 450, 'regalo')"));
    }

    @Test
    void efectivoRecibido_menorAlMonto_seRechaza_yNuloSePermite() {
        assertRejected(CHECK_VIOLATION, () -> execute("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, efectivo_recibido, tipo_cobro) "
                + "VALUES ('GYM-1', " + socio + ", " + usuario + ", " + efectivo + ", 450, 449.99, 'primera')"));
        assertDoesNotThrow(() -> pago(socio, "GYM-2"));
    }

    @Test
    void membresiaDeOtroSocio_queElQuePago_seRechaza() {
        long pagoDeLuis = pago(socio, "GYM-1");
        assertRejected(FOREIGN_KEY_VIOLATION, () -> execute("INSERT INTO membresia (socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento) "
                + "VALUES (" + otroSocio + ", " + plan + ", " + pagoDeLuis + ", '2026-11-01', '2026-12-01')"));
    }

    @Test
    void unPago_generaUnaSolaMembresia_yConVigenciaPositiva() {
        long pago = pago(socio, "GYM-1");
        String insert = "INSERT INTO membresia (socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento) VALUES ("
                + socio + ", " + plan + ", " + pago + ", ";
        assertRejected(CHECK_VIOLATION, () -> execute(insert + "'2026-11-01', '2026-11-01')"));
        execute(insert + "'2026-11-01', '2026-12-01')");
        assertRejected(UNIQUE_VIOLATION, () -> execute(insert + "'2026-12-01', '2026-12-31')"));
    }

    // ---------- Ventas e inventario ----------

    private long venta(String folio) {
        return id("INSERT INTO venta (folio, metodo_pago_id, total, usuario_id) VALUES ('" + folio + "', "
                + efectivo + ", 37, " + usuario + ")");
    }

    @Test
    void elImporteDelDetalle_loCalculaLaBase() {
        long venta = venta("GYM-V-1");
        long detalle = id("INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario) VALUES ("
                + venta + ", " + producto + ", 3, 18.50)");
        assertEquals(new BigDecimal("55.50"), jdbi.withHandle(h -> h.createQuery(
                "SELECT importe FROM detalle_venta WHERE id = " + detalle).mapTo(BigDecimal.class).one()));
    }

    @Test
    void elMismoProductoDosVecesEnUnaVenta_seRechaza() {
        long venta = venta("GYM-V-1");
        String insert = "INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario) VALUES ("
                + venta + ", " + producto + ", 1, 18.50)";
        execute(insert);
        assertRejected(UNIQUE_VIOLATION, () -> execute(insert));
    }

    private String movimiento(String tipo, int cantidad, String motivoId, String detalleId) {
        return "INSERT INTO movimiento_inventario (producto_id, tipo, cantidad, existencia_resultante, "
                + "motivo_movimiento_id, detalle_venta_id, usuario_id) VALUES (" + producto + ", '" + tipo + "', "
                + cantidad + ", 40, " + motivoId + ", " + detalleId + ", " + usuario + ")";
    }

    @Test
    void elSignoDeLaCantidad_debeCoincidirConElTipoDeMovimiento() {
        String m = String.valueOf(motivo);
        assertRejected(CHECK_VIOLATION, () -> execute(movimiento("entrada", -5, m, "NULL")));
        assertRejected(CHECK_VIOLATION, () -> execute(movimiento("salida", 5, m, "NULL")));
        assertRejected(CHECK_VIOLATION, () -> execute(movimiento("ajuste", 0, m, "NULL")));
        assertDoesNotThrow(() -> execute(movimiento("entrada", 5, m, "NULL")));
        assertDoesNotThrow(() -> execute(movimiento("salida", -5, m, "NULL")));
        assertDoesNotThrow(() -> execute(movimiento("ajuste", -2, m, "NULL")));
        assertDoesNotThrow(() -> execute(movimiento("ajuste", 2, m, "NULL")));
    }

    @Test
    void unMovimientoSinMotivoNiVenta_seRechaza() {
        assertRejected(CHECK_VIOLATION, () -> execute(movimiento("entrada", 5, "NULL", "NULL")));
    }
}
