package dev.gimnasio.sistemagimnasio.domain.user;

/** Qué contraseña se acepta al crearla o cambiarla. No se recortan espacios: cuentan como caracteres. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static boolean isAcceptable(String raw) {
        return raw != null && raw.length() >= MIN_LENGTH;
    }
}
