package com.guerram.DespensaFamily.dto.ia;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Lo que la IA cree haber identificado de un producto, a partir de texto o
// una foto. Nunca se guarda directamente: el usuario lo revisa/corrige en el
// preview y recien ahi se manda al alta normal de producto (POST /productos),
// que valida todo de nuevo como si lo hubiera tipeado una persona.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductoExtraidoDTO {
    private String nombre;
    private String marca;
    private String presentacion;
    private String unidad;
    private String categoriaSugerida;
    private String codigoBarras;

    // null si la IA no pudo leer el precio en la imagen/texto — nunca se
    // inventa un valor. El front lo marca como "falta precio" en ese caso.
    private BigDecimal precio;

    // "ALTA" | "MEDIA" | "BAJA": que tan segura dice estar la IA de este
    // renglon. El front resalta en amarillo/rojo lo que no es ALTA para que
    // el usuario lo revise antes de confirmar.
    private String confianza;
}
