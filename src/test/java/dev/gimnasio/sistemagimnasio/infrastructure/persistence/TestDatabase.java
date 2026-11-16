package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.StatementException;
import org.junit.jupiter.api.function.Executable;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Un solo PostgreSQL para todas las pruebas; se levanta en el primer uso y Testcontainers lo apaga al terminar. */
final class TestDatabase {
    static final String CHECK_VIOLATION = "23514";
    static final String FOREIGN_KEY_VIOLATION = "23503";
    static final String UNIQUE_VIOLATION = "23505";
    static final String INVALID_ENUM_VALUE = "22P02";
    static final String STRING_TOO_LONG = "22001";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    private static HikariDataSource dataSource;
    private static Jdbi jdbi;

    private TestDatabase() {
    }

    static synchronized HikariDataSource dataSource() {
        if (dataSource == null) {
            POSTGRES.start();
            HikariDataSource connected = Database.connect(
                    new DatabaseConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
            Database.migrate(connected);
            jdbi = Jdbi.create(connected);
            dataSource = connected;
        }
        return dataSource;
    }

    /** Una base vacía en el mismo contenedor, sin migrar; para probar lo que dejan las migraciones. */
    static DatabaseConfig newEmptyDatabase(String name) {
        dataSource();
        jdbi.useHandle(handle -> {
            handle.execute("DROP DATABASE IF EXISTS " + name);
            handle.execute("CREATE DATABASE " + name);
        });
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        return new DatabaseConfig(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    static Jdbi jdbi() {
        dataSource();
        return jdbi;
    }

    static void clean() {
        jdbi().useHandle(handle -> handle.execute("""
                TRUNCATE configuracion, folio_secuencia, usuario, plan_membresia, metodo_pago, socio, pago, membresia
                RESTART IDENTITY CASCADE"""));
    }

    /** Comprueba que la base rechazó la operación por el motivo esperado, no por otro. */
    static void assertRejected(String sqlState, Executable operation) {
        StatementException e = assertThrows(StatementException.class, operation);
        assertEquals(sqlState, ((SQLException) e.getCause()).getSQLState(), e.getMessage());
    }
}
