package com.guerram.DespensaFamily.dto;

import java.math.BigDecimal;

import lombok.*;

/**
 * Una linea de /ventas/cotizar: lo que se cobraria por este producto y
 * cantidad si se confirmara la venta ahora mismo, calculado con el MISMO
 * metodo que usa VentaService.registrarVenta (ver
 * VentaService.calcularLinea) — no hay una segunda cuenta en el frontend.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineaCotizadaDTO {
    private Long productoId;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;

    /** Null si no hay descuento por % activo para esta linea. */
    private BigDecimal descuentoPorcentaje;

    /** Null si no hay combo activo. Ej: "3x2". */
    private String promoDescripcion;

    /**
     * true si al momento de cotizar no habia stock suficiente para la
     * cantidad pedida. cotizar() NO rechaza la linea por esto — a diferencia
     * de registrarVenta, que si la rechaza — para que el carrito pueda
     * mostrar el total y avisar, en vez de bloquear la pantalla mientras el
     * cliente todavia esta ajustando cantidades.
     */
    private boolean stockInsuficiente;
}
