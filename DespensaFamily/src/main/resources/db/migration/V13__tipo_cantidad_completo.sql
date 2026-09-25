-- DF-DATA-001 (parte 1): productos sin tipoCantidad se vendian en fracciones
-- porque VentaService solo rechazaba decimales cuando tipoCantidad ==
-- 'UNIDAD'; con tipoCantidad nulo (7 de 14 productos reales de Martin) esa
-- validacion nunca corria. El alta tampoco exigia stock entero ni una unidad
-- de medida coherente con el tipo.
--
-- Decision del dueño (Martin, 24/09/2026): los productos que ya existen sin
-- tipo son TODOS por unidad (ids 2,3,4,6,8,9,16: chupetines, Obleas, Papas
-- Lays, Leche, torta raspada, perfume Tannat, jugo Ades) — ninguno se vende
-- por peso.
update producto set tipo_cantidad = 'UNIDAD' where tipo_cantidad is null;

-- Stock decimal que haya quedado grabado en un producto por unidad (ej. 38,5
-- chupetines, arrastrado por el mismo bug: una venta de 0,5 unidad restaba
-- fraccion de un producto que nunca debio aceptarla) no se puede truncar sin
-- mas: FLOOR perderia media unidad de stock real sin que nadie se entere, y
-- eso es mercaderia/plata del negocio desapareciendo en silencio.
--
-- Se redondea PARA ARRIBA (CEIL): nunca hace perder stock respecto de lo que
-- la base ya tenia. Puede dejar el conteo hasta una unidad de mas hasta el
-- proximo recuento fisico, pero esa direccion del error es la segura — sobra
-- inventario en el sistema, no falta.
update producto set stock = ceil(stock) where tipo_cantidad = 'UNIDAD' and stock <> floor(stock);

-- A partir de aca todo producto tiene tipo: la venta y el alta pueden confiar
-- en el campo sin repetir el default en cada lugar que lo lee.
alter table producto alter column tipo_cantidad set not null;
