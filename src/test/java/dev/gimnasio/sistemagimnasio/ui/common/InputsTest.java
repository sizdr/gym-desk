package dev.gimnasio.sistemagimnasio.ui.common;

import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InputsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "450        | 450.00",
            "' 450.5 '  | 450.50",
            "$1,250.00  | 1250.00",
            "'$ 450'    | 450.00",
            "1,234,567.8| 1234567.80",
            "1250.00    | 1250.00",
            "0          | 0.00"})
    void moneyAcceptsWhatPeopleType(String text, String expected) {
        assertEquals(Money.of(expected), Inputs.money(text));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "abc", "12.3.4", "-5",
            "450,50", "1,2", "1$2", "12 34", "1,25,0", "12,34.5", "450.555", "450.", ".5", "$-5", "45$"})
    void moneyIsNullWhenItCannotBeAnAmount(String text) {
        assertNull(Inputs.money(text));
    }

    @ParameterizedTest
    @CsvSource({"30,30", "' 365 ',365", "-3,-3"})
    void integerParsesWholeNumbers(String text, int expected) {
        assertEquals(expected, Inputs.integer(text));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "30 días", "1.5"})
    void integerIsNullWhenItIsNotAWholeNumber(String text) {
        assertNull(Inputs.integer(text));
    }
}
