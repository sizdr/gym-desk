package dev.gimnasio.sistemagimnasio.infrastructure.config;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Variables de configuración: las del sistema tienen prioridad sobre las del {@code .env}. */
public final class Environment {
    public static final Path DEFAULT_ENV_FILE = Path.of(".env");

    private Environment() {
    }

    public static Map<String, String> load() {
        return load(DEFAULT_ENV_FILE, System.getenv());
    }

    public static Map<String, String> load(Path envFile, Map<String, String> system) {
        Map<String, String> merged = new HashMap<>(EnvFile.read(envFile));
        merged.putAll(system);
        return Map.copyOf(merged);
    }
}
