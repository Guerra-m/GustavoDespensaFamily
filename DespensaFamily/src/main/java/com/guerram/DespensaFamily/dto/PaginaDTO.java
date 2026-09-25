package com.guerram.DespensaFamily.dto;

import java.util.List;
import lombok.*;

/**
 * DF-PERF-001: envoltorio propio en vez de exponer org.springframework.data.domain.Page
 * directo -- Page serializa con un monton de metadata interna (pageable,
 * sort, etc) que el frontend no necesita y que ata la respuesta HTTP a la
 * version de Spring Data. Esto es lo unico que /ventas/pagina y
 * /compras/pagina necesitan mandar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginaDTO<T> {
    private List<T> contenido;
    private long totalElementos;
    private int totalPaginas;
    private int pagina;
    private int tamanio;
}
