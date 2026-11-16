-- Diccionario de datos en Markdown a partir del catálogo de PostgreSQL y de los COMMENT de 01_esquema.sql:
-- por cada atributo, tipo, si admite nulos, clave, valor predeterminado, restricciones y significado.
WITH col AS (
    SELECT c.oid AS tabla_oid, c.relname AS tabla, a.attnum, a.attname AS columna,
           format_type(a.atttypid, a.atttypmod) AS tipo,
           CASE WHEN a.attnotnull THEN 'No' ELSE 'Sí' END AS nulo,
           concat_ws(', ',
               CASE WHEN EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = c.oid AND k.contype = 'p' AND a.attnum = ANY (k.conkey)) THEN 'PK' END,
               (SELECT string_agg('FK → ' || k.confrelid::regclass, ', ') FROM pg_constraint k
                WHERE k.conrelid = c.oid AND k.contype = 'f' AND a.attnum = ANY (k.conkey)),
               CASE WHEN EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.oid AND i.indisunique AND NOT i.indisprimary
                                 AND a.attnum = ANY (i.indkey::int2[]))
                    THEN 'UNIQUE' END,
               -- Índices únicos sobre lower(columna): únicos sin distinguir mayúsculas.
               CASE WHEN EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.oid AND i.indisunique
                                 AND pg_get_indexdef(i.indexrelid) ~ ('lower\(\(?' || a.attname || '\M'))
                    THEN 'UNIQUE (sin mayúsculas)' END) AS clave,
           CASE WHEN a.attidentity <> '' THEN 'identidad' ELSE COALESCE(pg_get_expr(d.adbin, d.adrelid), '') END AS predeterminado,
           COALESCE((SELECT string_agg(regexp_replace(pg_get_constraintdef(k.oid), '^CHECK ', ''), '; ')
                     FROM pg_constraint k WHERE k.conrelid = c.oid AND k.contype = 'c' AND k.conkey = ARRAY[a.attnum]), '') AS restricciones,
           COALESCE(col_description(c.oid, a.attnum), '') AS significado
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
    LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
    WHERE c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
)
SELECT string_agg(bloque, E'\n' ORDER BY orden)
FROM (
    SELECT tabla AS orden,
           '### `' || tabla || E'`\n\n' || COALESCE(obj_description(tabla_oid, 'pg_class'), '') || E'\n\n'
           || E'| Atributo | Tipo | Nulo | Clave | Predeterminado | Restricciones | Significado |\n|---|---|---|---|---|---|---|\n'
           || string_agg('| `' || columna || '` | ' || tipo || ' | ' || nulo || ' | ' || clave || ' | '
                         || replace(predeterminado, '|', '\|') || ' | ' || replace(restricciones, '|', '\|') || ' | '
                         || significado || ' |', E'\n' ORDER BY attnum)
           || E'\n' AS bloque
    FROM col
    GROUP BY tabla, tabla_oid
) t;
