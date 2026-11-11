package dev.gimnasio.sistemagimnasio.application.fakes;

import java.util.Objects;

/**
 * Hace fallar a un repositorio falso si se usa fuera de una transacción de {@link ImmediateTransactions}.
 * Está apagado hasta que la prueba llama a {@link #watch}: los casos que solo leen no abren transacción.
 */
public final class TransactionGuard {
    private ImmediateTransactions transactions;

    public void watch(ImmediateTransactions transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    void check(String operation) {
        if (transactions != null && !transactions.open) {
            throw new AssertionError(operation + " se llamó fuera de una transacción.");
        }
    }
}
