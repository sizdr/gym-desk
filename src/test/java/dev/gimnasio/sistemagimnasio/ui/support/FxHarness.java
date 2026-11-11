package dev.gimnasio.sistemagimnasio.ui.support;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Arranca JavaFX en las pruebas, corre código en su hilo, simula teclas y guarda capturas de una escena. */
public final class FxHarness {
    private static boolean started;

    private FxHarness() {
    }

    public static synchronized void start() throws InterruptedException {
        if (started) {
            return;
        }
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        Platform.setImplicitExit(false);
        if (!ready.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX no arrancó");
        }
        started = true;
    }

    public static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    /** Para condiciones de {@link #waitUntil}, que ya corren en el hilo de JavaFX. */
    public static <T> T onFxUnchecked(java.util.function.Supplier<T> work) {
        return work.get();
    }

    public static void runFx(Runnable work) throws Exception {
        onFx(() -> {
            work.run();
            return null;
        });
    }

    /** Espera a que la condición, evaluada en el hilo de JavaFX, se cumpla; deja pasar un pulso para el CSS. */
    public static void waitUntil(String what, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (onFx(condition::getAsBoolean)) {
                settle();
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("No se cumplió a tiempo: " + what);
    }

    /** Deja que JavaFX aplique CSS y haga el layout de los cambios pendientes. */
    public static void settle() throws Exception {
        for (int i = 0; i < 3; i++) {
            runFx(() -> {
            });
            Thread.sleep(30);
        }
    }

    /** Ajusta la ventana para que la escena mida exactamente {@code width × height}. */
    public static void resizeScene(Stage stage, double width, double height) throws Exception {
        runFx(() -> {
            stage.setMinWidth(0);
            stage.setMinHeight(0);
        });
        for (int attempt = 0; attempt < 5; attempt++) {
            runFx(() -> {
                Scene scene = stage.getScene();
                stage.setWidth(stage.getWidth() + width - scene.getWidth());
                stage.setHeight(stage.getHeight() + height - scene.getHeight());
            });
            settle();
            boolean exact = onFx(() -> stage.getScene().getWidth() == width && stage.getScene().getHeight() == height);
            if (exact) {
                return;
            }
        }
        throw new AssertionError("La escena no quedó en " + width + "×" + height + ": "
                + onFx(() -> stage.getScene().getWidth() + "×" + stage.getScene().getHeight()));
    }

    /** Simula una tecla sobre el control con el foco, como la entregaría el sistema. */
    public static void press(Scene scene, KeyCode key, boolean shift) throws Exception {
        runFx(() -> {
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, shift, false, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, shift, false, false, false));
        });
        settle();
    }

    /**
     * Como {@link #press}, pero sin esperar: para teclas que abren un diálogo modal ({@code showAndWait}
     * deja el hilo de JavaFX en un ciclo anidado hasta que se cierra).
     */
    public static void pressLater(Scene scene, KeyCode key) {
        Platform.runLater(() -> {
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, false, false, false, false));
        });
    }

    /** Escribe un carácter sobre el control con el foco, sin esperar (ver {@link #pressLater}). */
    public static void typeLater(Scene scene, String character) {
        Platform.runLater(() -> {
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, character, "", KeyCode.UNDEFINED,
                    false, false, false, false));
        });
    }

    /** Id del control con el foco, o su clase si no tiene id. */
    public static String focusOwner(Scene scene) throws Exception {
        return onFx(() -> {
            Node owner = scene.getFocusOwner();
            if (owner == null) {
                return "(nadie)";
            }
            return owner.getId() != null ? owner.getId() : owner.getClass().getSimpleName();
        });
    }

    public static Path capture(Scene scene, Path file) throws Exception {
        WritableImage image = onFx(() -> scene.snapshot(null));
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(out, "png", file.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar " + file, e);
        }
        return file;
    }
}
