package dev.gimnasio.sistemagimnasio.application.usecase.members;

/** Orden de la lista de socios. */
public enum MemberSort {
    /** Primero los cubiertos, del vencimiento más próximo; luego vencidos y bajas, del más reciente; al final, sin membresía. */
    VENCIMIENTO,
    /** Del vencimiento más lejano al más antiguo; sin membresía al final. */
    VENCIMIENTO_DESC,
    /** Alfabético. */
    NOMBRE
}
