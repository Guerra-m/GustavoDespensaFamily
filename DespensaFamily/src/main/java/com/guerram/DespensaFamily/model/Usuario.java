package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password;
    private String nombre;
    private String rol; // ADMIN | EMPLEADO

    // El admin que crea el instalador arranca con una contrasena generada al
    // azar que quedo escrita en un archivo de texto: sirve para entrar la
    // primera vez, no para quedarse. Mientras esto sea true el frontend manda
    // al usuario a cambiarla antes de dejarlo usar el sistema.
    private boolean debeCambiarPassword;
}
