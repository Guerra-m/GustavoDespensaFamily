package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.Compra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {

    // DF-PERF-001: mismo patron que VentaRepository.buscarPaginado --
    // desde/hasta opcionales, mas reciente primero.
    @Query("""
    SELECT c FROM Compra c
    WHERE (:desde IS NULL OR c.fechaHora >= :desde)
    AND (:hasta IS NULL OR c.fechaHora <= :hasta)
    ORDER BY c.fechaHora DESC
    """)
    Page<Compra> buscarPaginado(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);
}
