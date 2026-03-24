package com.guerram.DespensaFamily.model;

import jakarta.persistence.*;
import lombok.*;

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

    private int stock;
    private String codigoBarras;
    private String proveedor;

    @ManyToOne
    @JoinColumn(name= "categoria_id")
    private Categoria categoria;

    @OneToMany(mappedBy = "producto")
    private List<DetalleVenta> detallesVenta;

    @OneToMany(mappedBy = "producto")
    private List<DetalleCompra> detallesCompra;

    private String imagenUrl;


    private boolean activo = true;//Borrado lógico
}
