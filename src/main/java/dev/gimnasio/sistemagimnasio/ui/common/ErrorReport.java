package dev.gimnasio.sistemagimnasio.ui.common;

import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Qué hacer con una excepción de un caso de uso, según «Errores» en docs/contratos.md.
 *
 * @param fieldErrors campo → mensaje, en el orden en que se validaron; vacío salvo en {@link Kind#VALIDATION}
 */
public record ErrorReport(Kind kind, String message, Map<String, String> fieldErrors) {
    static final String UNEXPECTED_MESSAGE =
            "Ocurrió un error inesperado. Inténtalo de nuevo y, si se repite, avisa al administrador.";
    private static final System.Logger LOG = System.getLogger(ErrorReport.class.getName());

    public enum Kind {
        /** Marcar cada campo con su mensaje. */
        VALIDATION,
        /** Mostrar el mensaje y recargar la pantalla: otro usuario cambió el registro. */
        NOT_FOUND,
        /** Regla de negocio que no es de un campo: mostrar el mensaje tal cual. */
        BUSINESS,
        /** Fallo técnico: mensaje genérico; el detalle va al registro. */
        UNEXPECTED
    }

    public ErrorReport {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
        // Map.copyOf no conserva el orden; el primer campo con error es el que recibe el foco.
        fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public static ErrorReport of(Throwable error) {
        Throwable cause = unwrap(error);
        return switch (cause) {
            case ValidationException e -> new ErrorReport(Kind.VALIDATION, e.getMessage(), e.errors());
            case NotFoundException e -> new ErrorReport(Kind.NOT_FOUND, e.getMessage(), Map.of());
            case DomainException e -> new ErrorReport(Kind.BUSINESS, e.getMessage(), Map.of());
            default -> {
                LOG.log(System.Logger.Level.ERROR, "Error inesperado en la UI", cause);
                yield new ErrorReport(Kind.UNEXPECTED, UNEXPECTED_MESSAGE, Map.of());
            }
        };
    }

    private static Throwable unwrap(Throwable error) {
        Objects.requireNonNull(error, "error");
        Throwable current = error;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
