package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    /** Sin distinguir mayúsculas; incluye a los desactivados. */
    Optional<UserCredentials> findCredentialsByUsername(String username);

    Optional<User> findById(long id);

    /** Activos e inactivos, sin orden garantizado. */
    List<User> findAll();

    int count();

    /** Sin distinguir mayúsculas; {@code excludeId} (puede ser null) no cuenta. */
    boolean existsByUsername(String username, Long excludeId);

    int countActiveAdmins();

    /** @return el mismo usuario con el id asignado */
    User insert(User user, String passwordHash);

    /** Nombre, usuario, rol y activo. @throws IllegalStateException si el id no existe */
    void update(User user);

    void updatePasswordHash(long id, String passwordHash);

    /** {@code true} si tiene cobros, ventas o movimientos de inventario registrados. */
    boolean hasOperations(long id);

    void delete(long id);
}
