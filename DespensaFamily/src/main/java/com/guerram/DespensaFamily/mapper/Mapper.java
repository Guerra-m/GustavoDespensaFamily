package com.guerram.DespensaFamily.mapper;

import com.guerram.DespensaFamily.dto.*;
import com.guerram.DespensaFamily.model.*;

public class Mapper {

    // ----------- PRODUCTO -------------
    public static ProductoDTO toDTO(Producto p) {
        if (p == null) return null;

        return ProductoDTO.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .precioVenta(p.getPrecioVenta())
                .precioCosto(p.getPrecioCosto())
                .porcentajeGanancia(p.getPorcentajeGanancia())
                .stock(p.getStock())
                .codigoBarras(p.getCodigoBarras())
                .categoriaId(p.getCategoria() != null ? p.getCategoria().getId() : null)
                .proveedor(p.getProveedor())
                .imagenUrl(p.getImagenUrl() != null ?"http://localhost:8080/imagenes/" + p.getImagenUrl()
                        : null )
                .tipoCantidad(p.getTipoCantidad())
                .unidadMedida(p.getUnidadMedida())
                .fraccionMinima(p.getFraccionMinima())
                .build();
    }


    // ----------- CATEGORIA -------------
    public static CategoriaDTO toDTO(Categoria c) {
        if (c == null) return null;

        return CategoriaDTO.builder()
                .id(c.getId())
                .nombre(c.getNombre())
                .descripcion(c.getDescripcion())
                .build();
    }


    // ----------- VENTA -------------
    public static VentaDTO toDTO(Venta venta) {
        if (venta == null) return null;

        return VentaDTO.builder()
                .id(venta.getId())
                .fechaHora(venta.getFechaHora())
                .total(venta.getTotal())
                .estado(venta.getEstado())
                .build();
    }


    // ----------- DETALLE DE VENTA -------------
    public static DetalleVentaDTO toDTO(DetalleVenta dv) {
        if (dv == null) return null;

        return DetalleVentaDTO.builder()
                .id(dv.getId())
                .cantidad(dv.getCantidad())
                .precioUnitario(dv.getPrecioUnitario())
                .precioCosto(dv.getPrecioCosto())
                .subtotal(dv.getSubtotal())
                .productoId(dv.getProducto() != null ? dv.getProducto().getId() : null)
                .ventaId(dv.getVenta() != null ? dv.getVenta().getId() : null)
                .build();
    }


    // ----------- COMPRA -------------
    public static CompraDTO toDTO(Compra compra) {
        if (compra == null) return null;

        return CompraDTO.builder()
                .id(compra.getId())
                .fechaHora(compra.getFechaHora())
                .proveedor(compra.getProveedor())
                .build();
    }


    // ----------- DETALLE DE COMPRA -------------
    public static DetalleCompraDTO toDTO(DetalleCompra dc) {
        if (dc == null) return null;

        return DetalleCompraDTO.builder()
                .id(dc.getId())
                .cantidad(dc.getCantidad())
                .precioCosto(dc.getPrecioCosto())
                .subtotal(dc.getSubtotal())
                .productoId(dc.getProducto() != null ? dc.getProducto().getId() : null)
                .compraId(dc.getCompra() != null ? dc.getCompra().getId() : null)
                .build();
    }
    public static Categoria toEntity(CategoriaDTO dto) {
        if (dto == null) return null;

        return Categoria.builder()
                .id(dto.getId()) // null en POST → OK
                .nombre(dto.getNombre())
                .descripcion(dto.getDescripcion())
                .build();
    }

    public static Producto toEntity(ProductoDTO dto) {
        if (dto == null) return null;

        Producto p = Producto.builder()
                .nombre(dto.getNombre())
                .precioVenta(dto.getPrecioVenta())
                .precioCosto(dto.getPrecioCosto())
                .porcentajeGanancia(dto.getPorcentajeGanancia())
                .stock(dto.getStock())
                .codigoBarras(dto.getCodigoBarras())
                .proveedor(dto.getProveedor())
                .imagenUrl(dto.getImagenUrl())
                .tipoCantidad(dto.getTipoCantidad() != null ? dto.getTipoCantidad() : "UNIDAD")
                .unidadMedida(dto.getUnidadMedida() != null ? dto.getUnidadMedida() : "u")
                .fraccionMinima(dto.getFraccionMinima() != null ? dto.getFraccionMinima() : 1.0)
                .build();

        return p;
    }

    public static Venta toEntity(VentaDTO dto) {
        if (dto == null) return null;

        return Venta.builder()
                .id(dto.getId())
                .fechaHora(dto.getFechaHora())
                .total(dto.getTotal())
                .build();
    }
    public static DetalleVenta toEntity(DetalleVentaDTO dto) {
        if (dto == null) return null;

        DetalleVenta dv = DetalleVenta.builder()
                .id(dto.getId())
                .cantidad(dto.getCantidad())
                .precioUnitario(dto.getPrecioUnitario())
                .precioCosto(dto.getPrecioCosto())
                .subtotal(dto.getSubtotal())
                .build();
        return dv;
    }
    public static Compra toEntity(CompraDTO dto) {
        if (dto == null) return null;

        return Compra.builder()
                .id(dto.getId())
                .fechaHora(dto.getFechaHora())
                .proveedor(dto.getProveedor())
                .build();
    }

    public static DetalleCompra toEntity(DetalleCompraDTO dto) {
        if (dto == null) return null;

        DetalleCompra dc = DetalleCompra.builder()
                .id(dto.getId())
                .cantidad(dto.getCantidad())
                .precioCosto(dto.getPrecioCosto())
                .subtotal(dto.getSubtotal())
                .build();

        return dc;
    }
    public static VentaDetalleResponseDTO toVentaDetalleResponse(Venta venta) {
        if (venta == null) return null;

        return VentaDetalleResponseDTO.builder()
                .id(venta.getId())
                .fechaHora(venta.getFechaHora())
                .total(venta.getTotal())
                .estado(venta.getEstado())
                .detalles(
                        venta.getDetalles()
                                .stream()
                                .map(Mapper::toDTO)
                                .toList()
                )
                .build();
    }
    public static CompraDetalleResponseDTO toCompraDetalleResponse(Compra compra) {
        if (compra == null) return null;

        return CompraDetalleResponseDTO.builder()
                .id(compra.getId())
                .fechaHora(compra.getFechaHora())
                .proveedor(compra.getProveedor())
                .detalles(
                        compra.getDetalles()
                                .stream()
                                .map(Mapper::toDTO)
                                .toList()
                )
                .build();
    }
    // ----------- USUARIO -------------
    public static UsuarioDTO toDTO(Usuario usuario) {
        if (usuario == null) return null;

        return UsuarioDTO.builder()
                .id(usuario.getId())
                .username(usuario.getUsername())
                .nombre(usuario.getNombre())
                .build();
    }
    public static Usuario toEntity(UsuarioDTO dto) {
        if (dto == null) return null;

        return Usuario.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .nombre(dto.getNombre())
                .build();
    }


}