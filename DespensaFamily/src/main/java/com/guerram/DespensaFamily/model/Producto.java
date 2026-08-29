package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

import com.guerram.DespensaFamily.util.Cantidades;

import java.util.List;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Producto {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private double precioVenta;
    private double precioCosto;

    private double porcentajeGanancia;

    private String codigoBarras;
    private String proveedor;

    private double stock; // antes int

    /**
     * Redondeado a 3 decimales al escribir y al leer (ver {@link Cantidades}).
     *
     * <p>Al escribir, para que la base no acumule la basura del double: vender
     * 1,538 kg de un producto con 5 kg dejaba el stock en 3.4619999999999997.
     * Al leer, para que las filas que ya quedaron sucias se muestren bien y no
     * hagan fallar el "stock &lt; cantidad" de una venta al vender lo ultimo
     * que queda.
     *
     * <p>Escritos a mano a proposito: Lombok no genera los que ya existen, y
     * Hibernate accede por campo (el {@code @Id} esta en el campo), asi que
     * este redondeo no interfiere con la carga desde la base.
     */
    public double getStock() {
        return Cantidades.redondear(stock);
    }

    public void setStock(double stock) {
        this.stock = Cantidades.redondear(stock);
    }

    // NUEVO 👇
    private String tipoCantidad; // "UNIDAD" | "PESO"
    private String unidadMedida; // "u" | "kg"
    private Double fraccionMinima; // 1 o 0.001

    // Cuántas unidades sueltas trae un paquete de este producto (ej: 8).
    // Solo aplica si tipoCantidad = "UNIDAD". El stock siempre se guarda
    // en unidades sueltas; esto es solo para comprar/mostrar por paquete.
    private Integer unidadesPorPaquete;

    @ManyToOne
    @JoinColumn(name= "categoria_id")
    private Categoria categoria;

    @OneToMany(mappedBy = "producto")
    private List<DetalleVenta> detallesVenta;

    @OneToMany(mappedBy = "producto")
    private List<DetalleCompra> detallesCompra;

    private String imagenUrl;


    private boolean activo = true;//Borrado lógico

    // Locking optimista: evita perder actualizaciones de stock cuando dos
    // ventas/compras concurrentes tocan el mismo producto (ver VentaService/
    // CompraService, que reintentan sobre ObjectOptimisticLockingFailureException).
    @Version
    private Long version;
}
