package com.guerram.DespensaFamily.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Fila única (id siempre 1L) con los ajustes globales del negocio. No hace
// falta una tabla de key-value genérica para tres campos — el día que
// crezca a muchos más ajustes, ahí sí se justifica repensarlo.
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuracion {

    @Id
    private Long id;

    @jakarta.persistence.Column(precision = 15, scale = 3)
    private BigDecimal umbralStockBajo;
    private String nombreNegocio;

    @jakarta.persistence.Column(precision = 15, scale = 2)
    private BigDecimal gananciaPorDefecto;

    @Version
    private Long version;
}
