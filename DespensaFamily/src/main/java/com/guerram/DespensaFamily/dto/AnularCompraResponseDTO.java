package com.guerram.DespensaFamily.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Respuesta de DELETE /compras/{id} — ver CompraService.anularCompra.
 *
 * <p>DF-BUG-005: si el precio de costo o de venta de un producto ya no es el
 * que dejo ESTA compra (porque alguien lo edito a mano despues, o porque
 * entro otra compra mas nueva del mismo producto), anular no lo pisa. El
 * nombre de esos productos queda aca para que el frontend avise en vez de
 * mostrar el precio cambiado sin explicacion.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnularCompraResponseDTO {
    @Builder.Default
    private List<String> productosConPrecioConservado = new ArrayList<>();
}
