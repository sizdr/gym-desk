package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.application.usecase.billing.MembershipReceipt;

/** Entrega el recibo impreso. Un fallo de la impresora llega como error técnico. */
public interface ReceiptPrinter {
    void print(MembershipReceipt receipt);
}
