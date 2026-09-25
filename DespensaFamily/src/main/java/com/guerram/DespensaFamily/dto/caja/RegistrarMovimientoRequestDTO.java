package com.guerram.DespensaFamily.dto.caja;

import java.math.BigDecimal;

import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Carga manual de un movimiento (GASTO, RETIRO, APORTE, AJUSTE). Las de
 * VENTA y COMPRA no pasan por acá: las genera el propio flujo de la venta o
 * la compra (ver VentaService, CompraService).
 */
@Getter
@Setter
@NoArgsConstructor
public class RegistrarMovimientoRequestDTO {

    @NotNull
    private TipoMovimiento tipo;

    @NotNull
    private CategoriaMovimiento categoria;

    @DecimalMin(value = "0.0", inclusive = false, message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotNull
    private MetodoPago metodoPago;

    private String descripcion;
}
