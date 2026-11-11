package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.FolioSeriesRepository;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;

import java.util.HashMap;
import java.util.Map;

public final class InMemoryFolioSeries implements FolioSeriesRepository {
    private final Map<String, FolioSeries> bySeries = new HashMap<>();
    public final TransactionGuard guard = new TransactionGuard();

    public InMemoryFolioSeries() {
        put(new FolioSeries(FolioSeries.MEMBERSHIP_PAYMENTS, "GYM-", 4));
    }

    public void put(FolioSeries series) {
        bySeries.put(series.series(), series);
    }

    @Override
    public FolioSeries get(String series) {
        guard.check("get");
        FolioSeries found = bySeries.get(series);
        if (found == null) {
            throw new IllegalStateException("Falta la serie de folios " + series + ".");
        }
        return found;
    }

    @Override
    public void save(FolioSeries series) {
        guard.check("save");
        put(series);
    }
}
