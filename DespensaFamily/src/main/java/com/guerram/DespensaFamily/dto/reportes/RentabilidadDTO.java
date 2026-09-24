package com.guerram.DespensaFamily.dto.reportes;

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
    private double ventasTotales;
    private double costoMercaderiaVendida;
    private double gananciaBruta;
    /** ganancia / ventas × 100. 0 si no hubo ventas (evita dividir por cero). */
    private double margenPorcentaje;
    /** Total comprado en el período — reposición de stock, NO es un costo del período. */
    private double comprasTotales;
    /** Solo categoría GASTO: RETIRO y APORTE son movimientos de capital, no gastos operativos. */
    private double gastosTotales;
    private boolean gastosCargados;
    private double resultadoNeto;
}
