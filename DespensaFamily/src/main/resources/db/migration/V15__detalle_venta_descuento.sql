-- DF-UX-005: detalle_venta no guardaba que descuento o promo se aplico a la
-- linea (ticket 196 mostraba "1 u x $2860 = $286" sin ninguna explicacion,
-- y el detalle de venta no tenia de donde sacarla).
--
-- Columnas NULLABLE a proposito: las ventas ya facturadas no tienen forma de
-- reconstruir si en su momento hubo descuento o promo, y forzar un 0/"" ahi
-- inventaria un dato que no existe. VentaService.calcularLinea ya devuelve
-- estos dos valores (los usa cotizar via LineaCotizadaDTO); esta migracion
-- solo le da a registrarVenta un lugar donde guardarlos.
alter table detalle_venta add column if not exists descuento_porcentaje double;
alter table detalle_venta add column if not exists promo_descripcion varchar(255);
