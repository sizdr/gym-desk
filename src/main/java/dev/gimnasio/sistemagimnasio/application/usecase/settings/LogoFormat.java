package dev.gimnasio.sistemagimnasio.application.usecase.settings;

import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;

import java.util.Arrays;

/**
 * Reconoce la imagen por sus primeros bytes, no por el nombre del archivo. Solo PNG y JPG: son los formatos
 * que JavaFX dibuja, y el logotipo se muestra en la app y en recibos y tickets. WEBP y SVG se rechazan
 * porque se guardarían pero nunca se verían.
 */
final class LogoFormat {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private LogoFormat() {
    }

    static boolean isAccepted(byte[] image) {
        if (image == null || image.length == 0 || !GymSettings.isValidLogoSize(image)) {
            return false;
        }
        return startsWith(image, PNG) || startsWith(image, JPG);
    }

    private static boolean startsWith(byte[] image, byte[] signature) {
        return image.length >= signature.length
                && Arrays.equals(image, 0, signature.length, signature, 0, signature.length);
    }
}
