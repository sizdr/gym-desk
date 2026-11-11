package dev.gimnasio.sistemagimnasio.ui.common;

import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.AuthenticationException;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorReportTest {

    @Test
    void validationKeepsEachFieldWithItsMessageInOrder() {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("username", "Escribe tu nombre de usuario.");
        errors.put("password", "Escribe tu contraseña.");
        errors.put("email", "Escribe un correo válido.");
        errors.put("phone", "Escribe el teléfono a 10 dígitos.");
        errors.put("joinDate", "Escribe la fecha de alta.");

        ErrorReport report = ErrorReport.of(new ValidationException(errors));

        assertEquals(ErrorReport.Kind.VALIDATION, report.kind());
        assertEquals(errors, report.fieldErrors());
        // assertEquals entre mapas no revisa el orden; el foco va al primer campo, así que se revisa aparte.
        assertEquals(List.copyOf(errors.keySet()), List.copyOf(report.fieldErrors().keySet()));
    }

    @Test
    void fieldErrorsCannotBeModified() {
        ErrorReport report = ErrorReport.of(ValidationException.of("name", "Escribe el nombre."));

        assertThrows(UnsupportedOperationException.class, () -> report.fieldErrors().put("x", "y"));
    }

    @Test
    void notFoundAsksToReload() {
        ErrorReport report = ErrorReport.of(new NotFoundException("El plan ya no existe. Recarga la pantalla."));

        assertEquals(ErrorReport.Kind.NOT_FOUND, report.kind());
        assertEquals("El plan ya no existe. Recarga la pantalla.", report.message());
        assertTrue(report.fieldErrors().isEmpty());
    }

    @Test
    void businessRulesShowTheirMessageAsIs() {
        assertEquals(new ErrorReport(ErrorReport.Kind.BUSINESS, "Deja al menos un método de pago.", Map.of()),
                ErrorReport.of(new DomainException("Deja al menos un método de pago.")));
    }

    @Test
    void authenticationFailuresAreBusinessMessages() {
        ErrorReport report = ErrorReport.of(new AuthenticationException("Usuario o contraseña incorrectos."));

        assertEquals(ErrorReport.Kind.BUSINESS, report.kind());
        assertEquals("Usuario o contraseña incorrectos.", report.message());
    }

    @Test
    void technicalFailuresHideTheirDetail() {
        ErrorReport report = ErrorReport.of(new IllegalStateException("Connection refused"));

        assertEquals(ErrorReport.Kind.UNEXPECTED, report.kind());
        assertEquals(ErrorReport.UNEXPECTED_MESSAGE, report.message());
    }

    @Test
    void unwrapsConcurrencyWrappers() {
        var wrapped = new CompletionException(new ExecutionException(new DomainException("Regla")));

        assertEquals(ErrorReport.Kind.BUSINESS, ErrorReport.of(wrapped).kind());
    }
}
