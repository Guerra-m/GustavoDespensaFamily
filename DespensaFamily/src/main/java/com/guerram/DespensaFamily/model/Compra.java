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

    // Congelado al registrar la compra (ver CompraService.registrarCompra), no
    // recalculado sumando detalles cada vez. Es un hecho historico igual que
    // Venta.total: si el dia de manana cambia como se redondea un subtotal, las
    // compras viejas no tienen por que cambiar de importe con retroactividad.
    private double total;

    // Si ya se le pago al proveedor. false = a cuenta: entro la mercaderia
    // pero no salio plata de caja todavia (ver CompraService y Fase 11 del
    // mapa de cambios). Se puede marcar pagada despues con /compras/{id}/pagar.
    private boolean pagada;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago")
    private MetodoPago metodoPago;

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
