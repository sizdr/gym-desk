package dev.gimnasio.sistemagimnasio.application.usecase.settings;

import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;

/** Cobros ya registrados; con alguno, la moneda ya no se puede cambiar. */
record RecordedOperations(int payments) {

    static RecordedOperations count(MembershipPaymentRepository payments) {
        return new RecordedOperations(payments.count());
    }

    boolean locksCurrency() {
        return payments > 0;
    }
}
