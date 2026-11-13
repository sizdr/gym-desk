package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.usecase.plans.SavingsView;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;

/** Un plan que se puede cobrar, con su ahorro frente a pagar mes a mes. */
public record PlanOption(long id, String name, int durationDays, Money price, SavingsView savings) {
}
