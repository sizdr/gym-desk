package dev.gimnasio.sistemagimnasio;

import com.zaxxer.hikari.HikariDataSource;
import dev.gimnasio.sistemagimnasio.infrastructure.bootstrap.InitialAdminSetup;
import dev.gimnasio.sistemagimnasio.infrastructure.config.Environment;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.Database;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.DatabaseConfig;
import dev.gimnasio.sistemagimnasio.ui.GymDeskUi;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import javafx.application.Application;
import javafx.stage.Stage;
import org.jdbi.v3.core.Jdbi;

import java.time.Clock;
import java.util.concurrent.CancellationException;

/**
 * Arranca la UI y le entrega la forma de conectarse a la base. La conexión corre en segundo plano
 * desde la pantalla de arranque, que muestra el error y permite reintentar si PostgreSQL no responde.
 *
 * <p>El bloqueo solo protege {@link #dataSource} y {@link #stopping}; nunca se mantiene mientras se habla
 * con la base, para que cerrar la ventana no espere a una conexión que no responde.
 */
public final class App extends Application {
    private final Object lock = new Object();
    private HikariDataSource dataSource;
    private boolean stopping;
    private GymDeskUi ui;

    @Override
    public void start(Stage stage) {
        ui = new GymDeskUi(stage, this::connect);
        ui.start();
    }

    /** Se llama fuera del hilo de JavaFX; con cada reintento cierra la conexión anterior. */
    private UseCases connect() {
        HikariDataSource previous;
        synchronized (lock) {
            requireRunning();
            previous = dataSource;
            dataSource = null;
        }
        if (previous != null) {
            previous.close();
        }

        HikariDataSource connected = Database.connect(DatabaseConfig.fromEnvironment());
        try {
            Database.migrate(connected);
            InitialAdminSetup.ensure(Environment.load(), Wiring.createInitialAdmin(Jdbi.create(connected)));
        } catch (RuntimeException e) {
            connected.close();
            throw e;
        }

        boolean discard;
        synchronized (lock) {
            discard = stopping;
            if (!discard) {
                dataSource = connected;
            }
        }
        if (discard) {
            connected.close();
            throw new CancellationException("La app se cerró mientras se conectaba.");
        }
        return Wiring.useCases(Jdbi.create(connected), Clock.systemDefaultZone());
    }

    /** No espera a una conexión en curso: esa conexión se cierra sola al terminar (ver {@link #connect}). */
    @Override
    public void stop() {
        HikariDataSource current;
        synchronized (lock) {
            stopping = true;
            current = dataSource;
            dataSource = null;
        }
        if (ui != null) {
            ui.close();
        }
        if (current != null) {
            current.close();
        }
    }

    private void requireRunning() {
        if (stopping) {
            throw new CancellationException("La app se está cerrando.");
        }
    }
}
