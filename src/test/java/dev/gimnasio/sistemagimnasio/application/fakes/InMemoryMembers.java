package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Phone;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryMembers implements MemberRepository {
    private final Map<Long, Member> byId = new LinkedHashMap<>();
    private long nextId = 1;
    public final TransactionGuard guard = new TransactionGuard();

    public Member add(String fullName, LocalDate joinDate) {
        Member member = new Member(nextId, fullName, new Phone(String.format("55%08d", nextId)), null, joinDate);
        byId.put(nextId++, member);
        return member;
    }

    @Override
    public Optional<Member> findById(long id) {
        guard.check("findById");
        return Optional.ofNullable(byId.get(id));
    }

    private final java.util.Set<Long> paid = new java.util.HashSet<>();
    public int writes;

    /** Simula que el socio ya tiene cobros. */
    public void markPaid(long id) {
        paid.add(id);
    }

    public Member get(long id) {
        return byId.get(id);
    }

    @Override
    public java.util.List<Member> findAll() {
        guard.check("findAll");
        return java.util.List.copyOf(byId.values());
    }

    @Override
    public Optional<String> nameWithEmail(String email, Long excludeId) {
        guard.check("nameWithEmail");
        return byId.values().stream()
                .filter(m -> m.email() != null && m.email().value().equalsIgnoreCase(email)
                        && !java.util.Objects.equals(m.id(), excludeId))
                .map(Member::fullName).findFirst();
    }

    @Override
    public Member insert(Member member) {
        guard.check("insert");
        writes++;
        Member saved = new Member(nextId++, member.fullName(), member.phone(), member.email(), member.joinDate());
        byId.put(saved.id(), saved);
        return saved;
    }

    @Override
    public void update(Member member) {
        guard.check("update");
        if (!byId.containsKey(member.id())) {
            throw new IllegalStateException("No existe el socio " + member.id());
        }
        writes++;
        byId.put(member.id(), member);
    }

    @Override
    public boolean hasPayments(long id) {
        guard.check("hasPayments");
        return paid.contains(id);
    }

    @Override
    public void delete(long id) {
        guard.check("delete");
        writes++;
        byId.remove(id);
    }
}
