### E1 · Cobro registrado desde la pantalla Cobrar membresía

```sql
SELECT p.folio, s.nombre AS socio, pl.nombre AS plan, p.monto, p.efectivo_recibido,
       p.tipo_cobro, m.fecha_inicio, m.fecha_vencimiento, u.nombre_usuario AS atendio
FROM pago p
JOIN socio s ON s.id = p.socio_id
JOIN membresia m ON m.pago_id = p.id
JOIN plan_membresia pl ON pl.id = m.plan_id
JOIN usuario u ON u.id = p.usuario_id
ORDER BY p.id DESC LIMIT 1
```

| folio | socio | plan | monto | efectivo_recibido | tipo_cobro | fecha_inicio | fecha_vencimiento | atendio |
|---|---|---|---|---|---|---|---|---|
| GYM-0026 | Teresa Campos | Mensual | 450.00 | 500.00 | renovacion_vencida | 2026-11-16 | 2026-12-16 | admin |

### E2 · Estado de Teresa Campos con la función de la base después del cobro

```sql
SELECT nombre, fn_estado_socio(id) AS estado FROM socio WHERE nombre = 'Teresa Campos'
```

| nombre | estado |
|---|---|
| Teresa Campos | VIGENTE |

### E3 · Socio registrado desde la pantalla Socios

```sql
SELECT id, nombre, telefono, correo, fecha_alta FROM socio WHERE nombre = 'Laura Medina'
```

| id | nombre | telefono | correo | fecha_alta |
|---|---|---|---|---|
| 24 | Laura Medina | 5551203456 | laura.medina@correo.mx | 2026-11-16 |

### E4 · Plan agregado desde la pantalla Planes

```sql
SELECT p.nombre, p.duracion_dias, p.precio, r.nombre AS referencia_mensual, p.activo
FROM plan_membresia p LEFT JOIN plan_membresia r ON r.id = p.referencia_mensual_id
WHERE p.nombre = 'Bimestral'
```

| nombre | duracion_dias | precio | referencia_mensual | activo |
|---|---|---|---|---|
| Bimestral | 60 | 850.00 | Mensual | t |

### E5 · Cuenta creada desde la pantalla Usuarios (solo se guarda el hash Argon2id)

```sql
SELECT nombre, nombre_usuario, rol, activo, left(contrasena_hash, 10) || '…' AS hash
FROM usuario WHERE nombre_usuario = 'carlos'
```

| nombre | nombre_usuario | rol | activo | hash |
|---|---|---|---|---|
| Carlos Ruiz | carlos | caja | t | $argon2id$… |

### E6 · Estados de los socios según la función de la base

```sql
SELECT fn_estado_socio(id) AS estado, COUNT(*) AS socios
FROM socio GROUP BY 1 ORDER BY 1
```

| estado | socios |
|---|---|
| BAJA | 2 |
| POR_VENCER | 6 |
| SIN_MEMBRESIA | 3 |
| VENCIDA | 3 |
| VIGENTE | 10 |

