package com.guerram.DespensaFamily.dto;

import com.guerram.DespensaFamily.model.EstadoVenta;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDetalleResponseDTO {
    private Long id;
    private LocalDateTime fechaHora;
    private double total;
    private com.guerram.DespensaFamily.model.MetodoPago metodoPago;
    private String cliente;
    private List<DetalleVentaDTO> detalles;
    private EstadoVenta estado;
    private String usuario;
    private String anuladaPor;
    private LocalDateTime fechaAnulacion;
}
