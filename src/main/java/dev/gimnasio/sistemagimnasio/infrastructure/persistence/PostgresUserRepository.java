package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.UserCredentials;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class PostgresUserRepository implements UserRepository {
    private final Jdbi jdbi;

    public PostgresUserRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    /** Sin distinguir mayúsculas, igual que el índice único de nombre_usuario. */
    @Override
    public Optional<UserCredentials> findCredentialsByUsername(String username) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, nombre, nombre_usuario, rol::TEXT AS rol, activo, contrasena_hash
                        FROM usuario
                        WHERE LOWER(nombre_usuario) = LOWER(:username)
                        """)
                .bind("username", username)
                .map(PostgresUserRepository::mapCredentials)
                .findOne());
    }

    @Override
    public Optional<User> findById(long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, nombre, nombre_usuario, rol::TEXT AS rol, activo
                        FROM usuario
                        WHERE id = :id
                        """)
                .bind("id", id)
                .map(PostgresUserRepository::mapUser)
                .findOne());
    }

    @Override
    public List<User> findAll() {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, nombre, nombre_usuario, rol::TEXT AS rol, activo
                        FROM usuario
                        ORDER BY id
                        """)
                .map(PostgresUserRepository::mapUser)
                .list());
    }

    @Override
    public int count() {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT COUNT(*)
                        FROM usuario
                        """)
                .mapTo(Integer.class)
                .one());
    }

    @Override
    public boolean existsByUsername(String username, Long excludeId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM usuario
                            WHERE LOWER(nombre_usuario) = LOWER(:username)
                              AND (CAST(:excludeId AS BIGINT) IS NULL OR id <> CAST(:excludeId AS BIGINT))
                        )
                        """)
                .bind("username", username)
                .bind("excludeId", excludeId)
                .mapTo(Boolean.class)
                .one());
    }

    @Override
    public int countActiveAdmins() {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT COUNT(*)
                        FROM usuario
                        WHERE activo
                          AND rol = 'administrador'
                        """)
                .mapTo(Integer.class)
                .one());
    }

    @Override
    public User insert(User user, String passwordHash) {
        long id = jdbi.withHandle(handle -> handle.createUpdate("""
                        INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol, activo)
                        VALUES (:nombre, :usuario, :hash, CAST(:rol AS rol_usuario), :activo)
                        """)
                .bind("nombre", user.name())
                .bind("usuario", user.username())
                .bind("hash", passwordHash)
                .bind("rol", roleValue(user.role()))
                .bind("activo", user.active())
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
        return new User(id, user.name(), user.username(), user.role(), user.active());
    }

    @Override
    public void update(User user) {
        int updated = jdbi.withHandle(handle -> handle.createUpdate("""
                        UPDATE usuario
                        SET nombre         = :nombre,
                            nombre_usuario = :usuario,
                            rol            = CAST(:rol AS rol_usuario),
                            activo         = :activo
                        WHERE id = :id
                        """)
                .bind("id", user.id())
                .bind("nombre", user.name())
                .bind("usuario", user.username())
                .bind("rol", roleValue(user.role()))
                .bind("activo", user.active())
                .execute());
        if (updated != 1) {
            throw new IllegalStateException("No existe el usuario " + user.id() + ".");
        }
    }

    @Override
    public void updatePasswordHash(long id, String passwordHash) {
        int updated = jdbi.withHandle(handle -> handle.createUpdate("""
                        UPDATE usuario
                        SET contrasena_hash = :hash
                        WHERE id = :id
                        """)
                .bind("id", id)
                .bind("hash", passwordHash)
                .execute());
        if (updated != 1) {
            throw new IllegalStateException("No existe el usuario " + id + ".");
        }
    }

    @Override
    public boolean hasOperations(long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT EXISTS (SELECT 1 FROM pago WHERE usuario_id = :id)
                            OR EXISTS (SELECT 1 FROM venta WHERE usuario_id = :id)
                            OR EXISTS (SELECT 1 FROM movimiento_inventario WHERE usuario_id = :id)
                        """)
                .bind("id", id)
                .mapTo(Boolean.class)
                .one());
    }

    @Override
    public void delete(long id) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                        DELETE FROM usuario
                        WHERE id = :id
                        """)
                .bind("id", id)
                .execute());
    }

    private static String roleValue(Role role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    private static UserCredentials mapCredentials(ResultSet rs, StatementContext ctx) throws SQLException {
        return new UserCredentials(mapUser(rs, ctx), rs.getString("contrasena_hash"));
    }

    private static User mapUser(ResultSet rs, StatementContext ctx) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("nombre"),
                rs.getString("nombre_usuario"),
                parseRole(rs.getString("rol")),
                rs.getBoolean("activo"));
    }

    private static Role parseRole(String role) {
        return Role.valueOf(role.toUpperCase(Locale.ROOT));
    }
}
