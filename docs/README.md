# Sistema Gimnasio

Aplicación de escritorio (JavaFX 21, Java 25, Maven) para administrar un gimnasio: socios, cobros, ventas de mostrador, inventario, usuarios y configuración.

Arquitectura por capas (hexagonal): el dominio es Java puro y no depende de nada; la aplicación define casos de uso y puertos; infraestructura y UI implementan esos puertos o los consumen.

## Estado actual

| Capa | Estado |
|---|---|
| `domain` | Entidades, value objects y reglas creados a partir de la especificación |
| `application` | `Authenticate`, `GetGymInfo`, `GetSettings`/`UpdateSettings`, el catálogo de métodos de pago, los planes (con su ahorro) y los usuarios (con el primer administrador); `Authorizer`, `ValidationException` y `NotFoundException` en `shared/` |
| `infrastructure` | Conexión a PostgreSQL, migraciones (esquema y datos iniciales), repositorios de usuarios, configuración y métodos de pago, y `Argon2PasswordHasher` |
| `ui` | Arranque (conexión, migraciones y reintento) y piezas comunes en `ui/common/`; detalle y validación en [ui.md](ui.md) |

## Modelo de datos

Tablas, relaciones y reglas de la base: [modelo-de-datos.md](modelo-de-datos.md). El DBML de origen está en [modelo-de-datos.dbml](modelo-de-datos.dbml).

## UI

Cómo está armada la capa de UI, cómo se arranca y qué se comprobó en cada entrega: [ui.md](ui.md).

## Contratos

Qué envía la UI a cada caso de uso, qué recibe y cómo falla (implementados y planeados): [contratos.md](contratos.md).

## Estructura

Paquete base: `dev.gimnasio.sistemagimnasio`. Lo marcado como *(planeado)* aún no existe en disco.

```
sistema-gimnasio/
├── pom.xml
├── docs/README.md
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
    │   │   ├── port/out/                (planeado) interfaces que la aplicación necesita del exterior
    │   │   │   ├── MemberRepository, MembershipPaymentRepository, SaleRepository
    │   │   │   ├── ProductRepository, UserRepository, SettingsRepository
    │   │   │   └── FolioGenerator, AppClock, PasswordHasher, ReceiptPrinter
    │   │   └── usecase/                 una carpeta por caso de uso: el caso, su command, su result y sus excepciones
    │   │       ├── authenticate/        Authenticate, AuthenticateCommand, AuthenticationException
    │   │       ├── (planeado) chargemembership/, sellatcounter/
    │   │       ├── (planeado) deactivatemember/, reactivatemember/, voidmembershippayment/
    │   │       ├── (planeado) registerinventorymovement/, daysummary/
    │   │       └── (planeado) plancatalog/, productcatalog/, useradmin/   (CRUD agrupado por área)
    │   │
    │   ├── infrastructure/              implementa los puertos
    │   │   ├── persistence/             DatabaseConfig, Database (HikariCP + Flyway), PostgresUserRepository
    │   │   ├── security/                Argon2PasswordHasher
    │   │   ├── printing/                (planeado) recibos y tickets
    │   │   ├── backup/                  (planeado) pg_dump diario
    │   │   └── time/                    (planeado) reloj del sistema (implementa AppClock)
    │   │
    │   └── ui/                          una carpeta por pantalla (controlador + ViewModel); ver docs/ui.md
    │       ├── GymDeskUi                entrada de la capa de UI
    │       ├── startup/                 conexión, migraciones y reintento
    │       ├── common/                  Background, ErrorReport, FieldErrors, Session, Formats, Inputs, Dialogs…
    │       ├── login/  dashboard/  billing/  sales/  members/                    (planeado)
    │       └── plans/  products/  inventory/  users/  settings/                   (planeado)
    │
    ├── main/resources/
    │   ├── db/migration/                V1__esquema_inicial.sql (todo el modelo), V2__configuracion_inicial.sql (reglas y datos iniciales), V3__planes.sql (referencia mensual)
    │   ├── i18n/messages_es.properties  (planeado)
    │   ├── fxml/   css/app.css           vistas y tema de la UI
    │   └── logback.xml                   (planeado)
    │
    └── test/java/dev/gimnasio/sistemagimnasio/
        ├── domain/member/               MembershipRulesTest
        ├── application/usecase/         pruebas con repositorios falsos en memoria, en la misma carpeta que su caso de uso
        └── infrastructure/persistence/  pruebas contra PostgreSQL (Testcontainers)
```

## Reglas de dependencia

- `domain` no importa nada de `application`, `infrastructure` ni `ui`, ni JavaFX ni librerías de persistencia.
- `application` depende solo de `domain`; define los puertos (`port/out`) que implementa `infrastructure`.
- `infrastructure` y `ui` dependen de `application` y `domain`, nunca entre sí.

## Tecnologías

En el `pom.xml`: JavaFX 21, PostgreSQL (driver JDBC), HikariCP, JDBI, Flyway, Argon2 (`argon2-jvm`), JUnit 5 y Testcontainers. Falta Logback.

La app corre en el classpath, sin `module-info.java`: Flyway y `argon2-jvm` no son módulos de Java, y `Launcher` arranca JavaFX sin necesitarlos.

## Desarrollo

```bash
./mvnw compile
./mvnw javafx:run
```

## Base de datos (Docker)

```bash
docker compose up -d
```

`docker-compose.yml` levanta solo PostgreSQL 16 (base y usuario `gimnasio`, puerto 5432, volumen persistente `pgdata`). La app se ejecuta en el equipo con `./mvnw javafx:run`.

La app lee la conexión de variables de entorno:

| Variable | Por defecto |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/gimnasio` |
| `DB_USER` | `gimnasio` |
| `DB_PASSWORD` | ninguno: es obligatoria y debe coincidir con `POSTGRES_PASSWORD` del compose |

`Database.migrate` aplica con Flyway las migraciones de `db/migration`; `App` la llama al arrancar. Las pruebas de `infrastructure/persistence` levantan su propio PostgreSQL con Testcontainers y se omiten si Docker no está corriendo o si su API no es compatible (ver [ui.md](ui.md#pruebas)).
