package com.guerram.DespensaFamily.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraDTO {
    private Long id;
    private LocalDateTime fechaHora;
    private String proveedor;
    private String usuario;
    private com.guerram.DespensaFamily.model.EstadoCompra estado;
    private String anuladaPor;
    private LocalDateTime fechaAnulacion;
}
