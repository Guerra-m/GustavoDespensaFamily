-- Permisos sueltos por empleado (ver Usuario.java): arrancan apagados, el
-- dueño los prende a mano para quien corresponda desde Usuarios.
alter table usuario add column if not exists puede_gestionar_productos boolean not null default false;
alter table usuario add column if not exists puede_ver_caja boolean not null default false;
