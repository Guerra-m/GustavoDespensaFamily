package com.guerram.DespensaFamily.dto.reportes;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Fase 8 y 9 del mapa de cambios: separa rentabilidad (¿cuánto gané?) de
 * flujo de caja (¿cuánto entró/salió?), y separa ganancia de gastos
 * operativos.
 *
 * <p>{@code gastosCargados} existe para no mostrar {@code resultadoNeto}
 * igual a {@code gananciaBruta} como si fuera un dato real: si nadie cargó
 * gastos en el período, esa igualdad no significa "no hubo gastos", significa
 * "no se sabe". La pantalla tiene que decir eso, no inventar un neto.
 */
@Getter
@Builder
@AllArgsConstructor
public class RentabilidadDTO {
    private BigDecimal ventasTotales;
    private BigDecimal costoMercaderiaVendida;
    private BigDecimal gananciaBruta;
    /** ganancia / ventas × 100. 0 si no hubo ventas (evita dividir por cero). */
    private BigDecimal margenPorcentaje;
    /** Total comprado en el período — reposición de stock, NO es un costo del período. */
    private BigDecimal comprasTotales;
    /** Solo categoría GASTO: RETIRO y APORTE son movimientos de capital, no gastos operativos. */
    private BigDecimal gastosTotales;
    private boolean gastosCargados;
    private BigDecimal resultadoNeto;
}
