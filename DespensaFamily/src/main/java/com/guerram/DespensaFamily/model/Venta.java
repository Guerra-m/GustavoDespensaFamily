package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // LocalDateTime y no LocalDate: el campo siempre se llamo "fechaHora"
    // pero guardaba solo el dia, asi que dos ventas de la misma jornada eran
    // indistinguibles y el ticket no podia decir a que hora se cobro.
    private LocalDateTime fechaHora;
    private double total;

    // Quien la hizo. Sin esto, con dos o tres empleados rotando en la caja no
    // hay forma de reconstruir que paso.
    private String usuario;

    // Rastro de la anulacion. Anular devuelve stock y saca la venta de los
    // totales: tiene que quedar constancia de quien lo hizo y cuando.
    private String anuladaPor;
    private LocalDateTime fechaAnulacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoVenta estado;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    private List<DetalleVenta> detalles;

    @PrePersist
    public void prePersist() {
        if (estado == null) {
            estado = EstadoVenta.ACTIVA;
        }
    }

}
