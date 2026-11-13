package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.PasswordHasher;

/** Hash falso pero distinto de la contraseña, para detectar comparaciones o guardados en claro. */
public final class ReversingHasher implements PasswordHasher {
    @Override
    public String hash(String rawPassword) {
        return "hash(" + new StringBuilder(rawPassword).reverse() + ")";
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return hash(rawPassword).equals(passwordHash);
    }
}
