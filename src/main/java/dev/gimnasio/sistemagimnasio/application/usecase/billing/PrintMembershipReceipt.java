package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.port.out.ReceiptPrinter;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/** Imprime el mismo documento que devuelve {@link GetMembershipReceipt}; fuera de cualquier transacción. */
public final class PrintMembershipReceipt {
    private final GetMembershipReceipt getReceipt;
    private final ReceiptPrinter printer;

    public PrintMembershipReceipt(GetMembershipReceipt getReceipt, ReceiptPrinter printer) {
        this.getReceipt = Objects.requireNonNull(getReceipt, "getReceipt");
        this.printer = Objects.requireNonNull(printer, "printer");
    }

    public void execute(User actor, long paymentId) {
        printer.print(getReceipt.execute(actor, paymentId));
    }
}
