package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.Venta;
import org.springframework.data.repository.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DF-SEC-003: las ventas que puede leer un empleado sin PERM_CAJA — las que
 * hizo el, en el dia. El filtro de dueño y de fecha va DENTRO de la consulta
 * (AUTZ-02, AUTZ-09), no se trae todo y se recorta despues.
 *
 * <p>Aparte de VentaRepository a proposito: es solo lectura (extiende
 * {@link Repository}, sin save ni delete) y no se cruza con los cambios de
 * paginacion que se hacen sobre VentaRepository en paralelo.
 *
 * <p>{@code Venta.usuario} es el username en texto, no una FK: es lo que
 * VentaService graba al registrar la venta, y el username no se puede
 * cambiar desde la aplicacion.
 */
public interface VentaDelEmpleadoRepository extends Repository<Venta, Long> {

    List<Venta> findByUsuarioAndFechaHoraBetweenOrderByIdAsc(
            String usuario, LocalDateTime desde, LocalDateTime hasta);

    Optional<Venta> findByIdAndUsuarioAndFechaHoraBetween(
            Long id, String usuario, LocalDateTime desde, LocalDateTime hasta);
}
