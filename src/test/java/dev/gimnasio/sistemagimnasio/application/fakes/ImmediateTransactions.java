package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;

import java.util.function.Supplier;

/**
 * Ejecuta el trabajo tal cual; cuenta cuántas transacciones se abrieron y si alguna sigue abierta.
 * Con {@link TransactionGuard#watch}, los repositorios falsos fallan si se usan mientras no hay una abierta.
 */
public final class ImmediateTransactions implements TransactionRunner {
    public int started;
    public boolean open;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        started++;
        open = true;
        try {
            return work.get();
        } finally {
            open = false;
        }
    }
}
