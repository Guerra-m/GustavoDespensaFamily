package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.reportes.GananciaPorFechaDTO;
import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoBajoStockDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoMasVendidoDTO;
import com.guerram.DespensaFamily.dto.reportes.RentabilidadDTO;
import com.guerram.DespensaFamily.dto.reportes.ResumenVentasDTO;
import com.guerram.DespensaFamily.dto.reportes.VentasPorCategoriaDTO;
import com.guerram.DespensaFamily.service.ReportesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
public class ReportesController {

    private final ReportesService reportesService;

    @GetMapping("/productos-bajo-stock")
    public List<ProductoBajoStockDTO> productosBajoStock(
            @RequestParam(required = false) BigDecimal limite
    ) {
        return reportesService.productosConBajoStock(limite);
    }

    @GetMapping("/resumen")
    public ResumenVentasDTO resumenVentas(
            @RequestParam String periodo
    ) {
        return reportesService.resumenVentas(periodo);
    }

    /** DF-PERF-002: 'top' es opcional -- sin el, el service aplica un default razonable. */
    @GetMapping("/productos-mas-vendidos")
    public List<ProductoMasVendidoDTO> productosMasVendidos(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta,
            @RequestParam(required = false) Integer top
    ) {
        return top != null
                ? reportesService.productosMasVendidosTop(desde, hasta, top)
                : reportesService.productosMasVendidos(desde, hasta);
    }

    @GetMapping("/ventas-por-categoria")
    public List<VentasPorCategoriaDTO> ventasPorCategoria(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta
    ) {
        return reportesService.ventasPorCategoria(desde, hasta);
    }

    @GetMapping("/ganancia")
    public List<GananciaPorFechaDTO> gananciaPorFecha(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta
    ) {
        return reportesService.gananciaPorFecha(desde, hasta);
    }

    @GetMapping("/rentabilidad")
    public RentabilidadDTO rentabilidad(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta
    ) {
        return reportesService.rentabilidad(desde, hasta);
    }

    @GetMapping("/movimientos")
    public List<MovimientoDTO> movimientosPorFecha(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta,
            @RequestParam(required = false) String tipo // COMPRA | VENTA
    ) {
        return reportesService.movimientosPorFecha(desde, hasta, tipo);
    }

}
