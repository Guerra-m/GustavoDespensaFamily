package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.NoEncontradoException;
import com.guerram.DespensaFamily.config.ReglaDeNegocioException;
import com.guerram.DespensaFamily.dto.CotizacionDTO;
import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.LineaCotizadaDTO;
import com.guerram.DespensaFamily.dto.PaginaDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        return registrarVenta(detallesDTO, metodoPago, cliente, null);
    }

    // DF-BUG-003: variante con Idempotency-Key. Si ya existe una venta con
    // esta clave, la devuelve tal cual en vez de crear otra — cubre tanto el
    // reintento secuencial (doble clic donde el segundo llega despues de que
    // el primero ya hizo commit) como, en combinacion con la unique
    // constraint de la base (V12) y el catch en VentaController, el caso
    // concurrente donde las dos peticiones pasan este chequeo casi juntas.
    @Transactional
    public VentaDTO registrarVenta(List<DetalleVentaRequestDTO> detallesDTO, MetodoPago metodoPago, String cliente,
            String claveIdempotencia) {

        String clave = normalizarClave(claveIdempotencia);
        if (clave != null) {
            Optional<Venta> existente = ventaRepository.findByClaveIdempotencia(clave);
            if (existente.isPresent()) {
                return Mapper.toDTO(existente.get());
            }
        }

        Venta venta = new Venta();
        venta.setFechaHora(LocalDateTime.now());
        venta.setEstado(EstadoVenta.ACTIVA);
        venta.setMetodoPago(metodoPago);
        venta.setCliente(cliente != null && !cliente.isBlank() ? cliente.trim() : null);
        venta.setUsuario(usuarioActual());
        venta.setClaveIdempotencia(clave);

        BigDecimal total = BigDecimal.ZERO;
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
                    .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));

            LineaCalculada calc = calcularLinea(producto, dto.getCantidad());

            if (calc.stockInsuficiente()) {
                throw new ReglaDeNegocioException("Stock insuficiente");
            }

            producto.setStock(producto.getStock().subtract(calc.cantidad()));

            DetalleVenta dv = new DetalleVenta();
            dv.setProducto(producto);
            dv.setCantidad(calc.cantidad());
            dv.setPrecioUnitario(calc.precioUnitario());
            dv.setPrecioCosto(producto.getPrecioCosto());
            dv.setSubtotal(calc.subtotal());
            dv.setDescuentoPorcentaje(calc.descuentoPorcentaje());
            dv.setPromoDescripcion(calc.promoDescripcion());
            dv.setVenta(venta);

            total = total.add(dv.getSubtotal());
            detalles.add(dv);
        }

        venta.setTotal(Dinero.redondear(total));
        venta.setDetalles(detalles);

        ventaRepository.save(venta);

        // Dentro de la MISMA transaccion que la venta: si esto falla, la
        // venta tampoco se guarda (ver Fase 10 del mapa de cambios). Un total
        // en cero (venta sin detalles no deberia poder pasar, pero por las
        // dudas) no genera movimiento: no hay plata que haya entrado.
        if (venta.getTotal().compareTo(BigDecimal.ZERO) > 0) {
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

    // DF-BUG-001: el resultado de calcular UNA linea, sin persistir nada.
    // Unico lugar donde se hace esta cuenta — lo usan registrarVenta (que
    // ademas descuenta stock y guarda) y cotizar (que solo lee). Que las dos
    // pasen por aca es lo que garantiza que el total que ve el cliente en el
    // carrito sea el mismo que despues se cobra: antes el frontend
    // recalculaba esto por su cuenta en ventas.ts, aplicando solo promos
    // (nunca descuentos) y sin redondear cantidades pesables.
    private record LineaCalculada(
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal,
            BigDecimal descuentoPorcentaje,
            String promoDescripcion,
            boolean stockInsuficiente) {
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
    //
    // No decrementa stock ni lo valida mas alla de marcarlo insuficiente: es
    // quien llama (registrarVenta) el que decide si eso rechaza la linea o
    // no (cotizar no rechaza, ver clase LineaCotizadaDTO).
    private LineaCalculada calcularLinea(Producto producto, BigDecimal cantidadSolicitada) {
        BigDecimal cantidad = Cantidades.redondear(cantidadSolicitada);

        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaDeNegocioException("Cantidad inválida");
        }

        // DF-DATA-001: antes solo se rechazaba el decimal cuando
        // tipoCantidad == "UNIDAD" a secas — un producto con tipoCantidad
        // nulo (7 de 14 productos reales, ya no deberia pasar mas gracias a
        // V13, pero esto queda como segunda barrera) se colaba y se vendia
        // en fracciones (0,5 chupetin). La regla real del negocio es al
        // reves: solo lo que se vende por PESO admite decimales: todo lo
        // demas se rechaza.
        boolean tieneDecimales = cantidad.stripTrailingZeros().scale() > 0;
        if (!"PESO".equals(producto.getTipoCantidad()) && tieneDecimales) {
            throw new ReglaDeNegocioException("Este producto no permite decimales");
        }

        boolean stockInsuficiente = producto.getStock().compareTo(cantidad) < 0;

        BigDecimal porcentajeDescuento = descuentoService.porcentajeEfectivoPara(producto);
        if (porcentajeDescuento.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal factor = BigDecimal.ONE.subtract(
                    porcentajeDescuento.divide(BigDecimal.valueOf(100), 10, java.math.RoundingMode.HALF_UP));
            BigDecimal precioConDescuento = producto.getPrecioVenta().multiply(factor);
            BigDecimal subtotal = Dinero.redondear(precioConDescuento.multiply(cantidad));
            return new LineaCalculada(cantidad, producto.getPrecioVenta(), subtotal, porcentajeDescuento, null,
                    stockInsuficiente);
        }

        if ("UNIDAD".equals(producto.getTipoCantidad())) {
            var promo = promocionService.promoActivaPara(producto.getId());
            if (promo.isPresent()) {
                long lleva = promo.get().getCantidadLleva();
                long paga = promo.get().getCantidadPaga();
                long cant = cantidad.longValueExact();
                long grupos = cant / lleva;
                long resto = cant % lleva;
                long unidadesAPagar = grupos * paga + resto;
                BigDecimal subtotal = Dinero.redondear(
                        BigDecimal.valueOf(unidadesAPagar).multiply(producto.getPrecioVenta()));
                return new LineaCalculada(cantidad, producto.getPrecioVenta(), subtotal, null,
                        lleva + "x" + paga, stockInsuficiente);
            }
        }

        BigDecimal subtotal = Dinero.redondear(producto.getPrecioVenta().multiply(cantidad));
        return new LineaCalculada(cantidad, producto.getPrecioVenta(), subtotal, null, null, stockInsuficiente);
    }

    // DF-BUG-001: lo que cobraria registrarVenta con este carrito, SIN
    // persistir nada ni tocar stock/caja. No usa buscarParaActualizarStock
    // (bloqueo pesimista) a proposito: cotizar se llama en cada tecla del
    // carrito (debounce de por medio en el frontend) y no tiene sentido que
    // compita por el mismo lock que usan las ventas reales — es una lectura,
    // no una reserva.
    @Transactional(readOnly = true)
    public CotizacionDTO cotizar(List<DetalleVentaRequestDTO> detallesDTO) {
        List<LineaCotizadaDTO> lineas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (DetalleVentaRequestDTO dto : detallesDTO) {
            Producto producto = productoRepository.findById(dto.getProductoId())
                    .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));

            LineaCalculada calc = calcularLinea(producto, dto.getCantidad());

            lineas.add(LineaCotizadaDTO.builder()
                    .productoId(producto.getId())
                    .cantidad(calc.cantidad())
                    .precioUnitario(calc.precioUnitario())
                    .subtotal(calc.subtotal())
                    .descuentoPorcentaje(calc.descuentoPorcentaje())
                    .promoDescripcion(calc.promoDescripcion())
                    .stockInsuficiente(calc.stockInsuficiente())
                    .build());

            total = total.add(calc.subtotal());
        }

        return CotizacionDTO.builder()
                .lineas(lineas)
                .total(Dinero.redondear(total))
                .build();
    }

    private String normalizarClave(String claveIdempotencia) {
        return (claveIdempotencia != null && !claveIdempotencia.isBlank()) ? claveIdempotencia.trim() : null;
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

    // DF-PERF-001: /ventas sin paginar mandaba 562 KB con 3.055 ventas
    // (~13 MB/ano). Este metodo es NUEVO -- no reemplaza listarVentas(), que
    // sigue devolviendo todo sin filtros para quien ya depende de eso (ver
    // VentaController, home.ts es el unico consumidor hoy).
    @Transactional(readOnly = true)
    public PaginaDTO<VentaDTO> listarVentasPaginado(LocalDateTime desde, LocalDateTime hasta, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venta> resultado = ventaRepository.buscarPaginado(desde, hasta, pageable);

        return PaginaDTO.<VentaDTO>builder()
                .contenido(resultado.getContent().stream().map(Mapper::toDTO).toList())
                .totalElementos(resultado.getTotalElements())
                .totalPaginas(resultado.getTotalPages())
                .pagina(page)
                .tamanio(size)
                .build();
    }

    /** DF-BUG-003: usado por el controller para resolver un choque de clave concurrente. */
    @Transactional(readOnly = true)
    public Optional<VentaDTO> buscarPorClaveIdempotencia(String claveIdempotencia) {
        return ventaRepository.findByClaveIdempotencia(claveIdempotencia).map(Mapper::toDTO);
    }

    public VentaDTO buscarPorId(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));

        return Mapper.toDTO(venta);
    }

    @Transactional(readOnly = true)
    public VentaDetalleResponseDTO buscarDetallePorId(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));

        return Mapper.toVentaDetalleResponse(venta);
    }

    @Transactional
    public void anularVenta(Long id) {

        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));

        if (venta.getEstado() == EstadoVenta.ANULADA) {
            throw new ReglaDeNegocioException("La venta ya está anulada");
        }

        // DF-DEBT-003: antes esto tocaba detalle.getProducto() directo, la
        // entidad que ya trajo el findById de arriba (SIN lock). Los demas
        // caminos que suman/restan stock (registrarVenta, CompraService)
        // pasan todos por buscarParaActualizarStock — el SELECT ... FOR
        // UPDATE que evita el lost update de una venta/compra concurrente
        // sobre el mismo producto. anularVenta se habia quedado afuera de
        // esa proteccion: dos anulaciones (o una anulacion y una venta) del
        // mismo producto al mismo tiempo podian perder uno de los dos
        // ajustes de stock. Mismo orden por id que usa CompraService.
        // anularCompra, por la misma razon (dos anulaciones que tocan los
        // mismos productos en orden distinto se bloquean cruzadas).
        List<DetalleVenta> detallesEnOrden = venta.getDetalles().stream()
                .sorted(java.util.Comparator.comparing(d -> d.getProducto().getId()))
                .toList();

        for (DetalleVenta detalle : detallesEnOrden) {
            Producto producto = productoRepository.buscarParaActualizarStock(detalle.getProducto().getId())
                    .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));
            producto.setStock(
                    producto.getStock().add(detalle.getCantidad())
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
