-- Como se cobro cada venta. Denormalizado en la propia venta (ademas de
-- quedar en el movimiento de caja que genera) porque el ticket y el listado
-- de ventas necesitan mostrarlo sin salir a buscarlo a otra tabla, y es un
-- hecho historico de la venta en si misma — igual que el total.
--
-- Default EFECTIVO para las ventas que ya existen: es el metodo mas comun en
-- una despensa y, mas importante, es la unica opcion que no requiere haber
-- sabido algo que esas ventas viejas nunca registraron.

alter table venta add column if not exists metodo_pago varchar(20) default 'EFECTIVO' not null;
