package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** El conteo de cobros de {@code PostgresMembershipPaymentRepository}. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresOperationCountsTest {
    private Jdbi jdbi;
    private PostgresMembershipPaymentRepository payments;

    @BeforeEach
    void setUp() {
        jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        payments = new PostgresMembershipPaymentRepository(jdbi);
    }

    private long id(String insert) {
        return jdbi.withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    private void execute(String sql) {
        jdbi.useHandle(handle -> handle.execute(sql));
    }

    @Test
    void cuentaLosCobros() {
        assertEquals(0, payments.count());

        long usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Ana', 'ana', 'h', 'caja')");
        long socio = id("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Luis', '5512345678', '2026-11-01')");
        long metodo = id("INSERT INTO metodo_pago (nombre) VALUES ('Tarjeta')");
        for (int i = 1; i <= 2; i++) {
            execute("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, tipo_cobro) VALUES ('GYM-" + i + "', "
                    + socio + ", " + usuario + ", " + metodo + ", 450, 'primera')");
        }

        assertEquals(2, payments.count());
    }
}
