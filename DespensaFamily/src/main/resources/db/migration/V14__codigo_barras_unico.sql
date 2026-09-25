-- DF-BUG-009: codigos de barras duplicados dejaban al escaner eligiendo
-- siempre el primer producto que encontraba (ventas.ts:468 usaba
-- productos.find). No habia unicidad ni en la base ni en ProductoService.
--
-- Vacio normalizado a NULL antes del indice: varios productos sin codigo no
-- tienen por que chocar entre si, y un unique index de H2 SI permite
-- multiples filas en NULL (no asi con '' — dos productos con codigo_barras
-- = '' hubieran roto el index de abajo).
update producto set codigo_barras = null where codigo_barras = '';

-- Se decide a proposito que el codigo tambien bloquea entre un producto
-- activo y uno dado de baja (borrado logico, activo=false): el codigo de
-- barras identifica una mercaderia fisica, y dos filas con el mismo codigo
-- -aunque una este inactiva- es la misma ambiguedad de DF-BUG-009 al
-- escanear (y a la fila borrada le siguen apuntando sus detalle_venta
-- historicos). Si hace falta reusar el codigo de un producto dado de baja,
-- el alta lo va a rechazar nombrando ese producto (ver
-- ProductoService.validarCodigoBarrasUnico), y el dueño decide a mano si lo
-- reactiva o le cambia el codigo antes de reusarlo.
--
-- La base real de Martin no tiene duplicados (confirmado). Si alguna otra
-- base SI los tuviera, esta migracion prefiere fallar fuerte -Flyway corta
-- el arranque con el error de H2 nombrando la tabla y el valor repetido-
-- antes que inventar en silencio cual de los dos productos se queda con el
-- codigo. Es el mismo criterio que ya usa el proyecto para no perder datos
-- en silencio (ver V1__esquema_base.sql), aplicado a "no elegir por vos".
create unique index if not exists ux_producto_codigo_barras on producto(codigo_barras);
