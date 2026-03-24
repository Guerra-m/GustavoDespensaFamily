package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.EstadoVenta;
import com.guerram.DespensaFamily.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {

    @Query("""
    SELECT COALESCE(SUM(v.total), 0)
    FROM Venta v
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND v.estado = 'ACTIVA'
""")
    Double totalVentasEntreFechas(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );

    @Query("""
    SELECT COUNT(v)
    FROM Venta v
    WHERE v.fechaHora BETWEEN :desde AND :hasta
    AND v.estado = 'ACTIVA'
""")
    Long cantidadVentasEntreFechas(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta
    );
    List<Venta> findByEstado(EstadoVenta estado);

}
