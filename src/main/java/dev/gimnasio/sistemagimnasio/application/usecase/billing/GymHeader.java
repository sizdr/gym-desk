package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;

import java.util.Currency;

/** Encabezado de recibos y tickets. {@code logo}, {@code address}, {@code phone} y {@code receiptFooter} son opcionales. */
public record GymHeader(String gymName, byte[] logo, String address, String phone, String receiptFooter,
                        Currency currency) {

    public static GymHeader of(GymSettings settings) {
        return new GymHeader(settings.gymName(), settings.logo(), settings.address(), settings.phone(),
                settings.receiptFooter(), settings.currency());
    }
}
