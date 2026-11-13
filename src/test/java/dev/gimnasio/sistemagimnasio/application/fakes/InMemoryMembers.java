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
}
