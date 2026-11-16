-- Describe el esquema public como JSON: tablas con sus columnas, claves y relaciones, y los tipos enumerados.
-- Lo usa generar-diagrama.sh para dibujar docs/diagrama-modelo-de-datos.svg a partir de la base real.
WITH cols AS (
    SELECT c.relname AS tabla,
           a.attnum,
           a.attname AS columna,
           replace(replace(replace(format_type(a.atttypid, a.atttypmod),
                   'character varying', 'varchar'), 'timestamp with time zone', 'timestamptz'),
                   'character(', 'char(') AS tipo,
           NOT a.attnotnull AS nulo,
           EXISTS (SELECT 1 FROM pg_constraint k
                   WHERE k.conrelid = c.oid AND k.contype = 'p' AND a.attnum = ANY (k.conkey)) AS pk,
           EXISTS (SELECT 1 FROM pg_constraint k
                   WHERE k.conrelid = c.oid AND k.contype = 'f' AND a.attnum = ANY (k.conkey)) AS fk
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
    WHERE c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
),
fks AS (
    SELECT c.relname AS tabla,
           (SELECT attname FROM pg_attribute WHERE attrelid = k.conrelid AND attnum = k.conkey[1]) AS columna,
           p.relname AS referencia,
           (SELECT attname FROM pg_attribute WHERE attrelid = k.confrelid AND attnum = k.confkey[1]) AS columna_referencia,
           -- 1:1 si las columnas de la clave foránea son únicas por sí mismas.
           EXISTS (SELECT 1 FROM pg_index i
                   WHERE i.indrelid = k.conrelid AND i.indisunique
                     AND i.indkey::int2[] @> ARRAY[k.conkey[1]] AND array_length(i.indkey::int2[], 1) = 1) AS unica
    FROM pg_constraint k
    JOIN pg_class c ON c.oid = k.conrelid
    JOIN pg_class p ON p.oid = k.confrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
    WHERE k.contype = 'f'
)
SELECT json_build_object(
    'tablas', (SELECT json_object_agg(tabla, columnas) FROM (
                   SELECT tabla, json_agg(json_build_object('nombre', columna, 'tipo', tipo, 'nulo', nulo,
                                                            'pk', pk, 'fk', fk) ORDER BY attnum) AS columnas
                   FROM cols GROUP BY tabla) t),
    'relaciones', (SELECT coalesce(json_agg(row_to_json(fks) ORDER BY tabla, columna), '[]') FROM fks),
    'enums', (SELECT json_object_agg(typname, valores) FROM (
                  SELECT t.typname, json_agg(e.enumlabel ORDER BY e.enumsortorder) AS valores
                  FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid
                  JOIN pg_namespace n ON n.oid = t.typnamespace AND n.nspname = 'public'
                  GROUP BY t.typname) e)
);
