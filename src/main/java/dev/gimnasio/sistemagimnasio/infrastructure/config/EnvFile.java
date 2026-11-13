package dev.gimnasio.sistemagimnasio.infrastructure.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lee un archivo {@code .env}: líneas {@code CLAVE=valor}, con comentarios {@code #} y líneas vacías. */
public final class EnvFile {
    private EnvFile() {
    }

    /** @return las variables del archivo, o un mapa vacío si no existe */
    public static Map<String, String> read(Path file) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + file.getFileName() + ".", e);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            String key = equals < 0 ? "" : line.substring(0, equals).strip();
            if (key.isEmpty()) {
                // No se muestra la línea: puede contener un secreto.
                throw new IllegalStateException("Línea " + (i + 1) + " de " + file.getFileName()
                        + " no es CLAVE=valor.");
            }
            values.put(key, unquote(line.substring(equals + 1).strip()));
        }
        return Map.copyOf(values);
    }

    private static String unquote(String value) {
        boolean quoted = value.length() >= 2
                && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"));
        return quoted ? value.substring(1, value.length() - 1) : value;
    }
}
