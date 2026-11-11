package dev.gimnasio.sistemagimnasio.ui.common;

import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Convierte lo que se escribió en un campo al tipo que pide el command. Si el texto no se puede convertir
 * devuelve {@code null}: el caso de uso lo reporta como campo vacío con su propio mensaje, así la UI
 * no repite reglas de validación.
 */
public final class Inputs {
    /**
     * Punto decimal y coma de miles, como se muestran los importes: «450», «450.5», «$1,250.00».
     * Las comas solo se aceptan en grupos de tres, para que «450,50» no se lea como 45,050.
     */
    private static final Pattern AMOUNT = Pattern.compile("\\$?\\s*(\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d{1,2})?");

    private Inputs() {
    }

    /**
     * {@code null} si está vacío, tiene otro formato (coma decimal, separadores fuera de lugar,
     * más de dos decimales) o es negativo.
     */
    public static Money money(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        if (!AMOUNT.matcher(trimmed).matches()) {
            return null;
        }
        String digits = trimmed.replace("$", "").replace(",", "").strip();
        return new Money(new BigDecimal(digits));
    }

    /** {@code null} si está vacío o no es un entero. */
    public static Integer integer(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
