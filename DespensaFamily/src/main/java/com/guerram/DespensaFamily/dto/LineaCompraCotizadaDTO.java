package com.guerram.DespensaFamily.dto;

import java.math.BigDecimal;

import lombok.*;

/**
 * Una linea de POST /compras/previsualizar: que precio de venta tiene HOY el
 * producto y a cual pasaria si se confirmara esta compra, calculado con la
 * MISMA formula que usa CompraService.registrarCompra (Dinero.
 * precioConGanancia) — no hay una segunda cuenta en el frontend (ver
 * DF-RISK-001 y el comentario equivalente en LineaCotizadaDTO de ventas).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineaCompraCotizadaDTO {
    private Long productoId;
    private String productoNombre;
    private BigDecimal precioCostoActual;
    private BigDecimal precioVentaActual;
    private BigDecimal precioVentaNuevo;

    /**
     * true si precioVentaNuevo difiere de precioVentaActual en mas de 50%,
     * para que el frontend resalte la linea antes de pedir confirmacion
     * (DF-RISK-001: un cero de mas en el costo multiplica por 10 el precio).
     */
    private boolean cambioSignificativo;
}
