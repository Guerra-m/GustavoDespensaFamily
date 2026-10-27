package com.guerram.DespensaFamily.dto;

import java.math.BigDecimal;

/**
 * DF-SEC-003 (API-04): el producto como lo ve quien vende sin gestionar el
 * catalogo. Lo mismo que ProductoDTO menos precioCosto, porcentajeGanancia y
 * proveedor: los campos no viajan, no vienen en null ni en cero.
 *
 * <p>Es un tipo aparte y no ProductoDTO con campos anulados a proposito: asi
 * un campo nuevo de costo que se agregue a ProductoDTO no se filtra solo al
 * empleado — hay que agregarlo aca a mano, a conciencia.
 */
public record ProductoParaVentaDTO(
        Long id,
        String nombre,
        BigDecimal precioVenta,
        BigDecimal stock,
        String tipoCantidad,
        String unidadMedida,
        BigDecimal fraccionMinima,
        Integer unidadesPorPaquete,
        String codigoBarras,
        String imagenUrl,
        Long categoriaId
) {
    public static ProductoParaVentaDTO de(ProductoDTO p) {
        return new ProductoParaVentaDTO(
                p.getId(),
                p.getNombre(),
                p.getPrecioVenta(),
                p.getStock(),
                p.getTipoCantidad(),
                p.getUnidadMedida(),
                p.getFraccionMinima(),
                p.getUnidadesPorPaquete(),
                p.getCodigoBarras(),
                p.getImagenUrl(),
                p.getCategoriaId());
    }
}
