package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.*;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.Compra;
import com.guerram.DespensaFamily.model.EstadoCompra;
import com.guerram.DespensaFamily.model.DetalleCompra;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import com.guerram.DespensaFamily.repository.CompraRepository;
import com.guerram.DespensaFamily.repository.MovimientoCajaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.util.Cantidades;
import com.guerram.DespensaFamily.util.Dinero;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProductoRepository productoRepository;
    private final CajaService cajaService;
    private final MovimientoCajaRepository movimientoCajaRepository;

    // Ver comentario equivalente en VentaService.registrarVenta: mismo
    // riesgo de concurrencia sobre producto.stock, misma solución.
    @Transactional
    public CompraDTO registrarCompra(CompraRequestDTO request) {

        // null se trata como pagada=true: es el caso mas comun (se le paga al
        // proveedor al recibir la mercaderia) y el default que ya tenian
        // implicitamente las compras antes de que existiera este campo.
        boolean pagada = request.getPagada() == null || request.getPagada();

        Compra compra = new Compra();
        compra.setFechaHora(LocalDateTime.now());
        compra.setProveedor(request.getProveedor());
        compra.setPagada(pagada);
        compra.setMetodoPago(request.getMetodoPago());
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        compra.setUsuario(auth != null ? auth.getName() : null);

        List<DetalleCompra> detalles = new ArrayList<>();

        // Mismo orden de bloqueo que las ventas (ver VentaService): si compras y
        // ventas tomaran los candados en orden distinto, se trabarian entre si.
        List<DetalleCompraRequestDTO> enOrden = request.getDetalles().stream()
                .sorted(java.util.Comparator.comparing(DetalleCompraRequestDTO::getProductoId))
                .toList();

        double total = 0;

        for (DetalleCompraRequestDTO dto : enOrden) {

            Producto producto = productoRepository.buscarParaActualizarStock(dto.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            // REDONDEO
            double cantidad = Cantidades.redondear(dto.getCantidad());

            //  VALIDACIONES
            if (cantidad <= 0) {
                throw new RuntimeException("Cantidad inválida");
            }

            if ("UNIDAD".equals(producto.getTipoCantidad())) {
                if (cantidad % 1 != 0) {
                    throw new RuntimeException("Este producto no permite decimales");
                }
            }

            if (dto.getPrecioCosto() <= 0) {
                throw new RuntimeException("Precio de costo inválido");
            }

            // STOCK
            producto.setStock(producto.getStock() + cantidad);

            //  COSTO Y PRECIO
            // El costo previo queda en el detalle para poder deshacer la
            // compra entera despues (ver anularCompra).
            double costoPrevio = producto.getPrecioCosto();
            producto.setPrecioCosto(dto.getPrecioCosto());

            // Redondeado al centavo: sin esto quedaban precios como 164.1885,
            // que despues salian con cuatro decimales en la pantalla y en el
            // ticket (ver Dinero).
            double porcentaje = producto.getPorcentajeGanancia();
            producto.setPrecioVenta(Dinero.precioConGanancia(dto.getPrecioCosto(), porcentaje));

            // DETALLE
            DetalleCompra dc = new DetalleCompra();
            dc.setProducto(producto);
            dc.setCantidad(cantidad);
            dc.setPrecioCosto(dto.getPrecioCosto());
            dc.setPrecioCostoAnterior(costoPrevio);
            dc.setSubtotal(Dinero.redondear(cantidad * dto.getPrecioCosto()));
            dc.setCompra(compra);

            total += dc.getSubtotal();
            detalles.add(dc);
        }

        compra.setTotal(Dinero.redondear(total));
        compra.setDetalles(detalles);
        compraRepository.save(compra);

        // Dentro de la MISMA transaccion que la compra (Fase 11 del mapa de
        // cambios), y solo si esta pagada: una compra a cuenta entra
        // mercaderia sin sacar plata de caja todavia.
        if (pagada && compra.getTotal() > 0) {
            cajaService.registrarMovimiento(
                    TipoMovimiento.EGRESO,
                    CategoriaMovimiento.COMPRA,
                    compra.getTotal(),
                    request.getMetodoPago(),
                    "Compra #" + compra.getId(),
                    null,
                    compra.getId());
        }

        return Mapper.toDTO(compra);
    }

    /**
     * Marca como pagada una compra que habia quedado a cuenta, y recien ahi
     * genera el egreso — con la fecha de HOY, no la de la compra: la plata
     * sale de caja el dia que se paga de verdad, no el dia que entro la
     * mercaderia.
     */
    @Transactional
    public CompraDTO marcarPagada(Long id) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        if (compra.getEstado() == EstadoCompra.ANULADA) {
            throw new RuntimeException("No se puede pagar una compra anulada");
        }
        if (compra.isPagada()) {
            throw new RuntimeException("La compra ya está pagada");
        }

        compra.setPagada(true);
        compraRepository.save(compra);

        cajaService.registrarMovimiento(
                TipoMovimiento.EGRESO,
                CategoriaMovimiento.COMPRA,
                compra.getTotal(),
                compra.getMetodoPago(),
                "Compra #" + compra.getId() + " (pagada después)",
                null,
                compra.getId());

        return Mapper.toDTO(compra);
    }

    public List<CompraDTO> listarCompras() {
        return compraRepository.findAll()
                .stream()
                .map(Mapper::toDTO)
                .toList();
    }

    public CompraDTO buscarPorId(Long id) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        return Mapper.toDTO(compra);
    }

    public CompraDetalleResponseDTO buscarDetallePorId(Long id) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        return Mapper.toCompraDetalleResponse(compra);
    }

    /**
     * Deshace una compra: devuelve el stock que sumo y restaura el precio de
     * costo anterior del producto.
     *
     * <p>Antes no existia. Una compra con un precio mal tipeado dejaba el
     * producto con ese costo y con el precio de venta recalculado sobre el, sin
     * ninguna forma de volver atras salvo editar el producto a mano y adivinar
     * los valores viejos.
     *
     * <p>DF-BUG-005: el precio solo se restaura si sigue siendo el que dejo
     * ESTA compra. Si alguien lo edito a mano despues (o entro otra compra mas
     * nueva del mismo producto), anular ya no puede saber cual es el valor
     * "correcto" al que volver — pisarlo silenciosamente era el bug — asi que
     * lo deja como esta y lo informa en la respuesta.
     */
    @Transactional
    public AnularCompraResponseDTO anularCompra(Long id) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        if (compra.getEstado() == EstadoCompra.ANULADA) {
            throw new RuntimeException("La compra ya está anulada");
        }

        List<DetalleCompra> enOrden = compra.getDetalles().stream()
                .sorted(java.util.Comparator.comparing(d -> d.getProducto().getId()))
                .toList();

        List<String> productosConPrecioConservado = new ArrayList<>();

        for (DetalleCompra detalle : enOrden) {
            Producto producto = productoRepository.buscarParaActualizarStock(detalle.getProducto().getId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            double stockResultante = Cantidades.redondear(producto.getStock() - detalle.getCantidad());
            if (stockResultante < 0) {
                // Ya se vendio parte de lo que entro con esta compra: dejar el
                // stock en negativo seria peor que no anular.
                throw new RuntimeException(
                        "No se puede anular: ya se vendió parte de lo que entró con esta compra ("
                                + producto.getNombre() + ")");
            }
            producto.setStock(stockResultante);

            // Volver al costo anterior y recalcular el precio de venta sobre
            // el: si solo se devolviera el stock, el producto quedaria con el
            // costo mal tipeado, que es la mitad del problema que se queria
            // deshacer. Las compras viejas, anteriores a que se guardara este
            // dato, no tienen con que volver: se les deja el costo actual.
            if (detalle.getPrecioCostoAnterior() != null) {
                // "Sigue siendo el que dejo esta compra": el costo actual es
                // el que esta compra escribio, Y el precio de venta actual es
                // el que resultaria de ese costo con el % de ganancia de HOY
                // (la misma cuenta que hizo registrarCompra). Si cualquiera de
                // los dos difiere, algo paso despues — una edicion manual o
                // otra compra mas nueva — y restaurar seria pisarlo.
                double precioVentaQueDejoEstaCompra = Dinero.precioConGanancia(
                        detalle.getPrecioCosto(), producto.getPorcentajeGanancia());

                boolean costoSinCambios = valoresIguales(producto.getPrecioCosto(), detalle.getPrecioCosto());
                boolean precioVentaSinCambios = valoresIguales(producto.getPrecioVenta(), precioVentaQueDejoEstaCompra);

                if (costoSinCambios && precioVentaSinCambios) {
                    producto.setPrecioCosto(detalle.getPrecioCostoAnterior());
                    producto.setPrecioVenta(Dinero.precioConGanancia(
                            detalle.getPrecioCostoAnterior(), producto.getPorcentajeGanancia()));
                } else {
                    productosConPrecioConservado.add(producto.getNombre());
                }
            }
        }

        compra.setEstado(EstadoCompra.ANULADA);
        compra.setAnuladaPor(usuarioActual());
        compra.setFechaAnulacion(LocalDateTime.now());
        compraRepository.save(compra);

        // Si estaba pagada, revierte el egreso (mismo criterio que anular una
        // venta). Si estaba a cuenta, no hay ningun movimiento que revertir:
        // nunca salio plata de caja por esta compra.
        movimientoCajaRepository
                .findByCompraIdAndCategoriaAndRevierteAIsNull(compra.getId(), CategoriaMovimiento.COMPRA)
                .ifPresent(m -> cajaService.revertirMovimiento(m.getId()));

        return AnularCompraResponseDTO.builder()
                .productosConPrecioConservado(productosConPrecioConservado)
                .build();
    }

    // Comparacion con tolerancia de centavo: los precios pasan por
    // Dinero.redondear en todos los caminos que los escriben, pero comparar
    // doubles con == es fragil (arrastre de precision entre el double en
    // memoria y el DOUBLE de la base). Ver Dinero para el mismo criterio de
    // redondeo.
    private boolean valoresIguales(double a, double b) {
        return Math.abs(a - b) < 0.005;
    }

    // DF-RISK-001: lo que pasaria a costar CADA linea si se confirmara esta
    // compra ahora mismo, SIN persistir nada. Comparte la formula con
    // registrarCompra (Dinero.precioConGanancia) para que el frontend no
    // tenga que recalcularla por su cuenta — mismo patron que
    // VentaService.cotizar.
    @Transactional(readOnly = true)
    public CompraCotizacionDTO previsualizar(List<DetalleCompraRequestDTO> detallesDTO) {
        List<LineaCompraCotizadaDTO> lineas = new ArrayList<>();

        for (DetalleCompraRequestDTO dto : detallesDTO) {
            Producto producto = productoRepository.findById(dto.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            double precioVentaActual = producto.getPrecioVenta();
            double precioVentaNuevo = Dinero.precioConGanancia(dto.getPrecioCosto(), producto.getPorcentajeGanancia());

            boolean cambioSignificativo = precioVentaActual > 0
                    && Math.abs(precioVentaNuevo - precioVentaActual) / precioVentaActual > 0.5;

            lineas.add(LineaCompraCotizadaDTO.builder()
                    .productoId(producto.getId())
                    .productoNombre(producto.getNombre())
                    .precioCostoActual(producto.getPrecioCosto())
                    .precioVentaActual(precioVentaActual)
                    .precioVentaNuevo(precioVentaNuevo)
                    .cambioSignificativo(cambioSignificativo)
                    .build());
        }

        return CompraCotizacionDTO.builder().lineas(lineas).build();
    }

    private String usuarioActual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
