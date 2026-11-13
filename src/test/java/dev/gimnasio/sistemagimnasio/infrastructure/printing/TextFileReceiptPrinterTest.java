package dev.gimnasio.sistemagimnasio.infrastructure.printing;

import dev.gimnasio.sistemagimnasio.application.usecase.billing.GymHeader;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.MembershipReceipt;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFileReceiptPrinterTest {
    @TempDir
    Path dir;

    private static MembershipReceipt receipt(Money received, Money change, String footer) {
        GymHeader header = new GymHeader("Gym Fuerza", null, "Av. Juárez 10", null, footer, Currency.getInstance("MXN"));
        PaymentRow row = new PaymentRow(1, "GYM-0042", LocalDateTime.of(2026, 11, 12, 9, 30), 7, "Ana López", "Mensual",
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 12, 12), ChargeKind.REACTIVACION, "Efectivo",
                Money.of("450"), "Lucía Pérez");
        return new MembershipReceipt(header, row, 30, received, change);
    }

    @Test
    void guardaElReciboConSuFolio_yLosDatosDelCobro() throws IOException {
        new TextFileReceiptPrinter(dir.resolve("recibos")).print(receipt(Money.of("500"), Money.of("50"), "¡Gracias!"));

        String text = Files.readString(dir.resolve("recibos").resolve("GYM-0042.txt"));
        for (String expected : new String[]{"Gym Fuerza", "Av. Juárez 10", "Folio: GYM-0042", "Fecha: 12/11/2026 09:30",
                "Socio: Ana López", "Plan: Mensual (30 días)", "Vigencia: 12/11/2026 al 12/12/2026", "Tipo: Reactivación",
                "Total: 450.00 MXN", "Recibido: 500.00 MXN", "Cambio: 50.00 MXN", "Atendió: Lucía Pérez", "¡Gracias!"}) {
            assertTrue(text.contains(expected), expected + " en:\n" + text);
        }
    }

    @Test
    void sinEfectivo_noMuestraRecibidoNiCambio() {
        String text = TextFileReceiptPrinter.render(receipt(null, null, null));
        assertFalse(text.contains("Recibido"));
        assertFalse(text.contains("Cambio"));
    }
}
