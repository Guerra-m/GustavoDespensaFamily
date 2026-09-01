package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compra {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fechaHora;
    private String proveedor;

    // Quien la registro (ver el comentario equivalente en Venta).
    private String usuario;

    // Una compra mal cargada inflaba el stock y reescribia el precio de venta
    // del producto, y no habia forma de deshacerla: quedaba mal para siempre.
    @Enumerated(EnumType.STRING)
    private EstadoCompra estado;

    private String anuladaPor;
    private LocalDateTime fechaAnulacion;

    @PrePersist
    public void prePersist() {
        if (estado == null) {
            estado = EstadoCompra.ACTIVA;
        }
    }

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL)
    private List<DetalleCompra> detalles;
}
