package dev.gimnasio.sistemagimnasio.application.usecase.settings;

import dev.gimnasio.sistemagimnasio.application.port.out.FolioSeriesRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

public final class GetSettings {
    private final SettingsRepository settings;
    private final FolioSeriesRepository folioSeries;
    private final MembershipPaymentRepository payments;
    private final Authorizer authorizer;

    public GetSettings(SettingsRepository settings, FolioSeriesRepository folioSeries,
                       MembershipPaymentRepository payments, Authorizer authorizer) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.folioSeries = Objects.requireNonNull(folioSeries, "folioSeries");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public SettingsView execute(User actor) {
        authorizer.require(actor, Permission.MANAGE_SETTINGS);
        return SettingsView.of(settings.get(), folioSeries.get(FolioSeries.MEMBERSHIP_PAYMENTS),
                RecordedOperations.count(payments));
    }
}
