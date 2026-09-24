package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.dto.VentaDetalleResponseDTO;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.DetalleVenta;
import com.guerram.DespensaFamily.model.EstadoVenta;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.MovimientoCaja;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import com.guerram.DespensaFamily.model.Venta;
import com.guerram.DespensaFamily.repository.MovimientoCajaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.util.Cantidades;
import com.guerram.DespensaFamily.util.Dinero;
import com.guerram.DespensaFamily.repository.VentaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final PromocionService promocionService;
    private final DescuentoService descuentoService;
    private final CajaService cajaService;
    private final MovimientoCajaRepository movimientoCajaRepository;

    // @Transactional: registrar una venta es leer stock + escribir stock +
    // crear detalles en un solo paso atómico. Sin esto, dos ventas
    // concurrentes del mismo producto podían dejar el stock inconsistente o
    // colisionar en un deadlock de MySQL sin ningún manejo. Combinado con
    // @Version en Producto, un conflicto real ahora tira
    // ObjectOptimisticLockingFailureException en vez de corromper datos — la
    // reintenta el controller (ver OptimisticRetry), no este método.
    @Transactional
    public VentaDTO registrarVenta(List<DetalleVentaRequestDTO> detallesDTO) {
        // Default para callers que no distinguen metodo de pago (tests que no
        // prueban caja). El camino real, VentaController, siempre pasa el
        // metodo que eligio quien cobra — ver el overload de abajo.
        return registrarVenta(detallesDTO, MetodoPago.EFECTIVO);
    }

    // @Transactional tambien aca, y no solo en el metodo de 3 args: la llamada
    // interna de abajo es una auto-invocacion (this.registrarVenta(...)), y el
    // proxy de Spring que hace funcionar @Transactional no intercepta llamadas
    // de un metodo a otro DENTRO del mismo bean. Sin esto, quien entra por
    // este overload corre sin transaccion activa — se detecto porque
    // VentaConCajaTest fallaba con "No active transaction" al llamar
    // exactamente este metodo.
    @Transactional
    public VentaDTO registrarVenta(List<DetalleVentaRequestDTO> detallesDTO, MetodoPago metodoPago) {
        return registrarVenta(detallesDTO, metodoPago, null);
    }

    @Transactional
    public VentaDTO registrarVenta(List<DetalleVentaRequestDTO> detallesDTO, MetodoPago metodoPago, String cliente) {

        Venta venta = new Venta();
        venta.setFechaHora(LocalDateTime.now());
        venta.setEstado(EstadoVenta.ACTIVA);
        venta.setMetodoPago(metodoPago);
        venta.setCliente(cliente != null && !cliente.isBlank() ? cliente.trim() : null);
        venta.setUsuario(usuarioActual());

        double total = 0;
        List<DetalleVenta> detalles = new ArrayList<>();

        // Siempre en el mismo orden de id. Dos ventas que tocan los mismos dos
        // productos en orden distinto se bloquean cruzadas y la base mata una
        // por deadlock; tomando los candados siempre de menor a mayor id, eso
        // no puede pasar.
        List<DetalleVentaRequestDTO> enOrden = detallesDTO.stream()
                .sorted(java.util.Comparator.comparing(DetalleVentaRequestDTO::getProductoId))
                .toList();

        for (DetalleVentaRequestDTO dto : enOrden) {

            Producto producto = productoRepository.buscarParaActualizarStock(dto.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));


            double cantidad = Cantidades.redondear(dto.getCantidad());

            // 🔒 VALIDACIONES
            if (cantidad <= 0) {
                throw new RuntimeException("Cantidad inválida");
            }

            if ("UNIDAD".equals(producto.getTipoCantidad())) {
                if (cantidad % 1 != 0) {
                    throw new RuntimeException("Este producto no permite decimales");
                }
            }

            if (producto.getStock() < cantidad) {
                throw new RuntimeException("Stock insuficiente");
            }


            producto.setStock(producto.getStock() - cantidad);

            DetalleVenta dv = new DetalleVenta();
            dv.setProducto(producto);
            dv.setCantidad(cantidad);
            dv.setPrecioUnitario(producto.getPrecioVenta());
            dv.setPrecioCosto(producto.getPrecioCosto());
            dv.setSubtotal(calcularSubtotal(producto, cantidad));
            dv.setVenta(venta);

            total += dv.getSubtotal();
            detalles.add(dv);
        }

        venta.setTotal(Dinero.redondear(total));
        venta.setDetalles(detalles);

        ventaRepository.save(venta);

        // Dentro de la MISMA transaccion que la venta: si esto falla, la
        // venta tampoco se guarda (ver Fase 10 del mapa de cambios). Un total
        // en cero (venta sin detalles no deberia poder pasar, pero por las
        // dudas) no genera movimiento: no hay plata que haya entrado.
        if (venta.getTotal() > 0) {
            cajaService.registrarMovimiento(
                    TipoMovimiento.INGRESO,
                    CategoriaMovimiento.VENTA,
                    venta.getTotal(),
                    metodoPago,
                    "Venta #" + venta.getId(),
                    venta.getId(),
                    null);
        }

        return Mapper.toDTO(venta);
    }

    // Aplica el combo o el descuento activo del producto (si existe) al
    // precio de la línea. Solo lectura de Promocion/Descuento — no agrega
    // otra escritura concurrente al lado del @Version de Producto ya
    // estabilizado.
    //
    // Un producto con descuento por % Y combo activos a la vez es un caso de
    // borde sin regla propia pedida: gana el descuento por % y se ignora el
    // combo, porque el descuento aplica a cualquier tipoCantidad (el combo
    // solo a UNIDAD) y es el más simple de calcular sobre el total de la
    // línea de forma consistente.
    private double calcularSubtotal(Producto producto, double cantidad) {
        double porcentajeDescuento = descuentoService.porcentajeEfectivoPara(producto);
        if (porcentajeDescuento > 0) {
            double precioConDescuento = producto.getPrecioVenta() * (1 - porcentajeDescuento / 100);
            return Dinero.redondear(precioConDescuento * cantidad);
        }

        if ("UNIDAD".equals(producto.getTipoCantidad())) {
            var promo = promocionService.promoActivaPara(producto.getId());
            if (promo.isPresent()) {
                int lleva = promo.get().getCantidadLleva();
                int paga = promo.get().getCantidadPaga();
                long cant = (long) cantidad;
                long grupos = cant / lleva;
                long resto = cant % lleva;
                long unidadesAPagar = grupos * paga + resto;
                return Dinero.redondear(unidadesAPagar * producto.getPrecioVenta());
            }
        }
        return Dinero.redondear(producto.getPrecioVenta() * cantidad);
    }

    // Devuelve TAMBIEN las anuladas, con su estado, para que el listado las
    // muestre tachadas. Antes filtraba por ACTIVA y una venta anulada
    // desaparecia del historial: eso es justo lo que hacia servible el fraude
    // de cobrar y anular, porque no quedaba ni el renglon.
    public List<VentaDTO> listarVentas() {
        return ventaRepository.findAll()
                .stream()
                .map(Mapper::toDTO)
                .toList();
    }

    public VentaDTO buscarPorId(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        return Mapper.toDTO(venta);
    }

    public VentaDetalleResponseDTO buscarDetallePorId(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        return Mapper.toVentaDetalleResponse(venta);
    }

    @Transactional
    public void anularVenta(Long id) {

        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (venta.getEstado() == EstadoVenta.ANULADA) {
            throw new RuntimeException("La venta ya está anulada");
        }


        for (DetalleVenta detalle : venta.getDetalles()) {
            Producto producto = detalle.getProducto();
            producto.setStock(
                    producto.getStock() + detalle.getCantidad()
            );
        }

        venta.setEstado(EstadoVenta.ANULADA);
        venta.setAnuladaPor(usuarioActual());
        venta.setFechaAnulacion(LocalDateTime.now());
        ventaRepository.save(venta);

        // Revierte el ingreso que genero esta venta, si genero alguno (ventas
        // de $0 no crean movimiento, ver registrarVenta). Se busca por
        // ventaId + categoria=VENTA + revierteA nulo para encontrar el
        // ORIGINAL y no una reversion previa — una venta anulada dos veces ya
        // fallaria antes por el chequeo de estado de arriba, pero esta
        // busqueda es la que asegura no revertir el movimiento equivocado.
        movimientoCajaRepository
                .findByVentaIdAndCategoriaAndRevierteAIsNull(venta.getId(), CategoriaMovimiento.VENTA)
                .ifPresent(m -> cajaService.revertirMovimiento(m.getId()));
    }

    /**
     * El usuario del token. Nunca llega por parametro desde el cliente: eso
     * dejaria que cualquiera registre una venta a nombre de otro.
     */
    private String usuarioActual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
