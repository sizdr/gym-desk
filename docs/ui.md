# Capa de UI

Cómo está armada la UI de JavaFX, cómo se arranca y qué se comprobó en cada entrega. La referencia visual es el prototipo funcional «Gimnasio – Prototipo funcional» (artefacto de Claude); de él se toman colores, tipografía, iconos y espaciados, pero no su lógica ni sus datos simulados: cada dato que muestra una pantalla sale de un caso de uso (ver [contratos.md](contratos.md)).

## Arranque

```
Launcher ─▶ App (JavaFX Application) ─▶ GymDeskUi ─▶ pantalla de arranque
              │                                          │ (en segundo plano)
              └─ connect(): DatabaseConfig → Database.connect → Database.migrate → Wiring.useCases
```

- `App` y `Wiring` viven en el paquete raíz, fuera de `ui/`: son la raíz de composición, el único lugar que conoce a la vez la infraestructura y la UI. La UI recibe los casos de uso ya armados en `ui.common.UseCases` y nunca importa `infrastructure`.
- La conexión y las migraciones corren fuera del hilo de JavaFX. Si fallan (PostgreSQL apagado, falta `DB_PASSWORD`…), la pantalla de arranque muestra la causa con «Reintentar» y «Salir».
- Cerrar la ventana no espera a una conexión en curso: `App` no mantiene su bloqueo durante operaciones de red y, si una conexión termina después de pedir la salida, la cierra y la descarta.

## Estructura

| Paquete / recurso | Qué contiene |
|---|---|
| `ui/GymDeskUi` | Entrada de la capa: abre la ventana y decide qué pantalla se muestra. |
| `ui/common/` | Piezas compartidas: `Background` (casos de uso fuera del hilo de JavaFX), `ErrorReport` y `FieldErrors` (errores según el contrato), `Session`, `Formats` e `Inputs` (formato y lectura de importes y fechas), `Dialogs`, `FormDialog` (diálogo con formulario que no se cierra hasta que el caso de uso responde bien), `DataTable` (tabla de catálogo con botones de acción por fila), `StatusTag` (etiqueta de estado con color y punto), `Notifier` (avisos flotantes), `FxmlView`, `AppWindow`, `UseCases`. |
| `ui/startup/` | Pantalla de arranque: conexión, migraciones y reintento. |
| `ui/login/` | Acceso: `LoginController` y `LoginViewModel`. |
| `ui/shell/` | Menú lateral y cabecera (`ShellController`), secciones y permisos (`Route`, `Navigation`), la pantalla de cada sección que funciona (`Screen`), el aviso de las secciones pendientes (`PendingView`), los avisos flotantes y la confirmación al salir con cambios sin guardar. |
| `ui/settings/` | Configuración: `SettingsController` y `SettingsViewModel` (el formulario), `PaymentMethodsController` y `PaymentMethodsViewModel` (el catálogo de métodos de pago). |
| `ui/<pantalla>/` | Cada pantalla de negocio tiene su carpeta: controlador y, cuando hay formulario, su ViewModel. `GymDeskUi.screenFor` decide qué secciones ya tienen pantalla. |
| `resources/fxml/` | Vistas FXML, sin `fx:controller`: el controlador se construye con sus dependencias y se pasa a `FxmlView.load`. |
| `resources/css/app.css` | Tema de la app. |

Reglas que siguen todas las pantallas:

- Ningún caso de uso se llama desde el hilo de JavaFX: se usa `Background.run`.
- Los errores se tratan como dice el contrato: `ValidationException` marca cada campo (en el orden en que se validaron, el foco va al primero), `NotFoundException` avisa y recarga, `DomainException` muestra el mensaje tal cual y cualquier otro error muestra un mensaje genérico y se registra.
- La UI no calcula vigencias, totales ni estados. `Inputs` solo convierte texto: lo que no puede leer (por ejemplo `450,50`, con coma decimal) lo envía como `null` y el caso de uso responde con su propio mensaje.
- Una pantalla con formulario lleva la cuenta de lo que cambió respecto de lo guardado. Con cambios sin guardar, cambiar de sección, cerrar sesión o cerrar la ventana pide confirmación («Seguir editando» es la opción predeterminada).
- Los campos de una pantalla llevan un `id` propio (por ejemplo `cf-nombre` en Configuración): la pantalla comparte la escena con el menú, que ya usa ids como `gymName`.

## Tema y recursos visuales

`resources/css/app.css` traslada los tokens del prototipo: los colores se definen una vez como colores con nombre (`-gd-*`), el tema claro en `.root` y el nocturno en `.tema-oscuro`. `ui.common.Themes` pone la clase del tema en la raíz de cada ventana, incluidos los diálogos (cada uno tiene su propia escena); los menús y listas desplegables toman los colores del control que los abre.

| Tema | Estado |
|---|---|
| Claro | Disponible. Es el predeterminado. |
| Nocturno | Disponible. |
| Según el sistema | **Pendiente.** JavaFX 21 no informa si el sistema operativo está en modo oscuro; la API `Platform.Preferences` llegó en JavaFX 22. Actualizar JavaFX es un cambio de `pom.xml`, fuera de la rama de UI. Mientras tanto el selector ofrece solo claro y nocturno. |

El tema elegido se recuerda en las preferencias del usuario del sistema operativo (`java.util.prefs`, nodo `dev/gimnasio/sistemagimnasio/ui`), no en la base: es una preferencia de cada equipo.

Diferencias deliberadas con el prototipo:

- **Sin vidrio esmerilado.** JavaFX no tiene `backdrop-filter`. Menú, cápsulas y marcos son superficies opacas, como la variante «transparencia reducida» del propio prototipo; se conservan el fondo con color y la rejilla tenue.
- **Tamaños escritos en cada regla.** El CSS de JavaFX solo admite colores con nombre, no variables de tamaño; radios (14, 10, 7), espacios y tamaños de texto (11.5 a 30 px) se copian del prototipo en cada regla.
- **Pesos por familia.** JavaFX solo distingue normal y negrita al buscar una fuente por peso, así que los pesos 500 y 600 se piden por familia: `Instrument Sans Medium` y `Instrument Sans SemiBold` (clases `peso-500` y `peso-600`).
- **Pantalla mínima de 1024 × 680.** La app es de escritorio; el menú desplegable y las tablas con desplazamiento de la vista estrecha del prototipo no aplican.

### Recursos de terceros

| Recurso | Dónde | Licencia | Origen |
|---|---|---|---|
| Instrument Sans 400, 500, 600 y 700 | `resources/fonts/` | SIL Open Font License 1.1 (`fonts/OFL.txt`), sin nombres reservados | Fuente variable de [google/fonts](https://github.com/google/fonts/tree/main/ofl/instrumentsans). Los cortes estáticos se generaron con `fontTools.varLib.instancer` (`wght=400…700`, `wdth=100`, `--update-name-table`), porque JavaFX no maneja ejes variables. |
| Iconos Lucide (29) | `resources/icons/lucide.properties` | ISC (`icons/LICENSE-lucide.txt`, que incluye la nota MIT de Feather) | Los mismos símbolos del prototipo. `rect` y `circle` se convirtieron a trazos y los subtrazos se unieron en un solo `SVGPath` por icono. |

## Ejecutar

```bash
docker compose up -d
DB_PASSWORD=12345 ./mvnw javafx:run
```

El proyecto compila para Java 25; con un JDK predeterminado más antiguo, define `JAVA_HOME` con un JDK 25 o posterior.

### Administrador de desarrollo

La base recién migrada no tiene usuarios (el alta del primer administrador de una instalación real sigue pendiente en [contratos.md](contratos.md#pendientes-que-afectan-los-contratos)). Para entrar en desarrollo:

```bash
docker compose up -d
scripts/dev/crear-admin-dev.sh
```

El script pide nombre, usuario y contraseña (dos veces, sin mostrarla), compila el proyecto, calcula el hash Argon2id con el mismo `Argon2PasswordHasher` de la app (`scripts/dev/HashPassword.java`, que recibe la contraseña por variable de entorno y no como argumento) e inserta el usuario como administrador activo en el PostgreSQL de `docker-compose.yml`. Si el usuario ya existe, actualiza su nombre y contraseña y lo deja activo como administrador. Las credenciales no se guardan en ningún archivo del repositorio. Usa el `java` de `JAVA_HOME` si está definido.

## Pruebas

```bash
./mvnw test
```

Las pruebas de `infrastructure/persistence` levantan PostgreSQL con Testcontainers. Con Docker 29 o posterior se omiten en silencio: Testcontainers 1.20.4 pide la API de Docker 1.32 y esas versiones aceptan como mínimo la 1.40. **Ajuste temporal**, sin tocar la configuración del proyecto:

```bash
./mvnw test -Dapi.version=1.40
```

La solución permanente es actualizar Testcontainers en `pom.xml`, fuera del alcance de la rama de UI.

### Capturas

`CapturasUiTest` abre la UI real con casos de uso sobre repositorios en memoria (sin base), guarda capturas en ambos temas a 1200 × 760 y 1024 × 680 en `target/capturas/` y revisa el orden del foco con el teclado. Necesita pantalla, así que no corre con `./mvnw test`:

```bash
./mvnw test -Dtest=CapturasUiTest -Dui.capturas=true
```

## Validación por entrega

### Fase 0 · Arranque (10/11/2026)

Con JDK 26.0.2, Docker 29.0.3 y PostgreSQL 16 de `docker-compose.yml`:

| Comprobación | Resultado |
|---|---|
| `./mvnw test` | 444 pruebas: 0 fallos, 0 errores, 65 omitidas (Testcontainers, ver arriba) |
| `./mvnw test -Dapi.version=1.40` | 444 pruebas: 0 fallos, 0 errores, 0 omitidas |
| Pruebas de `ui/common` | 48: `Background`, `ErrorReport` (incluido el orden de los campos), `Formats`, `Inputs` (incluidos `450,50`, `1,2`, `1$2`, `12 34`), `Session` |
| Arranque con la base | Aplica V1–V3 en una base vacía y muestra el nombre del gimnasio y la fecha |
| Arranque sin `DB_PASSWORD` | Muestra «Falta la variable de entorno DB_PASSWORD.» con «Reintentar» y «Salir» |
| Cierre durante una conexión que no responde (servidor que acepta TCP y no contesta) | `stop()` termina en 1 ms; antes de la corrección esperaba a que fallara la conexión |

### Fase 1 · Entrega 1: base visual y vista de acceso (10/11/2026)

| Comprobación | Resultado |
|---|---|
| Pruebas de `ui/common` | 61: las de la Fase 0 más `ThemesTest` (predeterminado, recordado, valor desconocido, fallo al guardar) y `BrandAndIconsTest` (iniciales, iconos usados, licencias incluidas) |
| Capturas del acceso | `acceso-{claro,oscuro}-{1200,1024}.png` |
| Capturas de los 29 iconos | `iconos-{claro,oscuro}.png`: se revisaron uno por uno contra el prototipo |
| Teclado en el acceso | Al abrir, el foco está en Usuario. Tab: Usuario → Contraseña → Entrar → selector de tema → Usuario. El selector es un grupo de opciones: las flechas cambian de tema, Espacio no quita el elegido |
| Entrar | Todavía sin acción: se conecta en la entrega 2 |

### Fase 1 · Entrega 2: acceso funcional (10/11/2026)

`LoginViewModel` envía `AuthenticateCommand` con `Background` (fuera del hilo de JavaFX) y reparte la respuesta: errores por campo, aviso general o sesión iniciada. Mientras revisa, el botón muestra «Entrando…», no se puede volver a pulsar y los campos no cambian; un segundo envío (Enter repetido, doble clic) se ignora. Tras un intento fallido la contraseña se borra, como en el prototipo. Al entrar, la sesión guarda al usuario como `actor`; cerrar sesión lo descarta y vuelve al acceso.

| Comprobación | Resultado |
|---|---|
| `LoginViewModelTest` (9) | Campos vacíos juntos y en orden sin consultar al usuario; solo contraseña vacía; contraseña incorrecta (conserva el usuario, borra la contraseña); usuario inexistente con el mismo mensaje; usuario desactivado; acceso correcto; un intento nuevo borra el aviso anterior; envíos repetidos mientras revisa se ignoran (una sola consulta y un solo inicio de sesión); falla técnica con el mensaje genérico |
| Capturas en ambos temas y tamaños | `campos-vacios`, `credenciales-incorrectas`, `usuario-desactivado`, `entrando`, `sesion-iniciada` (provisional) |
| Teclado | Enter envía desde Usuario y desde Contraseña. Con campos vacíos el foco va a Usuario; con credenciales incorrectas, a Contraseña. Enter y clic repetidos durante la revisión no envían otra vez. Cerrar sesión vuelve al acceso con la contraseña vacía |
| Con PostgreSQL y Argon2 | `crear-admin-dev.sh` creó un administrador con hash `$argon2id$v=19$m=65536,t=3,p=1`; `Authenticate` con `PostgresUserRepository` lo aceptó con su contraseña y rechazó una incorrecta. El script rechaza un usuario con espacios y una contraseña corta. El usuario de prueba se borró después |
| App real | Arranca, migra y muestra el acceso con la base de desarrollo |

### Fase 1 · Entrega 3: menú, cabecera y permisos (10/11/2026)

Tras entrar se muestra el menú lateral y la cabecera del prototipo. Las secciones y sus grupos son los del prototipo (Tablero; Mostrador: Cobrar membresía, Punto de venta; Socios; Catálogo: Planes, Productos, Inventario; Administración: Usuarios, Configuración) y cada rol ve solo las que permite `Role.permissions()`. Empieza en Tablero si el rol lo puede ver y, si no, en Cobrar membresía. Ocultar una sección es solo presentación: los casos de uso vuelven a revisar el permiso.

Ninguna pantalla de negocio está implementada. Cada sección muestra su título y un aviso:

- **«Esta sección todavía no funciona»** con los casos de uso que faltan (Tablero, Cobrar membresía, Punto de venta, Socios, Productos, Inventario, Usuarios).
- **«Pantalla en preparación»** con los casos de uso que ya existen (Planes, Configuración). En Configuración, categorías de producto y motivos de inventario siguen siendo de la fase 2.
- La búsqueda global de socios se muestra con la etiqueta **«Pendiente»** y no se puede usar: falta `SearchMembers`.

No se usan datos simulados. La fecha de la cabecera sale de `GetGymInfo` (reloj del sistema) y se vuelve a leer al entrar. Las cuentas de demostración y las herramientas del prototipo para cambiar la fecha, simular lentitud o errores y restablecer datos no forman parte del producto.

| Comprobación | Resultado |
|---|---|
| `NavigationTest` (6) | Grupos y secciones de Administrador, Recepción y Caja; un grupo sin secciones visibles no aparece; sección inicial por rol; solo Planes y Configuración tienen ya sus casos de uso |
| `FormatsTest` | Fecha de la cabecera «mar 10 nov 2026», «mié 2 sep 2026», «sáb 1 ago 2026» |
| Capturas en ambos temas y tamaños | `tablero-administrador`, `configuracion-administrador`, `tablero-recepcion`, `cobro-caja`, `menu-desplazado-claro-1024` |
| Diálogo y lista desplegable | `dialogo-atajos-{claro,oscuro}`: el diálogo cambia de tema mientras está abierto. `lista-control-*` y `lista-abierta-*`: la lista abierta toma los colores y la fuente del tema |
| Teclado | Al entrar, el foco está en la sección inicial. Tab: secciones del menú en orden → Cerrar sesión → selector de tema → atajos → vuelta al menú (la búsqueda pendiente no es una parada). Espacio y Enter abren la sección con el foco, que se queda en el menú. «?» abre los atajos (salvo escribiendo en un campo) y Esc los cierra. Cerrar sesión con el teclado vuelve al acceso |
| Ventana al mínimo (1024 × 680) | Las secciones del administrador no caben: la lista se desplaza con barra visible, la sesión y Cerrar sesión quedan siempre a la vista y la sección con el foco se desplaza hasta verse |
| Suite completa (`-Dapi.version=1.40`) | 479 pruebas: 0 fallos, 0 errores; 6 omitidas (`CapturasUiTest`, que corre aparte con `-Dui.capturas=true`: 6 de 6) |

El paso completo «escribir credenciales en la ventana real» no se automatizó contra PostgreSQL: el arnés usa la UI real con repositorios en memoria y, por separado, se comprobó `Authenticate` con PostgreSQL y Argon2 (entrega 2).

### Fase 2 · Entrega 1: Configuración, datos del gimnasio (12/11/2026)

La sección Configuración ya funciona con `GetSettings` y `UpdateSettings`. Sigue al prototipo: un panel con secciones (título y explicación a la izquierda, campos a la derecha) y la barra «Tienes cambios sin guardar» fija abajo mientras se desplaza el formulario.

- **Datos del gimnasio:** nombre, dirección y teléfono (opcionales), logotipo y leyenda de recibos con su cuenta de caracteres. El logotipo se elige con el selector de archivos (solo PNG y JPG), se ve en una vista previa con el nombre y se puede quitar; si la imagen no se puede mostrar, la vista previa lo dice. Se lee como mucho un byte más que el máximo, así el caso de uso rechaza una imagen grande sin cargarla entera.
- **Membresías:** días de aviso de «por vencer» y plazo de baja automática. Solo admiten cifras; el rango lo revisa el caso de uso.
- **Cobros y folios:** moneda (MXN o USD, y la guardada si es otra) y prefijo de folio, que se escribe en mayúsculas. El ejemplo de folio usa el prefijo escrito con el ancho que dicta el sistema (`folioExample`). Con cobros o ventas, la moneda queda bloqueada y la ayuda dice cuántos hay.
- **Guardar:** Enter en cualquier campo o «Guardar cambios». Mientras guarda, el botón dice «Guardando…» y no se puede volver a pulsar. Los errores del caso de uso se marcan en su campo y el foco va al primero; un error que no es de un campo (permiso, sesión) aparece arriba. Al guardar, un aviso flotante lo confirma y el menú y el título de la ventana muestran enseguida el nombre y el logotipo nuevos (se vuelve a leer `GetGymInfo`).
- **Descartar** vuelve a lo guardado.
- **Pendiente para la entrega 2:** el catálogo de métodos de pago (sus casos de uso ya existen). Categorías de producto y motivos de inventario quedan para cuando existan sus casos de uso.
- **Fuera del producto:** «Herramientas de la demostración» y «Decisiones pendientes del cliente» del prototipo.

Planes pasa a ser la única sección con «Pantalla en preparación»; la captura de la entrega 3 que mostraba ese aviso en Configuración ahora es `planes-administrador`.

| Comprobación | Resultado |
|---|---|
| `SettingsViewModelTest` (20) | Carga sin cambios pendientes; opcionales vacíos; cambios y vuelta al valor original; descartar; guardar envía lo escrito y queda como guardado; sin cambios no guarda; errores en el orden del caso de uso sin guardar; texto que no es número; moneda bloqueada con su ayuda; monedas ofrecidas y la guardada; singular y plural; folio de ejemplo con el ancho del sistema; quitar el logotipo; formato de logotipo rechazado en su campo; elegir otra imagen quita el error; sin permiso; carga fallida y reintento; un segundo guardar mientras el primero sigue se ignora; al guardar, quien escucha el logotipo ya lo ve como guardado |
| `NavigationTest` | Configuración funciona, Planes tiene sus casos de uso y el resto los espera |
| Capturas en ambos temas y tamaños | `cargando`, `configuracion`, `cambios-sin-guardar`, `errores`, `guardado`, `moneda-bloqueada`; diálogo `salir-sin-guardar-{claro,oscuro}` |
| Teclado | Abrir la sección deja el foco en el menú. Tab recorre el formulario en orden de lectura: nombre → dirección → teléfono → subir imagen → leyenda → aviso → plazo → moneda → prefijo (y la barra, si está). Enter en un campo guarda. Con errores, el foco va al primero. Al salir con cambios, el diálogo abre en «Seguir editando» y Esc lo cierra sin perder lo escrito; «Salir sin guardar» cambia de sección y descarta |
| Ventana al mínimo (1024 × 680) | Si la vista previa y los botones del logotipo no caben juntos, los botones bajan a la siguiente línea en lugar de recortarse; la columna de títulos mide lo mismo en todas las secciones |
| Suite completa (`-Dapi.version=1.40`) | 501 pruebas: 0 fallos, 0 errores; 8 omitidas (`CapturasUiTest`, que corre aparte con `-Dui.capturas=true`: 8 de 8) |

Durante la revisión de las capturas se corrigieron: los botones del logotipo recortados a 1024 × 680 y la columna de títulos, que medía distinto en cada sección.

### Fase 2 · Entrega 2: Configuración, métodos de pago (12/11/2026)

Configuración suma el panel «Métodos de pago» con `ListPaymentMethods`, `CreatePaymentMethod`, `UpdatePaymentMethod`, `SetPaymentMethodActive` y `DeletePaymentMethod`.

- **Panel propio, aparte de «Guardar cambios».** En el prototipo los métodos son etiquetas que se guardan con el formulario; según el contrato, cada operación se guarda al momento en su transacción. Para no confundir, van en su panel y lo dice: «Los cambios de esta lista se aplican al momento».
- **Lista:** activos e inactivos por nombre; el efectivo lleva «Pide el efectivo recibido y calcula el cambio»; estado con etiqueta (texto, color y punto); filas inactivas atenuadas. Cada fila tiene **Editar**, **Desactivar** / **Activar** y **Eliminar**.
- **Agregar y editar** en un diálogo (nombre y «Es efectivo»). Enter envía y Esc cancela; el diálogo no se cierra con un error: «Ya existe uno con ese nombre.» o «No puedes cambiar si «Efectivo» es efectivo…» quedan en su campo con el foco. Mientras guarda, nada se puede pulsar.
- **Desactivar y activar** al momento, con aviso; el foco se queda en la misma fila. «Deja al menos un método de pago.» llega como aviso de error.
- **Eliminar** pide confirmación (abre en «Cancelar»). Uno ya usado se rechaza con el mensaje del caso de uso, que sugiere desactivarlo.
- **Si otro usuario lo cambió** (`NotFoundException`), se avisa y la lista se recarga.
- **Avisos flotantes** de éxito y de error (`Notifier`), abajo a la derecha como en el prototipo.

Piezas comunes nuevas para las siguientes pantallas: `DataTable`, `StatusTag`, `FormDialog`, `Notifier`, `Dialogs.confirmDanger` y los estilos de tabla, etiquetas de estado y casillas.

| Comprobación | Resultado |
|---|---|
| `PaymentMethodsViewModelTest` (17) | Carga con inactivos; agregar (cierra, avisa y aparece en su lugar); nombre repetido o vacío en su campo; editar; cambiar «es efectivo» de uno usado; cancelar; desactivar y activar; último activo; eliminar sin uso y usado; borrado por otro usuario al actuar y al editar; sin permiso; carga fallida; acciones y envíos repetidos ignorados; la lista sigue avisando sus cambios después de recolectar basura |
| Capturas en ambos temas y tamaños | `metodos-de-pago`, `agregado`, `eliminar-rechazado`; diálogos `dialogo-agregar`, `dialogo-error` y `confirmar-eliminar` en claro y oscuro |
| Teclado | Tab: «Agregar método» → Editar → Desactivar → Eliminar de cada fila, en orden. El diálogo abre con el foco en Nombre; Enter envía, el foco va al campo con error y Esc cancela. Espacio desactiva y el foco sigue en la fila. La confirmación de borrado abre en «Cancelar» |
| Suite completa (`-Dapi.version=1.40`) | 519 pruebas: 0 fallos, 0 errores; 9 omitidas (`CapturasUiTest`, que corre aparte: 9 de 9) |

Al revisar las capturas y las pruebas se corrigieron:

- **La tabla no se actualizaba tras agregar.** `items()` devolvía una vista de solo lectura nueva en cada llamada; esa vista escucha la lista con una referencia débil y, al recolectarse, la pantalla dejaba de recibir cambios. Ahora es una sola vista, con una prueba que falla con el código anterior.
- **Alineación de las filas.** El nombre quedaba arriba en las filas sin descripción.
- **Ayuda de «Es efectivo» recortada** en el diálogo.
- **Moneda recortada a 1024 × 680** («MXN · Peso mexica…»): la columna de la moneda es un poco más ancha que la del prefijo.
