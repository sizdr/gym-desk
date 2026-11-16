-- ---------- Alcance del proyecto: socios, membresías y cobros ----------
-- El sistema administra socios, planes, métodos de pago, usuarios, cobros de membresía y su configuración.
-- Productos, ventas e inventario quedan fuera del alcance: se quitan sus tablas y su tipo enumerado.
-- Ningún caso de uso las llenó nunca, así que no se pierden datos del negocio.

DROP TABLE movimiento_inventario;
DROP TABLE detalle_venta;
DROP TABLE venta;
DROP TABLE producto;
DROP TABLE categoria_producto;
DROP TABLE motivo_movimiento;

DROP TYPE tipo_movimiento;
