package com.guerram.DespensaFamily.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo del ajuste rapido de precio y stock.
 *
 * <p>Los tres campos son opcionales: se manda solo lo que se quiere cambiar.
 * {@code stock} es Double porque hay productos por peso.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarStockPrecioDTO {
    private BigDecimal precioVenta;
    private BigDecimal precioCosto;
    private BigDecimal stock;
}
