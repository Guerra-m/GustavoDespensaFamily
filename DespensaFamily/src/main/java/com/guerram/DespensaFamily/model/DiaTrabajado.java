package com.guerram.DespensaFamily.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una fila = un empleado trabajó un día. No una grilla de 7 columnas: la
 * grilla semanal es la vista, esto es el dato — sirve igual para "cuánto
 * trabajó en septiembre" que para la semana actual.
 *
 * <p>Sin booleano "trabajó sí/no": si la fila existe, trabajó. Un campo
 * menos donde equivocarse.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaTrabajado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    private LocalDate fecha;
}
