import dev.gimnasio.sistemagimnasio.infrastructure.config.Environment;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.Database;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.DatabaseConfig;

import java.util.Map;

/**
 * Aplica las migraciones de la aplicación a la base de demostración, con el mismo código que usa la app al
 * arrancar (Database.migrate): así la base demo es igual a una instalación real. Lo usa crear-demo.sh.
 *
 * La URL llega en la variable DEMO_URL; el usuario y la contraseña salen del .env (DB_USER, DB_PASSWORD).
 */
public class MigrarDemo {
    public static void main(String[] args) {
        String url = System.getenv("DEMO_URL");
        if (url == null || url.isBlank()) {
            System.err.println("Falta la variable de entorno DEMO_URL.");
            System.exit(2);
        }
        Map<String, String> env = Environment.load();
        DatabaseConfig config = new DatabaseConfig(url, env.getOrDefault("DB_USER", "gimnasio"), env.get("DB_PASSWORD"));
        try (var dataSource = Database.connect(config)) {
            Database.migrate(dataSource);
        }
    }
}
