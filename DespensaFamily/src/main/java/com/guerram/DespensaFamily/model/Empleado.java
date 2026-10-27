package com.guerram.DespensaFamily.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una persona que cobra, no una cuenta de acceso.
 *
 * <p><b>No es lo mismo que {@link Usuario}.</b> Hay gente que trabaja y cobra
 * sin necesitar entrar al sistema (el que repone góndola, el que ayuda los
 * sábados) — si el sueldo colgara de Usuario, esa persona no existiría para
 * el sistema. Y borrar una cuenta de Usuario no puede borrar el historial de
 * pagos de la persona real: mismo criterio que {@code Venta.usuario}, que se
 * guarda como texto y no como relación, justamente para que la venta
 * sobreviva al borrado de la cuenta.
 *
 * <p>{@link #username} es opcional y suelto (no {@code @ManyToOne}): si la
 * persona además usa el sistema, se anota su username para relacionarlas en
 * la pantalla; si no, el empleado existe igual.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    /** Para no borrar historial: un empleado que se fue se desactiva, no se elimina. */
    private boolean activo;

    /** Opcional. Si está seteado, tiene que coincidir con Usuario.username, pero no hay FK. */
    private String username;
}
