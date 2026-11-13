# Sistema Gimnasio

Aplicación de escritorio (JavaFX 21, Java 25, Maven) para administrar un gimnasio: socios, cobros, ventas de mostrador, inventario, usuarios y configuración.

Arquitectura por capas (hexagonal): el dominio es Java puro y no depende de nada; la aplicación define casos de uso y puertos; infraestructura y UI implementan esos puertos o los consumen.

## Estado actual

| Capa | Estado |
|---|---|
| `domain` | Entidades, value objects y reglas creados a partir de la especificación |
| `application` | `Authenticate`, `GetGymInfo`, `GetSettings`/`UpdateSettings`, el catálogo de métodos de pago y los planes (con su ahorro); `Authorizer`, `ValidationException` y `NotFoundException` en `shared/` |
| `infrastructure` | Conexión a PostgreSQL, migraciones (esquema, datos iniciales y planes), transacciones `SERIALIZABLE` con reintento, repositorios de usuarios, configuración, folios, métodos de pago y planes (más el conteo de cobros y ventas), y `Argon2PasswordHasher` |
| `ui` | Arranque, acceso con `Authenticate`, menú lateral y cabecera con permisos por rol, y piezas comunes en `ui/common/`. Las pantallas de negocio aún no existen: cada sección muestra qué le falta. Detalle y validación en [ui.md](ui.md) |

## Modelo de datos

Tablas, relaciones y reglas de la base: [modelo-de-datos.md](modelo-de-datos.md). El DBML de origen está en [modelo-de-datos.dbml](modelo-de-datos.dbml).

## UI

Cómo está armada la capa de UI, cómo se arranca y qué se comprobó en cada entrega: [ui.md](ui.md).

## Contratos

Qué envía la UI a cada caso de uso, qué recibe y cómo falla (implementados y planeados): [contratos.md](contratos.md).

## Estructura

Paquete base: `dev.gimnasio.sistemagimnasio`. Lo marcado como *(planeado)* aún no existe en disco.

```
gym-desk/
├── pom.xml
├── docker-compose.yml           PostgreSQL de desarrollo (contraseña en .env, ver .env.example)
├── .github/workflows/           pruebas en GitHub Actions
├── docs/                        este README, contratos, modelo de datos y UI
├── scripts/dev/                 crear-admin-dev.sh (administrador de desarrollo)
└── src/
    ├── main/java/dev/gimnasio/sistemagimnasio/
    │   ├── Launcher, App, Wiring        arranque y raíz de composición (conecta infraestructura y UI)
    │   │
    │   ├── domain/                      Java puro: reglas y conceptos del negocio
    │   │   ├── payment/                 Folio, CashPayment, PaymentMethod (lo comparten billing y sales)
    │   │   ├── shared/                  Money, DomainException
    │   │   ├── member/                  Member, Membership, Plan, MemberStatus, MembershipRules, Deactivation, Phone, Email
    │   │   ├── billing/                 MembershipPayment
    │   │   ├── sales/                   Sale, SaleLine
    │   │   ├── inventory/               Product, StockMovement, ProductCategory, MovementReason
    │   │   ├── settings/                GymSettings
    │   │   └── user/                    User, Role, Permission
    │   │
    │   ├── application/                 lo que el sistema hace
    │   │   ├── shared/                  Authorizer, ValidationException, NotFoundException
    │   │   ├── port/out/                lo que la aplicación pide al exterior: UserRepository, UserCredentials, PasswordHasher,
    │   │   │                            SettingsRepository, FolioSeriesRepository, PaymentMethodRepository, PlanRepository,
    │   │   │                            MembershipPaymentRepository, SaleRepository, TransactionRunner
    │   │   └── usecase/                 una carpeta por caso de uso o por área: el caso, su command, su result y sus excepciones
    │   │       ├── authenticate/        Authenticate, AuthenticateCommand, AuthenticationException
    │   │       ├── gyminfo/             GetGymInfo, GymInfo
    │   │       ├── settings/            GetSettings, UpdateSettings, SettingsView, LogoFormat
    │   │       ├── paymentmethods/      List, Create, Update, SetActive y Delete de métodos de pago
    │   │       ├── plans/               ListPlans, PreviewPlanSavings, Create, Update, SetActive y DeletePlan
    │   │       └── (planeado)           socios, cobro, venta, inventario, productos, usuarios y resumen del día (ver contratos.md)
    │   │
    │   ├── infrastructure/              implementa los puertos
    │   │   ├── persistence/             DatabaseConfig, Database (HikariCP + Flyway), JdbiTransactionRunner, Postgres*Repository
    │   │   ├── security/                Argon2PasswordHasher
    │   │   ├── printing/                (planeado) recibos y tickets
    │   │   └── backup/                  (planeado) pg_dump diario
    │   │
    │   └── ui/                          una carpeta por pantalla (controlador + ViewModel); ver docs/ui.md
    │       ├── GymDeskUi                entrada de la capa de UI
    │       ├── startup/                 conexión, migraciones y reintento
    │       ├── login/                   acceso (LoginController, LoginViewModel)
    │       ├── shell/                   menú lateral, cabecera, rutas por permiso y avisos de secciones pendientes
    │       ├── common/                  Background, ErrorReport, FieldErrors, Session, Formats, Inputs, Dialogs, Themes…
    │       └── (planeado)               una carpeta por pantalla de negocio: plans/, settings/, members/, billing/…
    │
    ├── main/resources/
    │   ├── db/migration/                V1__esquema_inicial.sql (todo el modelo), V2__configuracion_inicial.sql (reglas y datos iniciales), V3__planes.sql (referencia mensual)
    │   ├── fxml/  css/  fonts/  icons/  vistas, tema, Instrument Sans e iconos Lucide de la UI
    │   └── logback.xml                  registros a consola y archivo
    │
    ├── test/java/dev/gimnasio/sistemagimnasio/
    │   ├── domain/                      reglas del dominio, sin base ni JavaFX
    │   ├── application/                 casos de uso con repositorios falsos en memoria (application/fakes)
    │   ├── infrastructure/              repositorios y esquema contra PostgreSQL (Testcontainers) y el hasher
    │   └── ui/                          ViewModels y piezas comunes; capturas/ abre la UI real (opcional)
    └── test/resources/logback-test.xml  en las pruebas, solo avisos en consola
```

## Reglas de dependencia

- `domain` no importa nada de `application`, `infrastructure` ni `ui`, ni JavaFX ni librerías de persistencia.
- `application` depende solo de `domain`; define los puertos (`port/out`) que implementa `infrastructure`.
- `infrastructure` y `ui` dependen de `application` y `domain`, nunca entre sí.

## Tecnologías

En el `pom.xml`: JavaFX 21, PostgreSQL (driver JDBC), HikariCP, JDBI, Flyway, Argon2 (`argon2-jvm`), SLF4J 2 con Logback, JUnit 5 y Testcontainers.

La app corre en el classpath, sin `module-info.java`: Flyway y `argon2-jvm` no son módulos de Java, y `Launcher` arranca JavaFX sin necesitarlos.

## Desarrollo

```bash
./mvnw compile
DB_PASSWORD='<la de tu .env>' ./mvnw javafx:run
./mvnw test
```

Las pruebas corren también en GitHub Actions en cada PR y en cada cambio a `main` (`.github/workflows/pruebas.yml`). Allí, si las pruebas de PostgreSQL se omiten por un problema con Docker, el flujo falla en lugar de pasar en verde.

## Registros

Logback (`src/main/resources/logback.xml`) escribe en consola y en `~/.gym-desk/logs/gym-desk.log`, con un archivo por día (y otro más si pasa de 10 MB), 30 días de historial y 200 MB como máximo. La carpeta se cambia con la variable de entorno `GYM_DESK_LOG_DIR`. Ahí llegan los registros de la app (`System.Logger`, por `slf4j-jdk-platform-logging`) y los de HikariCP y Flyway. No se registran contraseñas.

## Base de datos (Docker)

```bash
cp .env.example .env    # la primera vez: escribe tu contraseña en .env
docker compose up -d
```

`docker-compose.yml` levanta solo PostgreSQL 16 (base y usuario `gimnasio`, volumen persistente `pgdata`). La contraseña sale de `POSTGRES_PASSWORD` en `.env`, que git ignora; sin ella, `docker compose` se niega a arrancar. El puerto 5432 solo escucha en `127.0.0.1`, así que otros equipos de la red no pueden conectarse. La app se ejecuta en el equipo con `./mvnw javafx:run`.

`POSTGRES_PASSWORD` solo se aplica cuando el volumen se crea por primera vez. Con una base que ya existe, la contraseña sigue siendo la de entonces; para cambiarla:

```bash
docker compose exec db psql -U gimnasio -d gimnasio -c '\password gimnasio'
```

La app lee la conexión de variables de entorno:

| Variable | Por defecto |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/gimnasio` |
| `DB_USER` | `gimnasio` |
| `DB_PASSWORD` | ninguno: es obligatoria y debe coincidir con la contraseña de la base (`POSTGRES_PASSWORD` de tu `.env`) |

`Database.migrate` aplica con Flyway las migraciones de `db/migration`; `App` la llama al arrancar. Las pruebas de `infrastructure/persistence` levantan su propio PostgreSQL con Testcontainers y se omiten si Docker no está corriendo (ver [ui.md](ui.md#pruebas)).
