package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.infrastructure.config.Environment;

import java.util.Map;

public record DatabaseConfig(String url, String user, String password) {
    static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/gimnasio";
    static final String DEFAULT_USER = "gimnasio";

    public DatabaseConfig {
        requireValue(url, "DB_URL");
        requireValue(user, "DB_USER");
        requireValue(password, "DB_PASSWORD");
    }

    /**
     * Del sistema y del {@code .env} del directorio de trabajo (gana el sistema).
     * La contraseña no tiene valor por defecto para no dejarla escrita en el código.
     */
    public static DatabaseConfig fromEnvironment() {
        return from(Environment.load());
    }

    static DatabaseConfig from(Map<String, String> env) {
        return new DatabaseConfig(
                env.getOrDefault("DB_URL", DEFAULT_URL),
                env.getOrDefault("DB_USER", DEFAULT_USER),
                env.get("DB_PASSWORD"));
    }

    private static void requireValue(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + variable + ".");
        }
    }

    @Override
    public String toString() {
        return "DatabaseConfig[url=" + url + ", user=" + user + ", password=***]";
    }
}
