package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.ReceiptPrinter;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.MembershipReceipt;

import java.util.ArrayList;
import java.util.List;

public final class RecordingReceiptPrinter implements ReceiptPrinter {
    public final List<MembershipReceipt> printed = new ArrayList<>();

    @Override
    public void print(MembershipReceipt receipt) {
        printed.add(receipt);
    }
}
