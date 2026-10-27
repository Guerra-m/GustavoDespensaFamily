-- Crea la ficha de Empleado para cada Usuario que ya existia antes de que
-- UsuarioService.crearUsuario empezara a hacerlo solo.
--
-- Encontrado el 01/09/2026: se creo un usuario con rol EMPLEADO (via
-- Usuarios) esperando verlo en la pantalla nueva de Empleados (dias
-- trabajados y pago) y no aparecia — las dos cosas quedaban desconectadas
-- porque Empleado y Usuario son entidades separadas a proposito (ver
-- Empleado.java), y antes de esta migracion nada las vinculaba en el sentido
-- Usuario -> Empleado.
--
-- No distingue por rol: tambien el admin queda con su ficha de Empleado. Es
-- inofensivo (una fila mas que puede quedar sin usar) y evita el caso
-- especial de "que rol si, que rol no" para una decision que no tiene una
-- respuesta correcta unica.

insert into empleado (nombre, activo, username)
select u.nombre, true, u.username
from usuario u
where not exists (
    select 1 from empleado e where e.username = u.username
);
