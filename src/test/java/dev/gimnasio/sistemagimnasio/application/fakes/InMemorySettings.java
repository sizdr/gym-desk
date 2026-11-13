package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;

import java.util.Currency;

public final class InMemorySettings implements SettingsRepository {
    public static final Currency MXN = Currency.getInstance("MXN");

    public GymSettings settings = new GymSettings("Mi gimnasio", null, null, null, null, MXN, 7, 30);
    public int saves;
    public final TransactionGuard guard = new TransactionGuard();

    @Override
    public GymSettings get() {
        guard.check("get");
        return settings;
    }

    @Override
    public void save(GymSettings settings) {
        guard.check("save");
        this.settings = settings;
        saves++;
    }
}
