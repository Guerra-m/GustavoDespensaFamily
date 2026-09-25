-- DF-DEBT-001: migra todo el dinero de double (float(53)) a DECIMAL con
-- escala fija. El double no representa exacto los decimales en base 10 (0.1 +
-- 0.2 no da 0.30 en binario), asi que precios, totales y montos de caja
-- arrastraban ruido de precision que a veces se colaba hasta la pantalla o el
-- ticket (ver Dinero/Cantidades). DECIMAL con escala fija saca el problema de
-- raiz en la base, que es donde en definitiva vive el numero.
--
-- Dos escalas, nunca mezcladas:
--   - DECIMAL(15,2): plata (precios, costos, totales, subtotales, montos,
--     porcentajes). El centavo es la fraccion minima.
--   - DECIMAL(15,3): cantidades pesables (stock, cantidad vendida/comprada,
--     fraccion minima de un producto). El gramo es la fraccion minima.
--
-- H2 convierte el valor existente al alterar el tipo de columna (float ->
-- decimal), redondeando a la escala nueva -- no hace falta un UPDATE previo.
-- Igual que las migraciones anteriores, nunca se toca una columna con un
-- ALTER que ya se aplico: esto es codigo nuevo, V17.

-- ------------------------------------------------------------- producto
alter table producto alter column precio_venta decimal(15,2) not null;
alter table producto alter column precio_costo decimal(15,2) not null;
alter table producto alter column porcentaje_ganancia decimal(15,2) not null;
alter table producto alter column stock decimal(15,3) not null;
alter table producto alter column fraccion_minima decimal(15,3);

-- --------------------------------------------------------------- compra
alter table compra alter column total decimal(15,2) not null;

-- ---------------------------------------------------------- configuracion
alter table configuracion alter column umbral_stock_bajo decimal(15,3);
alter table configuracion alter column ganancia_por_defecto decimal(15,2);

-- -------------------------------------------------------------- descuento
alter table descuento alter column porcentaje decimal(5,2) not null;

-- ---------------------------------------------------------- detalle_compra
alter table detalle_compra alter column cantidad decimal(15,3) not null;
alter table detalle_compra alter column precio_costo decimal(15,2) not null;
alter table detalle_compra alter column subtotal decimal(15,2) not null;
alter table detalle_compra alter column precio_costo_anterior decimal(15,2);

-- ----------------------------------------------------------- detalle_venta
alter table detalle_venta alter column cantidad decimal(15,3) not null;
alter table detalle_venta alter column precio_unitario decimal(15,2) not null;
alter table detalle_venta alter column precio_costo decimal(15,2) not null;
alter table detalle_venta alter column subtotal decimal(15,2) not null;
alter table detalle_venta alter column descuento_porcentaje decimal(5,2);

-- ------------------------------------------------------------ movimiento_caja
alter table movimiento_caja alter column monto decimal(15,2) not null;

-- ---------------------------------------------------------------- venta
alter table venta alter column total decimal(15,2) not null;
