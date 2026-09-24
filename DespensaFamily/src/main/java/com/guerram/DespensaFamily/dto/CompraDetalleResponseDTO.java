package com.guerram.DespensaFamily.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraDetalleResponseDTO {
    private Long id;
    private LocalDateTime fechaHora;
    private double total;
    private boolean pagada;
    private com.guerram.DespensaFamily.model.MetodoPago metodoPago;
    private String proveedor;
    private List<DetalleCompraDTO> detalles;
    private String usuario;
    private com.guerram.DespensaFamily.model.EstadoCompra estado;
    private String anuladaPor;
    private LocalDateTime fechaAnulacion;
}
