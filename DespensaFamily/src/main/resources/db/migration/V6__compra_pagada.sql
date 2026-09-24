-- Distingue una compra pagada al recibir mercaderia de una que queda a
-- cuenta: el proveedor puede dejar la mercaderia y cobrar despues, y eso no
-- deberia sacar plata de caja hasta que se pague de verdad (Fase 11 del mapa
-- de cambios).
--
-- pagada default TRUE para las compras que ya existen, a proposito: no hay
-- ninguna senal en el sistema de que hayan quedado pendientes, y mostrarlas
-- de golpe como "pendientes de pago" — semanas despues de haber pasado, y sin
-- que nadie las haya marcado asi — seria peor que asumir que se resolvieron.
-- Las compras VIEJAS tampoco generan un movimiento de caja retroactivo (ver
-- el comentario en CompraService.registrarCompra): no hay dato confiable de
-- cuanto efectivo real habia en el cajon en esas fechas, y fabricar esa
-- historia seria peor que no tenerla.

alter table compra add column if not exists pagada boolean default true not null;
alter table compra add column if not exists metodo_pago varchar(20) default 'EFECTIVO' not null;
