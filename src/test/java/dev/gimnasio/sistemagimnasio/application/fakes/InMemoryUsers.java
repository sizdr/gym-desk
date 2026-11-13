package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.UserCredentials;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class InMemoryUsers implements UserRepository {
    private final Map<Long, User> byId = new LinkedHashMap<>();
    private final Map<Long, String> hashes = new HashMap<>();
    private final Set<Long> withOperations = new HashSet<>();
    private long nextId = 1;
    public int lookups;
    public int writes;
    public final TransactionGuard guard = new TransactionGuard();

    public User add(String name, Role role, boolean active) {
        return add(new User(nextId, name, name, role, active), "hash");
    }

    /** Guarda el usuario con el id que trae (o el siguiente si no tiene) y su hash. */
    public User add(User user, String passwordHash) {
        long id = user.id() == null ? nextId : user.id();
        User saved = new User(id, user.name(), user.username(), user.role(), user.active());
        byId.put(id, saved);
        hashes.put(id, passwordHash);
        nextId = Math.max(nextId, id + 1);
        return saved;
    }

    public User admin() {
        return add("Admin" + nextId, Role.ADMINISTRADOR, true);
    }

    public void replace(User user) {
        byId.put(user.id(), user);
    }

    public void markWithOperations(long id) {
        withOperations.add(id);
    }

    public User get(long id) {
        return byId.get(id);
    }

    public String hashOf(long id) {
        return hashes.get(id);
    }

    public int size() {
        return byId.size();
    }

    @Override
    public Optional<UserCredentials> findCredentialsByUsername(String username) {
        guard.check("findCredentialsByUsername");
        lookups++;
        return byId.values().stream()
                .filter(user -> user.username().equalsIgnoreCase(username))
                .map(user -> new UserCredentials(user, hashes.get(user.id())))
                .findFirst();
    }

    @Override
    public Optional<User> findById(long id) {
        guard.check("findById");
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<User> findAll() {
        guard.check("findAll");
        return new ArrayList<>(byId.values());
    }

    @Override
    public int count() {
        guard.check("count");
        return byId.size();
    }

    @Override
    public boolean existsByUsername(String username, Long excludeId) {
        guard.check("existsByUsername");
        return byId.values().stream()
                .anyMatch(user -> user.username().equalsIgnoreCase(username) && !Objects.equals(user.id(), excludeId));
    }

    @Override
    public int countActiveAdmins() {
        guard.check("countActiveAdmins");
        return (int) byId.values().stream().filter(user -> user.active() && user.role() == Role.ADMINISTRADOR).count();
    }

    @Override
    public User insert(User user, String passwordHash) {
        guard.check("insert");
        writes++;
        return add(new User(nextId, user.name(), user.username(), user.role(), user.active()), passwordHash);
    }

    @Override
    public void update(User user) {
        guard.check("update");
        writes++;
        byId.put(user.id(), user);
    }

    @Override
    public void updatePasswordHash(long id, String passwordHash) {
        guard.check("updatePasswordHash");
        writes++;
        hashes.put(id, passwordHash);
    }

    @Override
    public boolean hasOperations(long id) {
        guard.check("hasOperations");
        return withOperations.contains(id);
    }

    @Override
    public void delete(long id) {
        guard.check("delete");
        writes++;
        byId.remove(id);
        hashes.remove(id);
    }
}
