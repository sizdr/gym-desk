package dev.gimnasio.sistemagimnasio.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvironmentTest {
    @TempDir
    Path dir;

    @Test
    void lasVariablesDelSistema_ganan_yLasDelArchivoCompletan() throws IOException {
        Path file = dir.resolve(".env");
        Files.writeString(file, "DB_USER=del-archivo\nDB_PASSWORD=del-archivo\n");

        Map<String, String> env = Environment.load(file, Map.of("DB_PASSWORD", "del-sistema", "OTRA", ""));

        assertEquals("del-archivo", env.get("DB_USER"));
        assertEquals("del-sistema", env.get("DB_PASSWORD"));
        assertEquals("", env.get("OTRA"));
    }

    @Test
    void sinArchivo_soloQuedanLasDelSistema() {
        assertEquals(Map.of("DB_PASSWORD", "x"), Environment.load(dir.resolve(".env"), Map.of("DB_PASSWORD", "x")));
    }
}
