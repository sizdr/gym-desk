package dev.gimnasio.sistemagimnasio.domain.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "       "})
    void menosDeOchoCaracteres_seRechaza(String raw) {
        assertFalse(PasswordPolicy.isAcceptable(raw));
    }

    @Test
    void ochoCaracteresOMas_seAcepta_yLosEspaciosCuentan() {
        assertTrue(PasswordPolicy.isAcceptable("12345678"));
        assertTrue(PasswordPolicy.isAcceptable(" clave1 "));
    }
}
