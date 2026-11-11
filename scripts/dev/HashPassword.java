import dev.gimnasio.sistemagimnasio.infrastructure.security.Argon2PasswordHasher;

/**
 * Imprime el hash Argon2id de la contraseña que llega en la variable de entorno CLAVE, con el mismo
 * hasher que usa la app. Lo usa crear-admin-dev.sh; no recibe la contraseña como argumento para que
 * no aparezca en la lista de procesos.
 */
public class HashPassword {
    public static void main(String[] args) {
        String password = System.getenv("CLAVE");
        if (password == null || password.isEmpty()) {
            System.err.println("Falta la variable de entorno CLAVE.");
            System.exit(2);
        }
        System.out.println(new Argon2PasswordHasher().hash(password));
    }
}
