package com.guerram.DespensaFamily.dto;

import java.util.List;
import lombok.*;

/** Respuesta de POST /compras/previsualizar — ver CompraService.previsualizar. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraCotizacionDTO {
    private List<LineaCompraCotizadaDTO> lineas;
}
