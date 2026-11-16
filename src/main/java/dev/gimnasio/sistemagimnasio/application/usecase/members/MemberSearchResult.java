package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;

import java.util.List;
import java.util.Map;

/**
 * @param members       hasta {@code limit}
 * @param matchCount    cuántos coinciden con el texto y el estado, antes de {@code limit}
 * @param totalMembers  todos los socios registrados
 * @param countByStatus cuántos hay en cada estado entre todos los socios (sin aplicar el texto); trae los cinco
 */
public record MemberSearchResult(List<MemberRow> members, int matchCount, int totalMembers,
                                 Map<MemberStatus, Integer> countByStatus) {
    public MemberSearchResult {
        members = List.copyOf(members);
        countByStatus = Map.copyOf(countByStatus);
    }
}
