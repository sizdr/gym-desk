package dev.gimnasio.sistemagimnasio.domain.settings;

import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;

import java.util.Currency;

/**
 * Que la moneda no cambie si ya hay cobros lo valida la capa de aplicación.
 * {@code logo} es la imagen guardada, o null; se copia para que nadie la altere desde fuera.
 * El prefijo de folios vive en su serie ({@code FolioSeries}).
 */
public record GymSettings(
        String gymName,
        String address,
        String phone,
        byte[] logo,
        String receiptFooter,
        Currency currency,
        int warningDays,
        int autoDeactivationDays) {

    public static final int DEFAULT_WARNING_DAYS = 7;
    public static final int DEFAULT_AUTO_DEACTIVATION_DAYS = 30;
    /** Los mensajes se arman con estos límites; la base repite los mismos en sus CHECK y varchar. */
    public static final int MIN_WARNING_DAYS = 1;
    public static final int MAX_WARNING_DAYS = 60;
    public static final int MIN_AUTO_DEACTIVATION_DAYS = 1;
    public static final int MAX_AUTO_DEACTIVATION_DAYS = 365;
    public static final int MAX_GYM_NAME_LENGTH = 100;
    public static final int MAX_ADDRESS_LENGTH = 200;
    public static final int MAX_PHONE_LENGTH = 30;
    public static final int MAX_RECEIPT_FOOTER_LENGTH = 160;
    public static final int MAX_LOGO_BYTES = 300 * 1024;

    public GymSettings {
        if (gymName == null || gymName.isBlank()) {
            throw new DomainException("El nombre del gimnasio es obligatorio.");
        }
        requireFits("El nombre del gimnasio", gymName, MAX_GYM_NAME_LENGTH);
        requireFits("La dirección", address, MAX_ADDRESS_LENGTH);
        requireFits("El teléfono", phone, MAX_PHONE_LENGTH);
        requireFits("La leyenda del recibo", receiptFooter, MAX_RECEIPT_FOOTER_LENGTH);
        if (!isValidLogoSize(logo)) {
            throw new DomainException("El logotipo debe pesar " + MAX_LOGO_BYTES / 1024 + " KB o menos.");
        }
        if (currency == null) {
            throw new DomainException("La moneda es obligatoria.");
        }
        if (!isValidWarningDays(warningDays)) {
            throw new DomainException("Los días de aviso deben estar entre " + MIN_WARNING_DAYS + " y "
                    + MAX_WARNING_DAYS + ".");
        }
        if (!isValidAutoDeactivationDays(autoDeactivationDays)) {
            throw new DomainException("El plazo de baja automática debe estar entre " + MIN_AUTO_DEACTIVATION_DAYS
                    + " y " + MAX_AUTO_DEACTIVATION_DAYS + " días.");
        }
        logo = logo == null ? null : logo.clone();
    }

    /** Un texto opcional (null) o de {@code max} caracteres o menos. */
    public static boolean fits(String value, int max) {
        return value == null || value.length() <= max;
    }

    public static boolean isValidLogoSize(byte[] logo) {
        return logo == null || logo.length <= MAX_LOGO_BYTES;
    }

    public static boolean isValidWarningDays(Integer days) {
        return days != null && days >= MIN_WARNING_DAYS && days <= MAX_WARNING_DAYS;
    }

    public static boolean isValidAutoDeactivationDays(Integer days) {
        return days != null && days >= MIN_AUTO_DEACTIVATION_DAYS && days <= MAX_AUTO_DEACTIVATION_DAYS;
    }

    private static void requireFits(String field, String value, int max) {
        if (!fits(value, max)) {
            throw new DomainException(field + " debe tener " + max + " caracteres o menos.");
        }
    }

    @Override
    public byte[] logo() {
        return logo == null ? null : logo.clone();
    }
}
