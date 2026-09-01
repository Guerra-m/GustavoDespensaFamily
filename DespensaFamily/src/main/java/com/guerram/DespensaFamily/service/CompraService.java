package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.*;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.Compra;
import com.guerram.DespensaFamily.model.EstadoCompra;
import com.guerram.DespensaFamily.model.DetalleCompra;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.CompraRepository;
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

    // Ver comentario equivalente en VentaService.registrarVenta: mismo
    // riesgo de concurrencia sobre producto.stock, misma solución.
    @Transactional
    public CompraDTO registrarCompra(CompraRequestDTO request) {

        Compra compra = new Compra();
        compra.setFechaHora(LocalDateTime.now());
        compra.setProveedor(request.getProveedor());
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        compra.setUsuario(auth != null ? auth.getName() : null);

        List<DetalleCompra> detalles = new ArrayList<>();

        // Mismo orden de bloqueo que las ventas (ver VentaService): si compras y
        // ventas tomaran los candados en orden distinto, se trabarian entre si.
        List<DetalleCompraRequestDTO> enOrden = request.getDetalles().stream()
                .sorted(java.util.Comparator.comparing(DetalleCompraRequestDTO::getProductoId))
                .toList();

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

            detalles.add(dc);
        }

        compra.setDetalles(detalles);
        compraRepository.save(compra);

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
    }

    private String usuarioActual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
