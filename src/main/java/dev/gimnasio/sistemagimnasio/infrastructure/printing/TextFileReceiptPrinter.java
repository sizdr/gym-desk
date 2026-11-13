package dev.gimnasio.sistemagimnasio.infrastructure.printing;

import dev.gimnasio.sistemagimnasio.application.port.out.ReceiptPrinter;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.GymHeader;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.MembershipReceipt;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Guarda cada recibo como texto en {@code <carpeta>/<folio>.txt}. Sirve mientras no se elige la impresora del
 * mostrador; el archivo se puede abrir e imprimir desde Windows. Reimprimir un folio reemplaza su archivo.
 */
public final class TextFileReceiptPrinter implements ReceiptPrinter {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Path directory;

    public TextFileReceiptPrinter(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    @Override
    public void print(MembershipReceipt receipt) {
        Path file = directory.resolve(receipt.payment().folio() + ".txt");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, render(receipt), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el recibo " + receipt.payment().folio() + ".", e);
        }
    }

    static String render(MembershipReceipt receipt) {
        GymHeader header = receipt.header();
        PaymentRow payment = receipt.payment();
        String currency = header.currency().getCurrencyCode();
        List<String> lines = new ArrayList<>();
        lines.add(header.gymName());
        addIfPresent(lines, header.address());
        addIfPresent(lines, header.phone());
        lines.add("");
        lines.add("Recibo de membresía");
        lines.add("Folio: " + payment.folio());
        lines.add("Fecha: " + payment.dateTime().format(DAY_TIME));
        lines.add("Socio: " + payment.memberName());
        lines.add("Plan: " + payment.planName() + " (" + receipt.durationDays() + " días)");
        lines.add("Vigencia: " + payment.start().format(DAY) + " al " + payment.end().format(DAY));
        lines.add("Tipo: " + kind(payment.kind()));
        lines.add("Método: " + payment.paymentMethodName());
        lines.add("Total: " + money(payment.amount(), currency));
        if (receipt.cashReceived() != null) {
            lines.add("Recibido: " + money(receipt.cashReceived(), currency));
            lines.add("Cambio: " + money(receipt.change(), currency));
        }
        lines.add("Atendió: " + payment.cashierName());
        if (header.receiptFooter() != null) {
            lines.add("");
            lines.add(header.receiptFooter());
        }
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private static String money(Money amount, String currency) {
        return amount.amount().toPlainString() + " " + currency;
    }

    private static String kind(ChargeKind kind) {
        return switch (kind) {
            case PRIMERA -> "Primera membresía";
            case RENOVACION_ANTICIPADA -> "Renovación anticipada";
            case RENOVACION_VENCIDA -> "Renovación";
            case REACTIVACION -> "Reactivación";
        };
    }
}
