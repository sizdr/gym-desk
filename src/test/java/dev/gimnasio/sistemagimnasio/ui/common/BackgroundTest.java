package dev.gimnasio.sistemagimnasio.ui.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BackgroundTest {

    @Test
    void runsWorkOffTheCallerThreadAndDeliversTheResult() throws Exception {
        Thread caller = Thread.currentThread();
        var workThread = new CompletableFuture<Thread>();
        var result = new CompletableFuture<String>();

        try (var background = new Background(Executors.newSingleThreadExecutor(), Runnable::run)) {
            background.run(() -> {
                workThread.complete(Thread.currentThread());
                return "listo";
            }, result::complete, result::completeExceptionally);

            assertEquals("listo", result.get(2, TimeUnit.SECONDS));
            assertNotEquals(caller, workThread.get());
        }
    }

    @Test
    void deliversFailuresInsteadOfLosingThem() throws Exception {
        var error = new IllegalStateException("base caída");
        var failure = new CompletableFuture<Throwable>();

        try (var background = new Background(Executors.newSingleThreadExecutor(), Runnable::run)) {
            background.run(() -> {
                throw error;
            }, ignored -> failure.complete(null), failure::complete);

            assertSame(error, failure.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void callbacksGoThroughTheUiExecutor() throws Exception {
        var uiThreadName = new CompletableFuture<String>();
        var uiThread = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "fx-simulado"));

        try (var background = new Background(Executors.newSingleThreadExecutor(), uiThread)) {
            background.run(() -> 1, ignored -> uiThreadName.complete(Thread.currentThread().getName()),
                    uiThreadName::completeExceptionally);

            assertEquals("fx-simulado", uiThreadName.get(2, TimeUnit.SECONDS));
        } finally {
            uiThread.shutdownNow();
        }
    }
}
