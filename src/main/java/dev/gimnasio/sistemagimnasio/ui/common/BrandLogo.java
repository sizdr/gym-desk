package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Logotipo del gimnasio o, si no tiene (o es SVG, que JavaFX no dibuja), sus iniciales sobre el acento. */
public final class BrandLogo {
    private BrandLogo() {
    }

    /** @param size 36 (menú) o 48 (acceso) */
    public static StackPane create(String gymName, byte[] logo, int size) {
        StackPane box = new StackPane();
        box.getStyleClass().add("logo");
        if (size == 48) {
            box.getStyleClass().add("logo-48");
        }
        box.setAccessibleText("Logotipo de " + gymName);

        Image image = decode(logo);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            Rectangle clip = new Rectangle(size, size);
            double radius = size == 48 ? 26 : 20;
            clip.setArcWidth(radius);
            clip.setArcHeight(radius);
            view.setClip(clip);
            box.getStyleClass().add("con-imagen");
            box.getChildren().add(view);
        } else {
            box.getChildren().add(new Label(initials(gymName)));
        }
        return box;
    }

    /** «Mi gimnasio» → «MG»; «Fuerza» → «FU». También para el avatar de la sesión. */
    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "G";
        }
        String[] words = name.strip().split("\\s+");
        String result = words.length >= 2
                ? Arrays.stream(words).limit(2).map(w -> w.substring(0, 1)).collect(Collectors.joining())
                : words[0].substring(0, Math.min(2, words[0].length()));
        return result.toUpperCase(Locale.forLanguageTag("es-MX"));
    }

    private static Image decode(byte[] logo) {
        if (logo == null || logo.length == 0) {
            return null;
        }
        Image image = new Image(new ByteArrayInputStream(logo));
        return image.isError() || image.getWidth() <= 0 ? null : image;
    }
}
