package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.application.Platform;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Corre los casos de uso fuera del hilo de JavaFX y entrega el resultado de vuelta en él.
 * Ningún caso de uso se llama desde un controlador directamente: consultan la base, hashean contraseñas
 * con Argon2 y reintentan transacciones, y cualquiera de esas cosas congelaría la ventana.
 */
public final class Background implements AutoCloseable {
    private static final int WORKERS = 4;

    private final ExecutorService workers;
    private final Executor uiThread;

    Background(ExecutorService workers, Executor uiThread) {
        this.workers = Objects.requireNonNull(workers, "workers");
        this.uiThread = Objects.requireNonNull(uiThread, "uiThread");
    }

    /** Con otros hilos: en las pruebas, un ejecutor propio y {@code Runnable::run} en lugar del hilo de JavaFX. */
    public static Background using(ExecutorService workers, Executor uiThread) {
        return new Background(workers, uiThread);
    }

    public static Background start() {
        var threads = Thread.ofPlatform().daemon().name("ui-worker-", 1).factory();
        return new Background(Executors.newFixedThreadPool(WORKERS, threads), Platform::runLater);
    }

    /** {@code onSuccess} y {@code onFailure} corren en el hilo de JavaFX; solo se llama uno de los dos. */
    public <T> void run(Callable<T> work, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        workers.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Throwable e) {
                uiThread.execute(() -> onFailure.accept(e));
                return;
            }
            uiThread.execute(() -> onSuccess.accept(result));
        });
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
