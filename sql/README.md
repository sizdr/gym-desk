# Scripts SQL del proyecto final

Scripts para crear y probar la base de datos del Proyecto Final Integrador de Bases de Datos (303-ISW). Reconstruyen la base completa sin la aplicación.

## Orden de ejecución

| # | Archivo | Qué hace |
|---|---|---|
| 1 | `01_esquema.sql` | Tipos enumerados, 8 tablas con `PRIMARY KEY`, `FOREIGN KEY`, `NOT NULL`, `UNIQUE`, `CHECK` y `DEFAULT`, índices y el diccionario de datos (`COMMENT`) |
| 2 | `02_datos.sql` | Datos de prueba: 4 usuarios, 4 métodos de pago, 6 planes, 12 socios y 14 cobros con sus membresías, en todos los estados |
| 3 | `03_consultas.sql` | 13 consultas: `WHERE`, funciones de PostgreSQL, `JOIN` y subconsultas |
| 4 | `04_funciones.sql` | 3 funciones propias y el trigger que impide borrar cobros y membresías |
| 5 | `05_procedimientos.sql` | Procedimiento almacenado `sp_registrar_cobro` |
| 6 | `06_pruebas.sql` | Usa las funciones y el procedimiento; lo que modifica se deshace al final |

### Con el PostgreSQL de `docker-compose.yml`

```bash
docker compose up -d
sql/crear-base.sh
```

Crea la base `gimnasio_proyecto` desde cero y corre los seis archivos en orden. No toca `gimnasio`, la base de la aplicación. Para consultarla:

```bash
docker compose exec db psql -U gimnasio -d gimnasio_proyecto
```

### Con cualquier PostgreSQL 16

```bash
createdb gimnasio_proyecto
for f in 01_esquema 02_datos 03_consultas 04_funciones 05_procedimientos 06_pruebas; do
  psql -v ON_ERROR_STOP=1 -d gimnasio_proyecto -f sql/$f.sql
done
```

## Relación con la aplicación

La aplicación crea su base con las migraciones de Flyway (`src/main/resources/db/migration`, V1 a V6) y tiene **el mismo esquema, las mismas funciones, el mismo procedimiento y el mismo trigger** que estos scripts:

- `01_esquema.sql` es V1 a V5 en un solo archivo.
- `V6__funciones_y_procedimientos.sql` contiene `04_funciones.sql` y `05_procedimientos.sql` tal cual.
- `SqlScriptsTest` construye las dos bases y compara columnas, restricciones, índices, enumerados, funciones y triggers; también corre cada consulta, el procedimiento y `06_pruebas.sql`. Si alguien cambia un camino sin el otro, la prueba falla.

Los usuarios de `02_datos.sql` no pueden entrar a la aplicación: su `contrasena_hash` no es un hash válido. Para entrar, crea un administrador con `scripts/dev/crear-admin-dev.sh`.

## Matriz de evidencias

Los resultados esperados corresponden a `02_datos.sql`; las fechas de los datos son relativas al día en que se cargan.

| ID | Necesidad | Recurso SQL | Archivo | Resultado esperado |
|---|---|---|---|---|
| Q01 | Buscar socios por parte del nombre | `WHERE` + `ILIKE` | `03_consultas.sql` | Ana López |
| Q02 | Planes activos en un rango de precio | `WHERE` + `BETWEEN` | `03_consultas.sql` | Estudiante, Mensual y Trimestral; no la promoción inactiva |
| Q03 | Cobros en efectivo de los últimos 30 días | `WHERE` + `IS NOT NULL` + fecha | `03_consultas.sql` | Los cobros en efectivo recientes con su cambio |
| Q04 | Usuarios activos de mostrador | `WHERE` + `IN` | `03_consultas.sql` | Carlos Ruiz y Rocío Méndez; Pedro está inactivo |
| Q05 | Membresías que vencen en 7 días | `WHERE` + `BETWEEN` con fechas | `03_consultas.sql` | Las de Bruno Díaz e Irene Vega |
| Q06 | Ingresos por mes y tipo de cobro | `date_trunc`, `to_char`, `COUNT`, `SUM`, `AVG`, `ROUND` | `03_consultas.sql` | Una fila por mes y tipo de cobro |
| Q07 | Antigüedad de cada socio | `age`, `EXTRACT`, `upper`, `COALESCE` | `03_consultas.sql` | Los 12 socios con días y meses inscritos |
| Q08 | Historial de cobros con sus datos relacionados | `JOIN` de 6 tablas | `03_consultas.sql` | 14 cobros con socio, plan, periodo, método y quién cobró |
| Q09 | Ingresos por método de pago | `LEFT JOIN` + `GROUP BY` | `03_consultas.sql` | 4 métodos; Vales con 0 cobros |
| Q10 | Cobros por usuario | `JOIN` + `GROUP BY` | `03_consultas.sql` | Ana Martínez, Carlos Ruiz y Rocío Méndez con sus totales |
| Q11 | Socios que nunca han pagado | Subconsulta `NOT EXISTS` | `03_consultas.sql` | Fernando Gil |
| Q12 | Cobros mayores al promedio | Subconsulta escalar con `AVG` | `03_consultas.sql` | El anual (GYM-0001) y el trimestral (GYM-0004) |
| Q13 | Última membresía de cada socio | Subconsulta correlacionada con `MAX` | `03_consultas.sql` | 11 socios con sus días restantes |
| F01 | Calcular el vencimiento de una membresía | Función `fn_fecha_vencimiento(inicio, plan)` | `04_funciones.sql` | 01/11 + Mensual → 01/12 |
| F02 | Estado de un socio en una fecha | Función `fn_estado_socio(socio, fecha)` | `04_funciones.sql` | 6 vigentes, 2 por vencer, 2 vencidos, 1 en baja, 1 sin membresía |
| F03 | Formato del folio | Función `fn_formatear_folio(prefijo, número, ancho)` | `04_funciones.sql` | `GYM-0007`; `GYM-123456` sin cortarse |
| T01 | Los cobros y membresías no se borran | Trigger `BEFORE DELETE` con `fn_impedir_borrado` | `04_funciones.sql` | `DELETE` rechazado con «Los registros de pago no se borran…» |
| P01 | Registrar el cobro de una membresía | Procedimiento `sp_registrar_cobro` | `05_procedimientos.sql` | Pago con el siguiente folio y su membresía; tipo de cobro según el estado; la misma operación no cobra dos veces |

`06_pruebas.sql` ejecuta F01, F02, F03 y P01 con estos datos y muestra sus resultados.

### Catálogos y módulos

| ID | Requisito | Casos de uso | Pantalla | Pruebas | Estado |
|---|---|---|---|---|---|
| C01 | Catálogo de socios: registrar, consultar, modificar y eliminar | `application/usecase/members/` | Socios (`ui/members/`) | `MembersTest`, `PostgresMemberRepositoryTest`, `MembersViewModelTest`, capturas `proyecto-socios` | ✅ |
| C02 | Catálogo de métodos de pago: registrar, consultar, modificar, activar y eliminar | `application/usecase/paymentmethods/` | Configuración › Métodos de pago (`ui/settings/`) | `PaymentMethodsTest`, `PaymentMethodsViewModelTest`, capturas `fase-2-entrega-2` | ✅ |
| C03 | Catálogo de planes: registrar, consultar, modificar, activar y eliminar | `application/usecase/plans/` | Planes | `PlansTest`, `PostgresPlanRepositoryTest` | Falta la pantalla |
| M01 | Módulo de cobro de membresías: socio, plan, pago, periodo calculado, folio y recibo | `application/usecase/billing/`; P01 en la base | Cobrar membresía (`ui/billing/`) | `BillingTest`, `SqlScriptsTest`, `ChargeViewModelTest`, capturas `proyecto-cobro` | ✅ |
| M02 | Módulo de control de vigencias | F02 en la base | Tablero | `SqlScriptsTest` | Faltan el caso de uso y la pantalla |
