package com.guerram.DespensaFamily.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraDTO {
    private Long id;
    private LocalDate fechaHora;
    private String proveedor;
}
