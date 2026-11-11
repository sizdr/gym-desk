package dev.gimnasio.sistemagimnasio.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvFileTest {
    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        Path file = dir.resolve(".env");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void leeClavesYValores_ignorandoComentariosYLineasVacias() throws IOException {
        Path file = write("""
                # Base de datos

                DB_URL=jdbc:postgresql://localhost:5432/gimnasio
                  DB_USER = gimnasio\s
                """);

        assertEquals(Map.of("DB_URL", "jdbc:postgresql://localhost:5432/gimnasio", "DB_USER", "gimnasio"),
                EnvFile.read(file));
    }

    @Test
    void quitaLasComillasQueRodeanElValor_yConservaLosIgualesDelValor() throws IOException {
        Path file = write("""
                A="con espacios"
                B='simples'
                C=a=b=c
                D="sin cerrar
                E=
                """);

        assertEquals(Map.of("A", "con espacios", "B", "simples", "C", "a=b=c", "D", "\"sin cerrar", "E", ""),
                EnvFile.read(file));
    }

    @Test
    void siNoExisteElArchivo_noHayVariables() {
        assertEquals(Map.of(), EnvFile.read(dir.resolve("no-existe.env")));
    }

    @Test
    void unaLineaSinClave_fallaConSuNumero_sinMostrarSuContenido() throws IOException {
        Path file = write("DB_USER=gimnasio\nesto-es-secreto\n");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> EnvFile.read(file));

        assertEquals("Línea 2 de .env no es CLAVE=valor.", e.getMessage());
        assertFalse(e.getMessage().contains("secreto"));
        assertThrows(IllegalStateException.class, () -> EnvFile.read(write("=valor\n")));
    }
}
