package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.reportes.GananciaPorFechaDTO;
import com.guerram.DespensaFamily.dto.reportes.MovimientoDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoBajoStockDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoMasVendidoDTO;
import com.guerram.DespensaFamily.dto.reportes.RentabilidadDTO;
import com.guerram.DespensaFamily.dto.reportes.ResumenVentasDTO;
import com.guerram.DespensaFamily.dto.reportes.VentasPorCategoriaDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.repository.DetalleCompraRepository;
import com.guerram.DespensaFamily.repository.DetalleVentaRepository;
import com.guerram.DespensaFamily.repository.MovimientoCajaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final ConfiguracionService configuracionService;

    /**
     * DF-BUG-008 de la auditoria del 23/09: con umbral 5 fijo, Reportes
     * marcaba "bajo stock" con un criterio distinto al resto de la app en
     * cuanto el dueño cambiaba el umbral en Ajustes — Ventas y Productos ya
     * usan {@link ConfiguracionService#obtener()}, Reportes no. El default
     * ahora es el mismo umbral configurado, no un numero de fabrica.
     */
    public List<ProductoBajoStockDTO> productosConBajoStock(Double limite) {

        if (limite == null || limite <= 0) {
            limite = configuracionService.obtener().getUmbralStockBajo();
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
                .totalVentasEntreFechas(inicioDe(desde), finDe(hoy));

        Long cantidadVentas = ventaRepository
                .cantidadVentasEntreFechas(inicioDe(desde), finDe(hoy));

        Double totalCosto = detalleVentaRepository
                .totalCostoEntreFechas(inicioDe(desde), finDe(hoy));

        Double ganancia = totalVentas - totalCosto;

        return ResumenVentasDTO.builder()
                .totalVentas(totalVentas)
                .cantidadVentas(cantidadVentas)
                .ganancia(ganancia)
                .build();
    }

    /** DF-PERF-002: sin top explicito, un default razonable en vez de traer todo el historial. */
    private static final int TOP_PRODUCTOS_MAS_VENDIDOS_POR_DEFECTO = 20;

    public List<ProductoMasVendidoDTO> productosMasVendidos(
            LocalDate desde,
            LocalDate hasta
    ) {
        return productosMasVendidosTop(desde, hasta, TOP_PRODUCTOS_MAS_VENDIDOS_POR_DEFECTO);
    }

    /**
     * DF-PERF-002 de la auditoria del 23/09: antes devolvia TODOS los
     * productos vendidos alguna vez en el rango (166 KB medidos contra la
     * base real) y filtraba productos inactivos con un findById POR FILA
     * (N+1). Ahora el filtro de activos esta en el SQL
     * (DetalleVentaRepository.productosMasVendidos) y el resultado se acota
     * con un top explicito via Pageable, sin traer de mas para despues
     * descartarlo en Java.
     */
    public List<ProductoMasVendidoDTO> productosMasVendidosTop(
            LocalDate desde,
            LocalDate hasta,
            int top
    ) {
        int limite = top > 0 ? top : TOP_PRODUCTOS_MAS_VENDIDOS_POR_DEFECTO;
        return detalleVentaRepository.productosMasVendidos(
                inicioDe(desde), finDe(hasta), PageRequest.of(0, limite));
    }

    public List<VentasPorCategoriaDTO> ventasPorCategoria(
            LocalDate desde,
            LocalDate hasta
    ) {
        return detalleVentaRepository.ventasPorCategoria(inicioDe(desde), finDe(hasta));
    }

    public List<GananciaPorFechaDTO> gananciaPorFecha(
            LocalDate desde,
            LocalDate hasta
    ) {
        return detalleVentaRepository.gananciaPorFecha(inicioDe(desde), finDe(hasta));
    }

    public List<MovimientoDTO> movimientosPorFecha(
            LocalDate desde,
            LocalDate hasta,
            String tipo
    ) {
        List<MovimientoDTO> movimientos = new ArrayList<>();

        // Movimientos de compras
        if (tipo == null || tipo.equalsIgnoreCase("COMPRA")) {
            movimientos.addAll(detalleCompraRepository.movimientosCompras(inicioDe(desde), finDe(hasta)));
        }

        // Movimientos de ventas
        if (tipo == null || tipo.equalsIgnoreCase("VENTA")) {
            movimientos.addAll(detalleVentaRepository.movimientosVentas(inicioDe(desde), finDe(hasta)));
        }

        // DF-PERF-002: el filtro de "producto activo" ya viene resuelto en
        // el SQL de movimientosVentas/movimientosCompras (AND p.activo =
        // true) -- antes esto hacia un productoRepository.findById POR CADA
        // FILA del resultado (N+1 medido en 1,09 s para 2 meses de datos
        // reales).

        // Ordenar por fecha
        movimientos.sort(Comparator.comparing(MovimientoDTO::getFecha));

        return movimientos;
    }





    /**
     * Fase 8/9 del mapa de cambios. Cada número separado explícitamente para
     * que la pantalla no pueda confundir ventas con ingresos, ni compras con
     * gastos: comprar mercadería reduce caja pero no es una pérdida, la
     * ganancia aparece cuando esa mercadería se vende (ver
     * {@link #resumenVentas} / {@link #gananciaPorFecha}, que ya calculan
     * bien la ganancia bruta con el costo real de lo vendido).
     */
    public RentabilidadDTO rentabilidad(LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = inicioDe(desde);
        LocalDateTime fin = finDe(hasta);

        double ventas = ventaRepository.totalVentasEntreFechas(inicio, fin);
        double costo = detalleVentaRepository.totalCostoEntreFechas(inicio, fin);
        double gananciaBruta = ventas - costo;
        double margen = ventas > 0 ? (gananciaBruta / ventas) * 100 : 0.0;

        double compras = movimientoCajaRepository
                .totalNetoPorCategoriaEntreFechas(CategoriaMovimiento.COMPRA, inicio, fin);
        double gastos = movimientoCajaRepository
                .totalNetoPorCategoriaEntreFechas(CategoriaMovimiento.GASTO, inicio, fin);
        boolean gastosCargados = movimientoCajaRepository
                .existsByCategoriaAndFechaHoraBetween(CategoriaMovimiento.GASTO, inicio, fin);

        return RentabilidadDTO.builder()
                .ventasTotales(ventas)
                .costoMercaderiaVendida(costo)
                .gananciaBruta(gananciaBruta)
                .margenPorcentaje(margen)
                .comprasTotales(compras)
                .gastosTotales(gastos)
                .gastosCargados(gastosCargados)
                .resultadoNeto(gananciaBruta - gastos)
                .build();
    }

    // La API sigue hablando en dias (?desde=2026-09-01), pero las ventas ahora
    // guardan la hora. Un BETWEEN entre dos LocalDate.atStartOfDay() dejaria
    // afuera todo lo vendido despues de las 00:00 del dia "hasta", que es casi
    // toda la jornada.
    private static LocalDateTime inicioDe(LocalDate dia) {
        return dia.atStartOfDay();
    }

    private static LocalDateTime finDe(LocalDate dia) {
        return dia.atTime(LocalTime.MAX);
    }
}
