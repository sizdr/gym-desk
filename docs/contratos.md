# Contratos entre capas

Qué promete cada caso de uso a quien lo consume (la UI): qué tiene que enviar, qué recibe y cómo falla. Los resultados siguen al prototipo funcional, de modo que cada dato que una pantalla muestra sale de aquí, sin cálculos en la UI. Donde el prototipo y la especificación no coinciden, manda la especificación. Si un contrato cambia, se actualiza aquí.

```
ui ──(actor + command)──▶ caso de uso ──(puerto de salida)──▶ infrastructure
   ◀──(resultado o excepción)──      ◀──────(datos)──────
```

Están implementados `Authenticate`, `GetGymInfo`, Configuración, el catálogo de métodos de pago y Planes. El resto es el contrato acordado para construir la UI y los casos de uso en paralelo. Al final están los cambios que estos contratos piden al código actual.

## Reglas generales

### Forma de las llamadas

| Tipo | Firma | Ejemplo |
|---|---|---|
| Acción | `Result execute(User actor, XCommand command)` | `chargeMembership.execute(actor, new ChargeMembershipCommand(...))` |
| Consulta con filtros | `Result execute(User actor, XQuery query)` | `searchMembers.execute(actor, new SearchMembersQuery("ana", null, MemberSort.VENCIMIENTO, null))` |
| Consulta sin filtros | `Result execute(User actor)` | `getDaySummary.execute(actor)` |

- **`actor`** es el `User` que devolvió `Authenticate` y que la UI guarda mientras dure la sesión. Cada caso de uso revisa con él el permiso que necesita y lo registra como quien hizo la operación. No lo reciben `Authenticate` ni `GetGymInfo`.
- **Commands y queries** son `record` con los datos tal como se capturaron. El caso de uso recorta espacios, trata `null` como vacío y valida.
- **Resultados** son `record` de solo lectura, listos para mostrarse. La UI no calcula vigencias, totales, cambio, estados, días restantes ni niveles de existencia.
- **«Hoy»** lo decide el reloj del sistema, nunca la UI.

### Vista previa y confirmación

Cobro, venta y movimiento de inventario tienen una consulta `Preview…` y un comando que confirma.

- La vista previa **no lanza excepciones por datos incompletos o incorrectos**: devuelve todo lo que ya se puede calcular, más `errors` (`Map<String, String>`: campo → mensaje) y `ready` (`true` si se puede confirmar). Así el resumen se dibuja mientras se llena el formulario.
- Solo lanza por permiso o usuario desactivado.
- El comando vuelve a validar todo y, si algo falla, lanza `ValidationException` con los mismos campos y mensajes.

### Operaciones que no se repiten

`ChargeMembership`, `Sell` y `RegisterStockMovement` reciben `operationId` (`String`), que la UI genera al abrir cada formulario. Si la misma operación llega dos veces (doble clic, reintento), el caso de uso no la registra otra vez: devuelve el resultado original con `repeated = true`.

### Fechas de vigencia

- El vencimiento se calcula sumando la duración del plan a la fecha de inicio: `end = start + duración`. Un plan de 30 días que empieza el 01/11 vence el 01/12.
- Un socio está cubierto mientras `hoy <= end`.
- Al renovar antes de vencer, el nuevo periodo empieza el día en que termina el actual (`start = end` anterior), así que pagar por adelantado no hace perder días.
- La baja ocurre al cumplirse el plazo: el socio pasa a `BAJA` el día `end + plazo de baja`. Su fecha de baja es `end`.

### Tipos de entrada

| Dato | Tipo | Notas |
|---|---|---|
| Identificadores | `long` | Los que la UI recibió en una consulta anterior. |
| Importes | `Money` | Dos decimales, nunca negativo. La UI convierte el texto capturado. |
| Fechas | `LocalDate` | La UI muestra día/mes/año. |
| Fecha y hora | `LocalDateTime` | Las asigna el sistema; la UI nunca las envía. |
| Opcionales | campo `null` | Se indica como *opcional* en cada tabla. |

### Errores

| Excepción | Cuándo | Qué hace la UI |
|---|---|---|
| `ValidationException` | Un dato del formulario falta, es incorrecto o ya no es válido (plan inactivo, existencia insuficiente…). Trae `Map<String, String> errors`: campo → mensaje. | Marca cada campo con su mensaje. |
| `NotFoundException` | Un id ya no existe (otro usuario lo cambió). | Muestra el mensaje y recarga la pantalla. |
| `DomainException` | Una regla que no corresponde a un campo: permiso, usuario desactivado, quedarse sin administradores… | Muestra el mensaje tal cual. |
| Otras | Fallos técnicos: base caída, impresora desconectada. | Mensaje genérico de error inesperado. |

`ValidationException` y `NotFoundException` extienden `DomainException`, así que atrapar `DomainException` basta para mostrar cualquier mensaje de negocio.

Si el actor no tiene el permiso, cualquier caso de uso lanza `DomainException` con «Tu rol (Caja) no tiene permiso para esta acción.». Si fue desactivado durante la sesión: «Tu sesión no es válida. Vuelve a iniciar sesión.». En las tablas de abajo no se repiten.

### Datos sensibles

Contraseñas y hashes no aparecen en `toString()` (se muestran como `***`) y ningún resultado los incluye.

## Tipos compartidos

### Enumeraciones

| Tipo | Valores |
|---|---|
| `MemberStatus` | `SIN_MEMBRESIA`, `VIGENTE`, `POR_VENCER`, `VENCIDA`, `BAJA` |
| `ChargeKind` | `PRIMERA` (empieza hoy), `RENOVACION_ANTICIPADA` (empieza el día en que termina la vigencia actual), `RENOVACION_VENCIDA` (empieza hoy), `REACTIVACION` (estaba en baja; empieza hoy) |
| `StockLevel` | `AGOTADO` (existencia 0), `BAJO` (mayor que 0 e igual o menor al mínimo), `SUFICIENTE` |
| `MovementType` | `ENTRADA`, `SALIDA`, `AJUSTE` |
| `MemberSort` | `VENCIMIENTO`, `VENCIMIENTO_DESC`, `NOMBRE` |

### `MemberRow`

Un socio en una lista (Socios, búsqueda rápida, tablero, cobro).

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `long` | |
| `fullName` | `String` | |
| `phone` | `String` | 10 dígitos, sin formato. |
| `email` | `String` | *opcional* |
| `status` | `MemberStatus` | |
| `planName` | `String` | *opcional*; plan de la última membresía. |
| `membershipEnd` | `LocalDate` | *opcional*; vencimiento de la última membresía. `null` si nunca ha pagado. |
| `daysLeft` | `Integer` | *opcional*; solo cubiertos: días de hoy a `membershipEnd` (0 = vence hoy). |
| `daysOverdue` | `Integer` | *opcional*; solo `VENCIDA` y `BAJA`: días desde `membershipEnd`. |
| `deactivatesOn` | `LocalDate` | *opcional*; solo `VENCIDA`: día en que pasará a baja si no renueva (`membershipEnd` + plazo de baja). |
| `deactivationDate` | `LocalDate` | *opcional*; solo `BAJA`: igual a `membershipEnd`. |

Con `status`, `daysLeft`, `daysOverdue` y las fechas, la UI arma «Quedan 12 días», «Vence mañana», «Venció hace 5 días» o «Baja desde el 30/09» sin calcular nada.

### `PaymentRow`

Un cobro de membresía.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `long` | |
| `folio` | `String` | |
| `dateTime` | `LocalDateTime` | |
| `memberId` | `long` | |
| `memberName` | `String` | |
| `planName` | `String` | |
| `start`, `end` | `LocalDate` | Periodo pagado. |
| `kind` | `ChargeKind` | Para marcar «Reactivación» o «Renovación anticipada». |
| `paymentMethodName` | `String` | |
| `amount` | `Money` | |
| `cashierName` | `String` | Quién atendió. |

### `TransactionRow`

Un cobro o una venta en la lista combinada del tablero.

| Campo | Tipo | Notas |
|---|---|---|
| `type` | `TransactionType` | `COBRO` o `VENTA`; con `id` abre el recibo o el ticket. |
| `id` | `long` | |
| `folio` | `String` | |
| `dateTime` | `LocalDateTime` | |
| `concept` | `String` | «Membresía mensual · Ana López» o «Venta de mostrador · 3 piezas». |
| `paymentMethodName` | `String` | |
| `amount` | `Money` | |

### `DeactivationView`

Una baja de la ficha del socio, abierta o ya reactivada.

| Campo | Tipo | Notas |
|---|---|---|
| `expiredPlanName` | `String` | Plan que venció. |
| `date` | `LocalDate` | Fecha de baja (= último vencimiento). |
| `daysWithoutRenewal` | `int` | Hasta la reactivación o, si sigue abierta, hasta hoy. |
| `reactivatedOn` | `LocalDate` | *opcional* |
| `reactivationFolio` | `String` | *opcional*; folio del cobro que la reactivó. |

### `SavingsView`

Ahorro de un plan frente a pagar mes a mes con su plan mensual de referencia.

| Campo | Tipo | Notas |
|---|---|---|
| `status` | `SavingsStatus` | `NO_APLICA` (dura 30 días o menos), `NO_DISPONIBLE`, `SIN_AHORRO`, `AHORRO`. |
| `amount` | `Money` | *opcional*; con `AHORRO`. |
| `months` | `Integer` | *opcional*; meses equivalentes (365 días = 12). |
| `referencePlanName` | `String` | *opcional* |
| `monthlyTotal` | `Money` | *opcional*; precio de referencia × meses. |
| `percent` | `Integer` | *opcional*; porcentaje ahorrado. |
| `reason` | `String` | *opcional*; con `NO_DISPONIBLE`: «el plan no tiene un plan mensual de referencia», «el plan de referencia (Mensual) está inactivo», «la referencia no dura 30 días» o «45 días no equivalen a meses completos». En la vista previa, sin precio: «escribe un precio mayor que cero para calcular el ahorro». |

### Elementos de catálogo

Cada catálogo tiene su propio record, con solo los campos que le aplican:

| Record | Campos |
|---|---|
| `PaymentMethodItem` | `id`, `name`, `cash` (`true` si es efectivo y pide lo recibido), `active` |
| `MovementReasonItem` | `id`, `name`, `system` (los que usa el sistema, como «Venta» o «Existencia inicial»; no se ofrecen, editan ni desactivan), `active` |
| `ProductCategoryItem` | `id`, `name`, `active` |

### `GymHeader`

Encabezado de recibos y tickets: `gymName`, `logo` (`byte[]`, *opcional*), `address`, `phone`, `receiptFooter` (los tres *opcionales*).

## Acceso

### `Authenticate` · Iniciar sesión · *implementado*

Sin permiso: es lo que abre la sesión.

**Entrada:** `AuthenticateCommand`

| Campo | Tipo | Reglas |
|---|---|---|
| `username` | `String` | Se le quitan los espacios de los extremos. No distingue mayúsculas. |
| `password` | `String` | Se usa tal cual, sin recortar espacios. |

**Salida:** `User(id, name, username, role, active)`. La UI lo guarda como `actor`, muestra nombre y rol en el menú y usa `user.role().can(Permission.X)` para mostrar u ocultar áreas.

**Errores**, en el orden en que se revisan:

| Situación | Excepción | Mensaje |
|---|---|---|
| Usuario vacío | `ValidationException` (`username`) | Escribe tu nombre de usuario. |
| Contraseña vacía | `ValidationException` (`password`) | Escribe tu contraseña. |
| El usuario no existe o la contraseña no coincide | `AuthenticationException` | Usuario o contraseña incorrectos. Revísalos e inténtalo de nuevo. |
| Credenciales correctas, pero el usuario está desactivado | `AuthenticationException` | Tu usuario está desactivado. Pide a un administrador que lo active. |

Los dos campos vacíos se reportan juntos. Usuario inexistente y contraseña incorrecta dan el mismo mensaje para no revelar qué nombres de usuario existen.

**Cerrar sesión** no es un caso de uso: la UI descarta el `actor` y vuelve al login.

### `GetGymInfo` · Marca y datos globales · *implementado*

Sin `actor` ni permiso: se usa también en la pantalla de acceso, antes de iniciar sesión.

**Salida:** `GymInfo`

| Campo | Tipo | Uso |
|---|---|---|
| `gymName` | `String` | Acceso, menú lateral. |
| `logo` | `byte[]` | *opcional*; sin logotipo la UI muestra las iniciales. |
| `currency` | `Currency` | Formato de todos los importes. |
| `today` | `LocalDate` | Fecha del encabezado. |
| `warningDays` | `int` | Textos como «Aviso de 7 días». |
| `autoDeactivationDays` | `int` | Textos como «Pasan a baja a los 30 días». |

## Resumen del día

### `GetDaySummary` · Tablero

Permiso: `VIEW_DAILY_SUMMARY`.

**Entrada:** solo `actor`.

**Salida:** `DaySummary`

| Campo | Tipo | Qué es |
|---|---|---|
| `today` | `LocalDate` | |
| `weekEnd` | `LocalDate` | Domingo de esta semana. |
| `activeMembers` | `int` | `VIGENTE` + `POR_VENCER`. |
| `expiringCount` | `int` | `POR_VENCER`. |
| `expiringThisWeekCount` | `int` | Cubiertos cuyo vencimiento cae de hoy a `weekEnd`. |
| `expiredCount` | `int` | `VENCIDA` (aún no en baja). |
| `membershipIncomeToday` | `Money` | Suma de los cobros de hoy. |
| `paymentsToday` | `int` | Cuántos cobros hubo hoy. |
| `salesIncomeToday` | `Money` | Suma de las ventas de hoy. |
| `salesToday` | `int` | Cuántas ventas hubo hoy. |
| `expiring` | `List<MemberRow>` | `POR_VENCER`, del vencimiento más próximo al más lejano. |
| `expired` | `List<MemberRow>` | `VENCIDA`, del vencimiento más reciente al más antiguo. Cada fila trae `deactivatesOn`. |
| `recentTransactions` | `List<TransactionRow>` | Últimos 8 cobros y ventas mezclados, el más reciente primero. |

## Socios

### `SearchMembers` · Buscar, filtrar y ordenar

Permiso: `VIEW_MEMBERS`. Lo usan la pantalla Socios, la búsqueda rápida del encabezado y el buscador del cobro.

**Entrada:** `SearchMembersQuery`

| Campo | Tipo | Reglas |
|---|---|---|
| `text` | `String` | *opcional*. Busca en nombre (parcial, sin mayúsculas ni acentos), correo y, con 3 dígitos o más, teléfono. Vacío lista a todos. |
| `status` | `MemberStatus` | *opcional*. `null` no filtra. |
| `sort` | `MemberSort` | *opcional*; por defecto `VENCIMIENTO`. |
| `limit` | `Integer` | *opcional*. La búsqueda rápida pide 7; `null` devuelve todos. |

Órdenes:

- `VENCIMIENTO`: primero los cubiertos, del vencimiento más próximo al más lejano; luego vencidos y bajas, del vencimiento más reciente al más antiguo; al final, sin membresía.
- `VENCIMIENTO_DESC`: del vencimiento más lejano al más antiguo; sin membresía al final.
- `NOMBRE`: alfabético.

Los empates se ordenan por nombre.

**Salida:** `MemberSearchResult`

| Campo | Tipo | Notas |
|---|---|---|
| `members` | `List<MemberRow>` | Hasta `limit`. |
| `matchCount` | `int` | Cuántos coinciden con `text` y `status`, antes de aplicar `limit` («Mostrando 7 de 23»). |
| `totalMembers` | `int` | Todos los socios registrados (pestaña «Todos»). |
| `countByStatus` | `Map<MemberStatus, Integer>` | Total de cada estado entre **todos** los socios, sin aplicar `text`. Incluye los cinco estados aunque valgan 0. |

### `GetMemberDetail` · Ficha del socio

Permiso: `VIEW_MEMBERS`.

**Entrada:** `long memberId`.

**Salida:** `MemberDetail`

| Campo | Tipo | Notas |
|---|---|---|
| `member` | `MemberRow` | Datos y estado actual. |
| `joinDate` | `LocalDate` | |
| `lastPeriod` | `MembershipView` | *opcional*. `planName`, `start`, `end` de la última membresía. |
| `payments` | `List<PaymentRow>` | Historial completo, el más reciente primero. |
| `deactivations` | `List<DeactivationView>` | Bajas y reactivaciones, la más reciente primero. Se calculan con el plazo de baja vigente, así que cambiar el plazo recalcula también las anteriores. |
| `editable` | `boolean` | `true` si el actor puede editar (`EDIT_MEMBERS`). |

**Errores:** `NotFoundException` «El socio no existe.».

### `RegisterMember` · Alta de socio

Permiso: `EDIT_MEMBERS`.

**Entrada:** `RegisterMemberCommand`

| Campo | Tipo | Reglas |
|---|---|---|
| `fullName` | `String` | Obligatorio, al menos 3 letras. Se compactan los espacios. |
| `phone` | `String` | Obligatorio. Se quitan espacios y guiones; deben quedar 10 dígitos. |
| `email` | `String` | *opcional*. Formato válido y que no tenga otro socio (sin distinguir mayúsculas). Se guarda en minúsculas. |
| `joinDate` | `LocalDate` | Obligatoria, no posterior a hoy. |

**Salida:** `MemberRow` del socio creado, en `SIN_MEMBRESIA`. Para «Guardar y cobrar», la UI abre después `ChargeMembership` con su `id`, donde se eligen el plan y el inicio de la primera membresía. El alta y el cobro son operaciones separadas: si el cobro no se confirma, el socio queda sin membresía.

**Errores** (`ValidationException`):

| Campo | Mensaje |
|---|---|
| `fullName` | Escribe el nombre completo. · Escribe el nombre completo, con al menos 3 letras. |
| `phone` | Escribe el teléfono a 10 dígitos. · El teléfono debe tener 10 dígitos; tiene 9. Ejemplo: 555 010 2233. |
| `email` | Escribe un correo válido, por ejemplo nombre@ejemplo.com, o déjalo vacío. · Ya hay un socio con este correo: Ana López. Usa otro o déjalo vacío. |
| `joinDate` | Escribe la fecha de alta. · La fecha de alta no puede ser posterior a hoy (09/11/2026). |

### `UpdateMember` · Corregir datos

Permiso: `EDIT_MEMBERS`. Solo datos personales: la membresía cambia únicamente con un cobro.

**Entrada:** `UpdateMemberCommand(memberId, fullName, phone, email, joinDate)` con las mismas reglas que el alta.

**Salida:** `MemberDetail` actualizado.

**Errores:** los de `RegisterMember` y `NotFoundException` «El socio no existe.».

### Bajas

No hay una pantalla propia: la especificación muestra las bajas en el filtro `BAJA` de `SearchMembers` (con `deactivationDate`) y, con sus reactivaciones, en la ficha (`MemberDetail.deactivations`). La pantalla «Bajas» del prototipo queda fuera de alcance.

**Reactivar** no es un caso de uso: es llevar al socio a `ChargeMembership`.

## Cobro de membresías

### `ListChargeOptions` · Opciones del formulario de cobro

Permiso: `CHARGE_MEMBERSHIP`.

**Salida:** `ChargeOptions`

| Campo | Tipo | Notas |
|---|---|---|
| `plans` | `List<PlanOption>` | Solo activos. Cada uno: `id`, `name`, `durationDays`, `price`, `savings` (`SavingsView`). |
| `paymentMethods` | `List<PaymentMethodItem>` | Solo activos. |

### `PreviewMembershipCharge` · Resumen antes de cobrar

Permiso: `CHARGE_MEMBERSHIP`. No guarda nada ni lanza por datos incompletos (ver *Vista previa y confirmación*).

**Entrada:** `ChargeMembershipCommand` (el mismo del cobro; puede venir incompleto).

**Salida:** `ChargePreview`

| Campo | Tipo | Notas |
|---|---|---|
| `member` | `MemberRow` | *opcional*; socio elegido con su estado y vigencia actual. |
| `plan` | `PlanOption` | *opcional*; plan elegido. |
| `newStart`, `newEnd` | `LocalDate` | *opcionales*; con socio y plan. |
| `kind` | `ChargeKind` | *opcional*; la UI explica con él la regla aplicada. |
| `total` | `Money` | Precio del plan, o 0 sin plan. |
| `paymentMethod` | `PaymentMethodItem` | *opcional* |
| `cashReceived` | `Money` | *opcional* |
| `change` | `Money` | *opcional*; si lo recibido alcanza. |
| `missing` | `Money` | *opcional*; si lo recibido no alcanza («Faltan $50.00»). |
| `errors` | `Map<String, String>` | Ver errores de `ChargeMembership`. |
| `ready` | `boolean` | |

### `ChargeMembership` · Cobrar membresía

Permiso: `CHARGE_MEMBERSHIP`.

**Entrada:** `ChargeMembershipCommand`

| Campo | Tipo | Reglas |
|---|---|---|
| `operationId` | `String` | Obligatorio. Ver *Operaciones que no se repiten*. |
| `memberId` | `Long` | Obligatorio. |
| `planId` | `Long` | Plan activo. |
| `paymentMethodId` | `Long` | Método activo. |
| `cashReceived` | `Money` | Obligatorio si el método es efectivo, y mayor o igual al total. Se ignora en los demás métodos. |
| `start` | `LocalDate` | *opcional*; solo en la primera membresía (`PRIMERA`), para elegir su inicio. No anterior a la fecha de alta. `null` empieza hoy. En los demás casos se ignora: el inicio lo calcula el sistema. |

No hay campo de monto: se cobra siempre el precio del plan. El folio se asigna al confirmar.

**Salida:** `ChargeMembershipResult`

| Campo | Tipo |
|---|---|
| `payment` | `PaymentRow` (folio, fecha y hora, socio, plan, periodo, tipo de cobro, método, monto, quién atendió) |
| `durationDays` | `int` |
| `cashReceived`, `change` | `Money` (*opcionales*) |
| `repeated` | `boolean` |

**Errores** (`ValidationException`):

| Campo | Mensaje |
|---|---|
| `memberId` | Elige al socio que va a pagar. |
| `planId` | Elige un plan. · Ese plan está inactivo; elige otro. |
| `paymentMethodId` | Elige el método de pago. · Ese método de pago ya no está disponible. |
| `cashReceived` | Escribe cuánto efectivo recibiste. · El efectivo recibido no cubre el total. |
| `start` | El inicio no puede ser anterior a la fecha de alta (03/11/2026). |

`NotFoundException` «El socio no existe.» si el socio fue borrado mientras tanto.

### `GetMembershipReceipt` / `PrintMembershipReceipt` · Recibo

Permiso: `CHARGE_MEMBERSHIP`. Se abre al terminar un cobro y desde cualquier folio del tablero o de la ficha.

**Entrada:** `long paymentId`.

**Salida de `GetMembershipReceipt`:** `MembershipReceipt(header, payment, durationDays, cashReceived, change)`, con `header` de tipo `GymHeader`. `PrintMembershipReceipt` imprime ese mismo documento y no devuelve nada.

**Errores:** `NotFoundException` «El cobro no existe.»; los fallos de impresora llegan como error técnico.

## Venta de mostrador

El ticket en armado (agregar, cambiar cantidad, quitar, vaciar) vive en la UI. El sistema interviene para listar, previsualizar y cobrar.

### `GetSaleOptions` · Opciones del punto de venta

Permiso: `SELL`.

**Salida:** `SaleOptions(categories, paymentMethods)`: `List<ProductCategoryItem>` y `List<PaymentMethodItem>`, con solo activos.

### `SearchProductsForSale` · Buscar productos

Permiso: `SELL`.

**Entrada:** `SearchProductsQuery(text, categoryId)`, ambos *opcionales*. `text` busca en clave, nombre y categoría.

**Salida:** `List<ProductOption>`, solo productos activos, por nombre.

| Campo | Tipo |
|---|---|
| `id` | `long` |
| `code` | `String` |
| `name` | `String` |
| `categoryName` | `String` |
| `price` | `Money` |
| `stock` | `int` |
| `minStock` | `int` |
| `level` | `StockLevel` |

### `PreviewSale` · Totales del ticket

Permiso: `SELL`. No guarda nada ni lanza por datos incompletos.

**Entrada:** `SellCommand` (el mismo de la venta).

**Salida:** `SalePreview`

| Campo | Tipo | Notas |
|---|---|---|
| `lines` | `List<SaleLineView>` | `productId`, `code`, `name`, `unitPrice`, `quantity`, `subtotal`, `stock`. Con `stock` la UI deshabilita «+» al llegar a la existencia. |
| `pieces` | `int` | Suma de cantidades. |
| `total` | `Money` | |
| `paymentMethod` | `PaymentMethodItem` | *opcional* |
| `cashReceived`, `change`, `missing` | `Money` | *opcionales*, igual que en el cobro. |
| `errors` | `Map<String, String>` | |
| `ready` | `boolean` | |

### `Sell` · Cobrar la venta

Permiso: `SELL`.

**Entrada:** `SellCommand`

| Campo | Tipo | Reglas |
|---|---|---|
| `operationId` | `String` | Obligatorio. |
| `lines` | `List<SaleLineInput>` | Al menos una. Cada una: `productId` y `quantity` (mayor que 0). Si un producto aparece dos veces, se suman las cantidades. |
| `paymentMethodId` | `Long` | Método activo. |
| `cashReceived` | `Money` | Igual que en `ChargeMembership`. |

No hay campo de precio: se usa el precio actual del producto. Cada línea descuenta existencia y genera su movimiento de inventario, vinculado al folio.

**Salida:** `SaleResult`

| Campo | Tipo |
|---|---|
| `saleId` | `long` |
| `folio` | `String` |
| `dateTime` | `LocalDateTime` |
| `lines` | `List<SaleLineView>` |
| `total` | `Money` |
| `paymentMethodName` | `String` |
| `cashReceived`, `change` | `Money` (*opcionales*) |
| `cashierName` | `String` |
| `repeated` | `boolean` |

**Errores** (`ValidationException`):

| Campo | Mensaje |
|---|---|
| `lines` | Agrega al menos un producto al ticket. · Revisa las cantidades del ticket. · Hay un producto que ya no está disponible; quítalo del ticket. · Solo hay 3 de Agua 1 L. |
| `paymentMethodId`, `cashReceived` | Los mismos de `ChargeMembership`. |

### `GetSaleTicket` / `PrintSaleTicket` · Ticket

Permiso: `SELL`. **Entrada:** `long saleId`. **Salida de `GetSaleTicket`:** `SaleTicket(header, sale)`, con `sale` de tipo `SaleResult`. **Errores:** `NotFoundException` «La venta no existe.».

## Inventario

### `GetInventory` · Existencias

Permiso: `VIEW_INVENTORY`.

**Entrada:** `InventoryQuery(text, onlyLow)`; `text` *opcional* (clave, nombre o categoría), `onlyLow` `boolean` (agotados y bajos).

**Salida:** `List<StockRow>` de productos activos: `productId`, `code`, `name`, `categoryName`, `stock`, `minStock`, `level`. Primero agotados, luego bajos, luego suficientes; dentro de cada grupo, por nombre.

### `ListStockMovements` · Movimientos recientes

Permiso: `VIEW_INVENTORY`.

**Entrada:** `MovementsQuery(productId)`; *opcional*, `null` muestra todos.

**Salida:** `List<MovementRow>`, últimos 50, el más reciente primero.

| Campo | Tipo | Notas |
|---|---|---|
| `dateTime` | `LocalDateTime` | |
| `productCode`, `productName` | `String` | |
| `type` | `MovementType` | |
| `fromSale` | `boolean` | `true` si lo generó una venta; la UI lo muestra como tipo «Venta». |
| `quantity` | `int` | Con signo: positivo suma, negativo resta. |
| `resultingStock` | `int` | «Queda». |
| `reasonName` | `String` | *opcional*; `null` si viene de una venta. |
| `saleFolio` | `String` | *opcional*; folio de la venta que lo generó. |
| `userName` | `String` | Quién lo registró. |

### `GetMovementOptions` · Opciones del formulario de movimiento

Permiso: `REGISTER_STOCK_MOVEMENT`.

**Salida:** `MovementOptions`

| Campo | Tipo | Notas |
|---|---|---|
| `products` | `List<StockRow>` | Productos activos. |
| `reasons` | `List<MovementReasonItem>` | Motivos activos, sin los de sistema. Es una sola lista para los tres tipos, como en la especificación. |

### `PreviewStockMovement` · Cómo quedará la existencia

Permiso: `REGISTER_STOCK_MOVEMENT`. No guarda nada ni lanza por datos incompletos.

**Entrada:** `RegisterStockMovementCommand` (el mismo del registro).

**Salida:** `StockPreview`

| Campo | Tipo | Notas |
|---|---|---|
| `product` | `StockRow` | *opcional* |
| `currentStock` | `Integer` | *opcional* |
| `delta` | `Integer` | *opcional*; con signo («+5», «−3»). |
| `resultingStock` | `Integer` | *opcional* |
| `levelAfter` | `StockLevel` | *opcional* |
| `errors` | `Map<String, String>` | |
| `ready` | `boolean` | |

### `RegisterStockMovement` · Registrar movimiento

Permiso: `REGISTER_STOCK_MOVEMENT`.

**Entrada:** `RegisterStockMovementCommand`

| Campo | Tipo | Reglas |
|---|---|---|
| `operationId` | `String` | Obligatorio. |
| `productId` | `Long` | Producto activo. |
| `type` | `MovementType` | Obligatorio. |
| `quantity` | `Integer` | `ENTRADA` y `SALIDA`: piezas, mayor que 0 y sin signo; el sistema pone el signo. Se ignora en `AJUSTE`. |
| `countedStock` | `Integer` | Solo `AJUSTE`: existencia contada, 0 o más y distinta a la actual. El sistema calcula la diferencia. |
| `reasonId` | `Long` | Motivo activo que no sea de sistema. |

**Salida:** `MovementRow` del movimiento guardado, con `repeated` (`boolean`).

**Errores** (`ValidationException`):

| Campo | Mensaje |
|---|---|
| `productId` | Elige un producto. · El producto está inactivo. |
| `type` | Elige el tipo de movimiento. |
| `quantity` | Escribe una cantidad entera mayor que cero, sin signo. · No puedes sacar 8; solo hay 5 en existencia. |
| `countedStock` | Escribe la existencia contada: un número entero, 0 o mayor. · La existencia contada es igual a la registrada; no hay nada que ajustar. |
| `reasonId` | Elige el motivo. · Ese motivo ya no está disponible. |

## Planes · *implementado*

Permiso: `MANAGE_PLANS` en todos.

| Caso de uso | Entrada | Salida |
|---|---|---|
| `ListPlans` | solo `actor` | `List<PlanRow>`: activos primero, luego por duración y por nombre |
| `PreviewPlanSavings` | `PlanCommand` | `SavingsView`, para la vista previa del formulario |
| `CreatePlan` | `PlanCommand` | `PlanRow` |
| `UpdatePlan` | `long planId` + `PlanCommand` | `PlanRow` |
| `SetPlanActive` | `long planId`, `boolean active` | `PlanRow` |
| `DeletePlan` | `long planId` | nada |

**`PlanRow`:** `id`, `name`, `durationDays`, `price`, `active`, `referenceMonthlyPlanId` (*opcional*), `referenceMonthlyPlanName` (*opcional*), `savings` (`SavingsView`).

**`PlanCommand`:** `name`, `durationDays` (`Integer`), `price`, `referenceMonthlyPlanId` (*opcional*: plan de 30 días contra el que se calcula el ahorro; puede estar inactivo).

Cambiar el precio aplica a los cobros nuevos; los anteriores conservan lo pagado. Un plan inactivo deja de ofrecerse pero se conserva en el historial. `CreatePlan` deja el plan activo; `UpdatePlan` no cambia si está activo; `SetPlanActive` con el estado que ya tiene no hace nada. Los que escriben corren en una transacción.

**Ahorro:** meses equivalentes = 12 para 365 días y días ÷ 30 para los múltiplos de 30; otras duraciones no tienen. `monthlyTotal` = precio de la referencia × meses; `percent` = ahorro × 100 / `monthlyTotal`, redondeado (,5 hacia arriba).

**`PreviewPlanSavings`** nunca lanza por datos incompletos: sin duración o con 30 días o menos devuelve `NO_APLICA`; sin precio (o 0), `NO_DISPONIBLE`; una referencia que no existe cuenta como sin referencia.

**`DeletePlan`** borra solo un plan que nadie usa (`DomainException`):

| Situación | Mensaje |
|---|---|
| Ya tiene membresías | No puedes eliminar «Mensual»: ya tiene membresías. Desactívalo para dejar de ofrecerlo. |
| Es referencia de otros planes | No puedes eliminar «Mensual»: es la referencia mensual de Anual, Trimestral. Cambia esa referencia antes. |

Un id que ya no existe lanza `NotFoundException`: «El plan ya no existe. Recarga la pantalla.».

**Errores** (`ValidationException`):

| Campo | Mensaje |
|---|---|
| `name` | Escribe el nombre del plan. · Usa 60 caracteres o menos. · Ya existe un plan con ese nombre (sin distinguir mayúsculas). |
| `durationDays` | Escribe la duración en días, de 1 a 730. |
| `price` | Escribe un precio mayor que cero, por ejemplo 450.00. |
| `referenceMonthlyPlanId` | Elige un plan de 30 días distinto de este. |

## Productos

Permiso: `MANAGE_PRODUCTS` en todos.

| Caso de uso | Entrada | Salida |
|---|---|---|
| `ListProducts` | `ProductsQuery(text, categoryId, active)`, todos *opcionales* | `List<ProductRow>` por nombre |
| `CreateProduct` | `CreateProductCommand(code, name, categoryId, price, minStock, initialStock)` | `ProductRow` |
| `UpdateProduct` | `long productId` + `UpdateProductCommand(code, name, categoryId, price, minStock)` | `ProductRow` |
| `SetProductActive` | `long productId`, `boolean active` | `ProductRow` |

**`ProductRow`:** `id`, `code`, `name`, `categoryId`, `categoryName`, `price`, `minStock`, `stock`, `level`, `active`.

La existencia no se edita aquí: solo cambia con ventas y movimientos. Si `initialStock` es mayor que 0, al crear se registra una entrada con el motivo de sistema «Existencia inicial». La clave se guarda en mayúsculas.

**Errores** (`ValidationException`):

| Campo | Mensaje |
|---|---|
| `code` | Escribe la clave del producto. · Usa de 2 a 12 letras, números o guiones, por ejemplo AGU-02. · Ya existe un producto con esa clave. |
| `name` | Escribe el nombre del producto. |
| `categoryId` | Elige una categoría. · Esa categoría ya no está activa; elige otra. |
| `price` | Escribe un precio mayor que cero, por ejemplo 25.00. |
| `minStock` | Escribe el mínimo: un número entero, 0 o mayor. |
| `initialStock` | Escribe la existencia inicial: un número entero, 0 o mayor. |

## Usuarios

Permiso: `MANAGE_USERS` en todos.

| Caso de uso | Entrada | Salida |
|---|---|---|
| `ListUsers` | solo `actor` | `List<UserRow(id, name, username, role, active)>` |
| `CreateUser` | `CreateUserCommand(name, username, role, password, passwordConfirmation)` | `UserRow` |
| `UpdateUser` | `long userId` + `UpdateUserCommand(name, username, role, newPassword, newPasswordConfirmation)` | `UserRow` |
| `SetUserActive` | `long userId`, `boolean active` | `UserRow` |

`newPassword` es *opcional*: `null` o vacío conserva la actual. El nombre de usuario se guarda en minúsculas. Desactivar a un usuario conserva sus cobros, ventas y movimientos. La matriz de permisos de la pantalla sale de `Role.permissions()`, sin caso de uso.

**Errores:**

| Situación | Excepción | Mensaje |
|---|---|---|
| Nombre vacío | `ValidationException` (`name`) | Escribe el nombre de la persona. |
| Usuario vacío, inválido o repetido | `ValidationException` (`username`) | Escribe el nombre de usuario. · Usa de 3 a 20 letras minúsculas, números, punto o guion, sin espacios. · Ese nombre de usuario ya está en uso. |
| Sin rol | `ValidationException` (`role`) | Elige un rol. |
| Contraseña vacía al crear o corta | `ValidationException` (`password` / `newPassword`) | Escribe una contraseña. · La contraseña debe tener al menos 8 caracteres. |
| Confirmación distinta | `ValidationException` (`passwordConfirmation` / `newPasswordConfirmation`) | Las contraseñas no coinciden. |
| Quitar el rol al último administrador activo | `ValidationException` (`role`) | Debe quedar al menos un administrador activo. |
| Desactivar al último administrador activo | `DomainException` | Debe quedar al menos un administrador activo. |
| Desactivarse a sí mismo | `DomainException` | No puedes desactivar tu propio usuario mientras lo usas. |
| Usuario inexistente | `NotFoundException` | El usuario no existe. |

### `CreateInitialAdmin` · Primer administrador

Sin `actor` ni permiso: solo funciona mientras no exista **ningún** usuario. La UI lo ofrece en el primer arranque y, mientras no haya UI, lo llama el punto de entrada con `ADMIN_NAME`, `ADMIN_USERNAME` y `ADMIN_PASSWORD` del `.env` (nunca en git).

**Entrada:** `CreateInitialAdminCommand(name, username, password, passwordConfirmation)`. **Salida:** `UserRow` del administrador creado, con rol `ADMINISTRADOR` y activo.

- Las reglas de nombre, usuario y contraseña, y sus mensajes, son las de `CreateUser`.
- Revisar que no hay usuarios y crear el administrador van en la misma transacción: si dos estaciones arrancan a la vez, solo una lo crea.
- Si ya hay usuarios: `DomainException` con «El sistema ya tiene usuarios; un administrador debe crear las cuentas nuevas.».
- `boolean needsInitialAdmin()` (consulta sin actor) dice si la UI debe mostrar la pantalla de primer arranque.

## Configuración

Permiso: `MANAGE_SETTINGS` en todos.

### `GetSettings` / `UpdateSettings` · *implementado*

`GetSettings` recibe solo `actor`. `UpdateSettings` recibe `UpdateSettingsCommand` con los campos editables y devuelve `SettingsView`. Los textos se recortan y un opcional en blanco se guarda vacío. `logo` en `null` quita el logotipo: para conservarlo, la UI reenvía los bytes que recibió. `warningDays` y `autoDeactivationDays` son `Integer` para poder reportar el campo vacío. Todos los errores se reportan juntos.

**`SettingsView`**

| Campo | Tipo | Reglas al guardar |
|---|---|---|
| `gymName` | `String` | Obligatorio, 100 caracteres o menos. |
| `address`, `phone` | `String` | *opcionales*; 200 y 30 caracteres o menos. |
| `receiptFooter` | `String` | *opcional*, 160 caracteres o menos. |
| `logo` | `byte[]` | *opcional*. PNG, JPG, WEBP o SVG de hasta 300 KB, reconocido por sus primeros bytes. |
| `currency` | `Currency` | No se puede cambiar si ya hay cobros o ventas. |
| `warningDays` | `int` | De 1 a 60. |
| `autoDeactivationDays` | `int` | De 1 a 365. |
| `folioPrefix` | `String` | De 1 a 8 letras mayúsculas, números o guiones. Es el de la serie de cobros. |
| `currencyLocked` | `boolean` | Solo lectura: `true` si ya hay cobros o ventas. |
| `paymentsCount`, `salesCount` | `int` | Solo lectura, para explicar el bloqueo («ya hay 41 cobros y 18 ventas registrados en MXN»). |
| `folioExample` | `String` | Solo lectura; un folio de cobro con el prefijo y el ancho actuales, por ejemplo `GYM-0001`. La UI pone el texto alrededor. |

**Errores** (`ValidationException`):

| Campo | Mensaje |
|---|---|
| `gymName` | Escribe el nombre del gimnasio. · Usa 100 caracteres o menos. |
| `address` | Usa 200 caracteres o menos. |
| `phone` | Usa 30 caracteres o menos. |
| `warningDays` | Escribe un número de días entre 1 y 60. |
| `autoDeactivationDays` | Escribe un número de días entre 1 y 365. |
| `currency` | Elige una moneda. · No se puede cambiar: ya hay cobros o ventas registrados en MXN. |
| `folioPrefix` | Usa de 1 a 8 letras mayúsculas, números o guiones, por ejemplo GYM-. |
| `receiptFooter` | Usa 160 caracteres o menos. |
| `logo` | Usa una imagen PNG, JPG, WEBP o SVG de hasta 300 KB. |

### Catálogos

Implementado: métodos de pago. Motivos de movimiento y categorías de producto son de la fase 2.

Los tres catálogos tienen los mismos cuatro casos de uso, cada uno con su command y su item:

| Caso de uso | Entrada | Salida |
|---|---|---|
| `ListPaymentMethods` | `boolean includeInactive` | `List<PaymentMethodItem>` |
| `CreatePaymentMethod` | `PaymentMethodCommand(name, cash)` | `PaymentMethodItem` |
| `UpdatePaymentMethod` | `long id` + `PaymentMethodCommand` | `PaymentMethodItem` |
| `SetPaymentMethodActive` | `long id`, `boolean active` | `PaymentMethodItem` |
| `DeletePaymentMethod` | `long id` | nada |

Motivos y categorías siguen la misma forma con `MovementReasonCommand(name)` / `ProductCategoryCommand(name)` y sus items. Crear, editar, activar y borrar corren cada uno en una transacción. Borrar solo se permite si el elemento nunca se usó; si ya se usó, se desactiva. El nombre se recorta y no puede repetirse sin distinguir mayúsculas, aunque el otro esté inactivo. `Create…` deja el elemento activo; `Update…` no cambia si está activo; `Set…Active` con el estado que ya tiene no hace nada. La lista va ordenada por nombre. Desactivar no cambia los registros anteriores.

**Errores:**

| Situación | Excepción | Mensaje |
|---|---|---|
| Nombre vacío, largo o repetido | `ValidationException` (`name`) | Escribe el nombre. · Usa 40 caracteres o menos. · Ya existe uno con ese nombre. |
| El id ya no existe | `NotFoundException` | El método de pago ya no existe. Recarga la pantalla. |
| Desactivar el último método de pago, motivo o categoría activos | `DomainException` | Deja al menos un método de pago. · Deja al menos un motivo. · Deja al menos una categoría. |
| Desactivar una categoría con productos activos | `DomainException` | No puedes desactivar «Bebidas»: la usa Agua 1 L. Cambia su categoría antes. |
| Editar o desactivar un motivo de sistema | `DomainException` | Los motivos del sistema no se pueden modificar. |
| Cambiar «es efectivo» de un método ya usado en cobros o ventas | `ValidationException` (`cash`) | No puedes cambiar si «Efectivo» es efectivo: ya se usó en cobros o ventas. Crea un método nuevo y desactiva este. |
| Borrar un método ya usado en cobros o ventas | `DomainException` | No puedes eliminar «Efectivo»: ya se usó en cobros o ventas. Desactívalo para dejar de ofrecerlo. |
| Borrar el último método activo | `DomainException` | Deja al menos un método de pago. |

## Pendientes que afectan los contratos

| Tema | Qué falta decidir | Qué cambiaría |
|---|---|---|
| Folios de ventas | Una serie para cobros y ventas, o dos (`GYM-` y `GYM-V-`). Hoy solo existe la serie `COBRO`. | `folioExample` y la numeración; los contratos no cambian. |
| Deshacer un cobro | Si se podrá, quién y durante cuánto tiempo. | Un caso de uso `VoidMembershipPayment`. |

## Cambios que estos contratos piden al código

Lo que hoy no coincide con los contratos y hay que ajustar al implementar cada caso de uso.

| Dónde | Cambio |
|---|---|
| Precios de productos | Mayores que cero (hoy `Product` y la base admiten 0). |

## Puertos de salida implementados

Lo que los casos de uso piden a `infrastructure`. Los puertos de los casos planeados se documentan al implementarlos.

### `UserRepository`

| Método | Contrato |
|---|---|
| `Optional<UserCredentials> findCredentialsByUsername(String username)` | Busca sin distinguir mayúsculas. Devuelve vacío si no existe; incluye usuarios desactivados (decidir qué hacer con ellos es del caso de uso). |
| `Optional<User> findById(long id)` | Sin hash. Vacío si no existe; incluye usuarios desactivados. |

`UserCredentials(User user, String passwordHash)` va separado de `User` para que el hash solo viaje entre el repositorio y el caso de uso. Implementación: `PostgresUserRepository`.

### `PasswordHasher`

| Método | Contrato |
|---|---|
| `String hash(String rawPassword)` | Hash con sal propia: la misma contraseña produce hashes distintos. Incluye el algoritmo y sus parámetros. |
| `boolean matches(String rawPassword, String passwordHash)` | `true` solo si corresponde. Una contraseña incorrecta devuelve `false`, no lanza excepción. |

Implementación: `Argon2PasswordHasher` (Argon2id, 64 MiB, 3 iteraciones, paralelismo 1).

### `SettingsRepository`

| Método | Contrato |
|---|---|
| `GymSettings get()` | La configuración (sin folios). Lanza `IllegalStateException` si falta la fila; la crea la migración V2. |
| `void save(GymSettings settings)` | Actualiza la fila. |

Implementación: `PostgresSettingsRepository`.

### `FolioSeriesRepository`

| Método | Contrato |
|---|---|
| `FolioSeries get(String series)` | Prefijo y ancho de la serie (`FolioSeries.MEMBERSHIP_PAYMENTS` = `COBRO`). Lanza `IllegalStateException` si no existe. |
| `void save(FolioSeries series)` | Guarda prefijo y ancho; el contador no cambia. |

Implementación: `PostgresFolioSeriesRepository`. Tomar el siguiente folio se agrega con el cobro.

### `MembershipPaymentRepository` y `SaleRepository`

| Método | Contrato |
|---|---|
| `int count()` | Cobros (o ventas) registrados. Con alguno, la moneda queda bloqueada. |

Implementaciones: `PostgresMembershipPaymentRepository` y `PostgresSaleRepository`. Crecen con el cobro y la venta.

### `TransactionRunner`

| Método | Contrato |
|---|---|
| `<T> T inTransaction(Supplier<T> work)` | Ejecuta el trabajo en una sola transacción; los repositorios usados dentro participan en ella. Si el trabajo lanza, se deshace todo. |
| `void run(Runnable work)` | Igual, sin resultado. |

Implementación: `JdbiTransactionRunner`, con aislamiento `SERIALIZABLE`. Si PostgreSQL aborta el trabajo por chocar con otra estación (SQLState `40001` o `40P01`), lo repite hasta 3 veces. Por eso el trabajo no debe tener efectos fuera de la base, como imprimir.

Todo caso de uso que lea para decidir y luego escriba usa una transacción: `UpdateSettings` (moneda bloqueada, configuración y prefijo) y crear, editar o activar métodos de pago (nombre repetido, último activo). El cobro hará en una sola: tomar el folio, insertar el pago y la membresía.

### `PaymentMethodRepository`

| Método | Contrato |
|---|---|
| `List<PaymentMethod> findAll(boolean includeInactive)` | Ordenados por nombre sin distinguir mayúsculas. |
| `Optional<PaymentMethod> findById(long id)` | Vacío si no existe. |
| `boolean existsByName(String name, Long excludeId)` | Sin distinguir mayúsculas; `excludeId` (opcional) no cuenta. |
| `int countActive()` | Métodos activos. |
| `PaymentMethod insert(PaymentMethod method)` | Lo devuelve con su id. |
| `void update(PaymentMethod method)` | Nombre, efectivo y activo. Lanza `IllegalStateException` si el id no existe. |
| `boolean isUsed(long id)` | `true` si hay cobros o ventas con este método. |
| `void delete(long id)` | Borra el método. |

Implementación: `PostgresPaymentMethodRepository`.

### `PlanRepository`

| Método | Contrato |
|---|---|
| `List<Plan> findAll()` | Activos e inactivos, sin orden garantizado (el caso de uso ordena). |
| `Optional<Plan> findById(long id)` | Vacío si no existe. |
| `boolean existsByName(String name, Long excludeId)` | Sin distinguir mayúsculas; `excludeId` (opcional) no cuenta. |
| `Plan insert(Plan plan)` | Lo devuelve con su id. |
| `void update(Plan plan)` | Todos los campos. Lanza `IllegalStateException` si el id no existe. |
| `boolean hasMemberships(long id)` | `true` si alguna membresía usa el plan. |
| `List<String> namesReferencing(long id)` | Nombres de los planes que lo usan como referencia mensual, en orden alfabético. |
| `void delete(long id)` | Borra el plan. |

Implementación: `PostgresPlanRepository`.

### Permisos y reloj

`Authorizer.require(actor, permiso)` recarga al actor con `UserRepository.findById` antes de revisar el permiso: un usuario desactivado o con otro rol durante la sesión recibe los mensajes de *Errores* en su siguiente operación. Devuelve el usuario vigente para registrarlo como quien hizo la operación.

«Hoy» sale de un `java.time.Clock` inyectado en el caso de uso, sin puerto propio.

## Al agregar un caso de uso

1. Crea `usecase/<caso>/` con el caso, su command o query, su result y sus excepciones propias.
2. Define en `port/out/` solo los métodos que el caso necesita.
3. Actualiza aquí su contrato y, al implementarlo, documenta sus puertos y quita de *Cambios que estos contratos piden al código* lo que ya quedó resuelto.
