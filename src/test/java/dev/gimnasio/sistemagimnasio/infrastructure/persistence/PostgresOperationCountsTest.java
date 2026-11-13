package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Los conteos de {@code PostgresMembershipPaymentRepository} y {@code PostgresSaleRepository}. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresOperationCountsTest {
    private Jdbi jdbi;
    private PostgresMembershipPaymentRepository payments;
    private PostgresSaleRepository sales;

    @BeforeEach
    void setUp() {
        jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        payments = new PostgresMembershipPaymentRepository(jdbi);
        sales = new PostgresSaleRepository(jdbi);
    }

    private long id(String insert) {
        return jdbi.withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    private void execute(String sql) {
        jdbi.useHandle(handle -> handle.execute(sql));
    }

    @Test
    void cuentaCobrosYVentasPorSeparado() {
        assertEquals(0, payments.count());
        assertEquals(0, sales.count());

        long usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Ana', 'ana', 'h', 'caja')");
        long socio = id("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Luis', '5512345678', '2026-11-01')");
        long metodo = id("INSERT INTO metodo_pago (nombre) VALUES ('Tarjeta')");
        for (int i = 1; i <= 2; i++) {
            execute("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, tipo_cobro) VALUES ('GYM-" + i + "', "
                    + socio + ", " + usuario + ", " + metodo + ", 450, 'primera')");
        }
        execute("INSERT INTO venta (folio, metodo_pago_id, total, usuario_id) VALUES ('GYM-V-1', "
                + metodo + ", 18.50, " + usuario + ")");

        assertEquals(2, payments.count());
        assertEquals(1, sales.count());
    }
}
