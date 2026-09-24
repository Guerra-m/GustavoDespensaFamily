-- Congela el total de la compra, igual que Venta.total.
--
-- Antes se recalculaba sumando detalle_compra cada vez que hacia falta. Un
-- total que se recalcula no es un hecho historico: si el dia de manana cambia
-- como se redondea un subtotal, las compras viejas cambiarian de importe con
-- retroactividad. Se detecto auditando los reportes antes de construir caja
-- encima (ver PLAN-INSTALADOR.md, hallazgo 3).
--
-- if not exists / IF NOT EXISTS en todo: mismo motivo que V1, esta migracion
-- tiene que poder correr tanto sobre una base que recien ahora ve esta
-- columna como, en teoria, sobre una que ya la tuviera.

alter table compra add column if not exists total float(53) default 0 not null;

-- Backfill: las compras que ya existen no tienen total calculado. Se completa
-- sumando sus detalles una sola vez, acá y no en el arranque de la
-- aplicacion, para que quede en el historial de migraciones y no dependa de
-- que el codigo Java lo recuerde hacer.
update compra c
set total = coalesce((
    select sum(dc.subtotal)
    from detalle_compra dc
    where dc.compra_id = c.id
), 0)
where c.total = 0;
