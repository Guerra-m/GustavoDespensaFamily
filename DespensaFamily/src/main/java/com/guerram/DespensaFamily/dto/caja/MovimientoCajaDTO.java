package com.guerram.DespensaFamily.dto.caja;

import java.math.BigDecimal;

import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MovimientoCajaDTO {
    private Long id;
    private LocalDateTime fechaHora;
    private TipoMovimiento tipo;
    private CategoriaMovimiento categoria;
    private BigDecimal monto;
    private MetodoPago metodoPago;
    private String descripcion;
    private String usuario;
    private Long ventaId;
    private Long compraId;
    private Long empleadoId;
    private Long revierteA;
    /** true si algun otro movimiento ya lo revirtio. Calculado, no persistido. */
    private boolean revertido;
}
