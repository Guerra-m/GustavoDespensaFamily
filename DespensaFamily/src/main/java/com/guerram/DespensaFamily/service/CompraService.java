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
     */
    @Transactional
    public void anularCompra(Long id) {
        Compra compra = compraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        if (compra.getEstado() == EstadoCompra.ANULADA) {
            throw new RuntimeException("La compra ya está anulada");
        }

        List<DetalleCompra> enOrden = compra.getDetalles().stream()
                .sorted(java.util.Comparator.comparing(d -> d.getProducto().getId()))
                .toList();

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
                producto.setPrecioCosto(detalle.getPrecioCostoAnterior());
                producto.setPrecioVenta(Dinero.precioConGanancia(
                        detalle.getPrecioCostoAnterior(), producto.getPorcentajeGanancia()));
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
    }

    private String usuarioActual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
