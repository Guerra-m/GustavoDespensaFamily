package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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
    @Column(precision = 15, scale = 2)
    private BigDecimal total;

    // Como se cobro. Denormalizado ademas del movimiento de caja que genera
    // (ver VentaService.registrarVenta): el ticket y el listado necesitan
    // mostrarlo sin salir a buscarlo a otra tabla.
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago")
    private MetodoPago metodoPago;

    // Nombre de quien compra, opcional y de texto libre — sale en el ticket.
    // No es una entidad a proposito (decision del dueño): sirve para saber a
    // quien es el ticket, no para agrupar compras por persona.
    private String cliente;

    // Quien la hizo. Sin esto, con dos o tres empleados rotando en la caja no
    // hay forma de reconstruir que paso.
    private String usuario;

    // Rastro de la anulacion. Anular devuelve stock y saca la venta de los
    // totales: tiene que quedar constancia de quien lo hizo y cuando.
    private String anuladaPor;
    private LocalDateTime fechaAnulacion;

    // DF-BUG-003: clave que manda el frontend por intento de confirmar la
    // venta (un UUID por click). Nullable porque las ventas viejas y las que
    // se crean sin pasar por el header (tests, integraciones) no la tienen;
    // unique para que la propia base sea la ultima linea de defensa contra
    // dos peticiones concurrentes con la misma clave (ver V12 y
    // VentaService.registrarVenta).
    @Column(name = "clave_idempotencia", unique = true, length = 64)
    private String claveIdempotencia;

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
        // Mismo default que la columna en la base (V5): cubre las ventas de
        // pruebas que arman el objeto sin pasar por VentaService.
        if (metodoPago == null) {
            metodoPago = MetodoPago.EFECTIVO;
        }
    }

}
