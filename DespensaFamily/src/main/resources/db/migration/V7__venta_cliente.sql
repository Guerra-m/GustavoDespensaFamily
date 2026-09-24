-- Nombre de cliente opcional en la venta, para que salga en el ticket.
--
-- Texto libre y sin entidad, por decision del dueño: "Juan", "juan " y
-- "Juan P." son tres valores distintos, asi que esto sirve para que el
-- ticket diga a quien es, no para agrupar "cuanto me compro Juan este mes".
-- Si algun dia hace falta eso, ahi si hace falta una entidad Cliente.

alter table venta add column if not exists cliente varchar(255);
