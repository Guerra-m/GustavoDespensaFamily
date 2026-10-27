-- DF-BUG-003: clave opcional para no duplicar una venta si "Confirmar Venta"
-- se hace doble clic o el frontend reintenta el mismo pedido tras un timeout
-- de red. Nullable: las filas existentes no tienen clave y seguir sin
-- mandarla desde ningun cliente tiene que seguir funcionando igual que hoy.
-- El indice unico es la ultima linea de defensa contra dos peticiones
-- concurrentes con la misma clave (VentaService las atrapa antes con una
-- lectura previa, pero esa lectura sola no alcanza si las dos llegan casi
-- juntas — ver VentaController).
alter table venta add column if not exists clave_idempotencia varchar(64);
create unique index if not exists ux_venta_clave_idempotencia on venta(clave_idempotencia);
