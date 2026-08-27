package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.reportes.GananciaPorFechaDTO;
import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoBajoStockDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoMasVendidoDTO;
import com.guerram.DespensaFamily.dto.reportes.ResumenVentasDTO;
import com.guerram.DespensaFamily.dto.reportes.VentasPorCategoriaDTO;
import com.guerram.DespensaFamily.repository.DetalleCompraRepository;
import com.guerram.DespensaFamily.repository.DetalleVentaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportesService {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final DetalleCompraRepository detalleCompraRepository;
    public List<ProductoBajoStockDTO> productosConBajoStock(Double limite) {

        if (limite == null || limite <= 0) {
            limite = 5.0;
        }

        return productoRepository.findByStockLessThanAndActivoTrue(limite)
                .stream()
                .map(p -> ProductoBajoStockDTO.builder()
                        .id(p.getId())
                        .nombre(p.getNombre())
                        .stock(p.getStock())
                        .build())
                .toList();
    }

    public ResumenVentasDTO resumenVentas(String periodo) {

        LocalDate hoy = LocalDate.now();
        LocalDate desde;

        switch (periodo.toLowerCase()) {
            case "dia" -> desde = hoy;
            case "semana" -> desde = hoy.minusDays(7);
            case "mes" -> desde = hoy.minusDays(30);
            default -> throw new RuntimeException("Periodo inválido");
        }

        Double totalVentas = ventaRepository
                .totalVentasEntreFechas(desde, hoy);

        Long cantidadVentas = ventaRepository
                .cantidadVentasEntreFechas(desde, hoy);

        Double totalCosto = detalleVentaRepository
                .totalCostoEntreFechas(desde, hoy);

        Double ganancia = totalVentas - totalCosto;

        return ResumenVentasDTO.builder()
                .totalVentas(totalVentas)
                .cantidadVentas(cantidadVentas)
                .ganancia(ganancia)
                .build();
    }

    public List<ProductoMasVendidoDTO> productosMasVendidos(
            LocalDate desde,
            LocalDate hasta
    ) {
        return detalleVentaRepository.productosMasVendidos(desde, hasta)
                .stream()
                .filter(p -> productoRepository.findById(p.getProductoId())
                        .map(prod -> prod.isActivo())
                        .orElse(false)) // 🔹 solo productos activos
                .toList();
    }

    public List<VentasPorCategoriaDTO> ventasPorCategoria(
            LocalDate desde,
            LocalDate hasta
    ) {
        return detalleVentaRepository.ventasPorCategoria(desde, hasta);
    }

    public List<GananciaPorFechaDTO> gananciaPorFecha(
            LocalDate desde,
            LocalDate hasta
    ) {
        return detalleVentaRepository.gananciaPorFecha(desde, hasta);
    }

    public List<MovimientoDTO> movimientosPorFecha(
            LocalDate desde,
            LocalDate hasta,
            String tipo
    ) {
        List<MovimientoDTO> movimientos = new ArrayList<>();

        // Movimientos de compras
        if (tipo == null || tipo.equalsIgnoreCase("COMPRA")) {
            movimientos.addAll(detalleCompraRepository.movimientosCompras(desde, hasta));
        }

        // Movimientos de ventas
        if (tipo == null || tipo.equalsIgnoreCase("VENTA")) {
            movimientos.addAll(detalleVentaRepository.movimientosVentas(desde, hasta));
        }

        //  Filtrar productos eliminados
        movimientos.removeIf(m ->
                productoRepository.findById(m.getProductoId())
                        .map(prod -> !prod.isActivo()) // true si producto NO está activo
                        .orElse(true) // true si producto no existe
        );

        // Ordenar por fecha
        movimientos.sort(Comparator.comparing(MovimientoDTO::getFecha));

        return movimientos;
    }




}
