package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Registra Instrument Sans (licencia SIL OFL 1.1, ver {@code /fonts/OFL.txt}) antes de cargar la hoja de estilos.
 * Se incluyen cortes estáticos 400, 500, 600 y 700, generados de la fuente variable de Google Fonts: JavaFX no
 * maneja ejes variables. Los cortes 500 y 600 quedan como familias propias («Instrument Sans Medium» y
 * «Instrument Sans SemiBold») porque JavaFX solo distingue normal y negrita al buscar por peso.
 */
public final class Fonts {
    static final String[] FILES = {
            "InstrumentSans-Regular.ttf",
            "InstrumentSans-Medium.ttf",
            "InstrumentSans-SemiBold.ttf",
            "InstrumentSans-Bold.ttf"};

    private static boolean loaded;

    private Fonts() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        for (String file : FILES) {
            try (InputStream in = Fonts.class.getResourceAsStream("/fonts/" + file)) {
                if (in == null || Font.loadFont(in, 14) == null) {
                    throw new IllegalStateException("No se pudo cargar la fuente /fonts/" + file);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        loaded = true;
    }
}
