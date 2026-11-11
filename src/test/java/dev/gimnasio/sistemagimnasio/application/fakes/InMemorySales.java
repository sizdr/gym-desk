package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.SaleRepository;

public final class InMemorySales implements SaleRepository {
    public int count;
    public final TransactionGuard guard = new TransactionGuard();

    @Override
    public int count() {
        guard.check("count");
        return count;
    }
}
