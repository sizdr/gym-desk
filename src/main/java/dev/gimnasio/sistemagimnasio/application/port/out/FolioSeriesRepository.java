package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.domain.payment.Folio;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;

public interface FolioSeriesRepository {
    /** @throws IllegalStateException si la serie no existe */
    FolioSeries get(String series);

    /** Guarda prefijo y ancho; el contador no cambia. */
    void save(FolioSeries series);

    /**
     * Avanza el contador y devuelve el folio que toca. Debe llamarse dentro de la transacción del cobro: si la
     * transacción se deshace, el número vuelve a quedar libre, y dos estaciones nunca reciben el mismo.
     *
     * @throws IllegalStateException si la serie no existe
     */
    Folio next(String series);
}
