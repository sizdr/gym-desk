package dev.gimnasio.sistemagimnasio.application.usecase.settings;

import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;
import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;

import java.util.Currency;

/**
 * {@code currencyLocked}, {@code paymentsCount} y {@code folioExample} son de solo lectura.
 * {@code folioExample} es un folio de cobro con el prefijo y el ancho actuales, por ejemplo GYM-0001.
 */
public record SettingsView(
        String gymName,
        String address,
        String phone,
        String receiptFooter,
        byte[] logo,
        Currency currency,
        int warningDays,
        int autoDeactivationDays,
        String folioPrefix,
        boolean currencyLocked,
        int paymentsCount,
        String folioExample) {

    static SettingsView of(GymSettings settings, FolioSeries series, RecordedOperations operations) {
        return new SettingsView(
                settings.gymName(),
                settings.address(),
                settings.phone(),
                settings.receiptFooter(),
                settings.logo(),
                settings.currency(),
                settings.warningDays(),
                settings.autoDeactivationDays(),
                series.prefix(),
                operations.locksCurrency(),
                operations.payments(),
                series.format(1).value());
    }
}
