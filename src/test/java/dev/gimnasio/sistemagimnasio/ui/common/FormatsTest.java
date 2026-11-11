package dev.gimnasio.sistemagimnasio.ui.common;

import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatsTest {
    private static final Currency MXN = Currency.getInstance("MXN");

    @Test
    void pesosUseSymbolGroupingAndTwoDecimals() {
        assertEquals("$1,250.00", Formats.money(Money.of("1250"), MXN));
        assertEquals("$0.50", Formats.money(Money.of("0.5"), MXN));
    }

    @Test
    void datesAreDayMonthYear() {
        assertEquals("09/11/2026", Formats.date(LocalDate.of(2026, 11, 9)));
        assertEquals("09/11/2026 18:05", Formats.dateTime(LocalDateTime.of(2026, 11, 9, 18, 5)));
    }

    @Test
    void mediumDateLikeThePrototypeHeader() {
        assertEquals("mar 10 nov 2026", Formats.mediumDate(LocalDate.of(2026, 11, 10)));
        assertEquals("mié 2 sep 2026", Formats.mediumDate(LocalDate.of(2026, 9, 2)));
        assertEquals("sáb 1 ago 2026", Formats.mediumDate(LocalDate.of(2026, 8, 1)));
    }

    @Test
    void longDateIsCapitalizedSpanish() {
        assertEquals("Lunes 9 de noviembre de 2026", Formats.longDate(LocalDate.of(2026, 11, 9)));
    }
}
