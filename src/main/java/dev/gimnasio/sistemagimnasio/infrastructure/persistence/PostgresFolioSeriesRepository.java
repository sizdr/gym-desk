package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.FolioSeriesRepository;
import dev.gimnasio.sistemagimnasio.domain.payment.Folio;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;
import org.jdbi.v3.core.Jdbi;

import java.util.Objects;

public final class PostgresFolioSeriesRepository implements FolioSeriesRepository {
    private final Jdbi jdbi;

    public PostgresFolioSeriesRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    @Override
    public FolioSeries get(String series) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT serie, prefijo, ancho
                        FROM folio_secuencia
                        WHERE serie = :serie
                        """)
                .bind("serie", series)
                .map((rs, ctx) -> new FolioSeries(rs.getString("serie"), rs.getString("prefijo"), rs.getInt("ancho")))
                .findOne()
                .orElseThrow(() -> new IllegalStateException("Falta la serie de folios " + series + ".")));
    }

    @Override
    public void save(FolioSeries series) {
        int updated = jdbi.withHandle(handle -> handle.createUpdate("""
                        UPDATE folio_secuencia
                        SET prefijo = :prefijo,
                            ancho   = :ancho
                        WHERE serie = :serie
                        """)
                .bind("prefijo", series.prefix())
                .bind("ancho", series.width())
                .bind("serie", series.series())
                .execute());
        if (updated != 1) {
            throw new IllegalStateException("Falta la serie de folios " + series.series() + ".");
        }
    }

    /** El {@code UPDATE ... RETURNING} bloquea la fila de la serie hasta que termina la transacción del cobro. */
    @Override
    public Folio next(String series) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        UPDATE folio_secuencia
                        SET ultimo = ultimo + 1
                        WHERE serie = :serie
                        RETURNING serie, prefijo, ancho, ultimo
                        """)
                .bind("serie", series)
                .map((rs, ctx) -> new FolioSeries(rs.getString("serie"), rs.getString("prefijo"), rs.getInt("ancho"))
                        .format(rs.getLong("ultimo")))
                .findOne()
                .orElseThrow(() -> new IllegalStateException("Falta la serie de folios " + series + ".")));
    }
}
