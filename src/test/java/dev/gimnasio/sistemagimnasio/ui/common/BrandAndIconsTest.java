package dev.gimnasio.sistemagimnasio.ui.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandAndIconsTest {

    @ParameterizedTest
    @CsvSource({"Mi gimnasio,MG", "Gimnasio Fuerza Total,GF", "Fuerza,FU", "ñu,ÑU", "'  ',G"})
    void initialsFromTheGymName(String name, String expected) {
        assertEquals(expected, BrandLogo.initials(name));
    }

    @Test
    void everyIconTheUiUsesExists() {
        List<String> used = List.of("tablero", "tarjeta", "carrito", "usuarios", "calendario", "paquete",
                "portapapeles", "escudo", "ajustes", "buscar", "salir", "teclado", "alerta", "info", "sol", "luna");
        assertTrue(Icons.names().containsAll(used), "faltan: " + used.stream().filter(n -> !Icons.names().contains(n)).toList());
    }

    @Test
    void fontAndIconLicensesAreBundled() {
        assertNotNull(BrandAndIconsTest.class.getResource("/fonts/OFL.txt"));
        assertNotNull(BrandAndIconsTest.class.getResource("/icons/LICENSE-lucide.txt"));
        for (String file : Fonts.FILES) {
            assertNotNull(BrandAndIconsTest.class.getResource("/fonts/" + file), file);
        }
    }
}
