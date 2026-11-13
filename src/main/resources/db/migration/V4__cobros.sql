-- ---------- Cobros: tipo de cobro y operaciones que no se repiten ----------

CREATE TYPE tipo_cobro AS ENUM ('primera', 'renovacion_anticipada', 'renovacion_vencida', 'reactivacion');

-- El tipo se guarda como hecho histórico: lo que se cobró en ese momento, aunque después cambie el plazo de baja.
-- operacion_id lo genera la UI al abrir el formulario; un segundo envío con el mismo id no registra otro cobro.
ALTER TABLE pago
    ADD COLUMN tipo_cobro   tipo_cobro  NOT NULL,
    ADD COLUMN operacion_id varchar(64) UNIQUE;
