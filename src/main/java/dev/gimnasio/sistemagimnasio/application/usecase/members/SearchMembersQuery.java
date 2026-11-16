package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;

/**
 * @param text   opcional: parte del nombre (sin distinguir mayúsculas ni acentos), del correo o, con 3 dígitos o
 *               más, del teléfono
 * @param status opcional: null no filtra
 * @param sort   opcional: VENCIMIENTO por defecto
 * @param limit  opcional: null devuelve todos
 */
public record SearchMembersQuery(String text, MemberStatus status, MemberSort sort, Integer limit) {
    public static SearchMembersQuery all() {
        return new SearchMembersQuery(null, null, null, null);
    }
}
