package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.dto.VentaDetalleResponseDTO;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.DetalleVenta;
import com.guerram.DespensaFamily.model.EstadoVenta;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.Venta;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.util.Cantidades;
import com.guerram.DespensaFamily.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final PromocionService promocionService;

    // @Transactional: registrar una venta es leer stock + escribir stock +
    // crear detalles en un solo paso atómico. Sin esto, dos ventas
    // concurrentes del mismo producto podían dejar el stock inconsistente o
    // colisionar en un deadlock de MySQL sin ningún manejo. Combinado con
    // @Version en Producto, un conflicto real ahora tira
    // ObjectOptimisticLockingFailureException en vez de corromper datos — la
    // reintenta el controller (ver OptimisticRetry), no este método.
    @Transactional
    public VentaDTO registrarVenta(List<DetalleVentaRequestDTO> detallesDTO) {

        Venta venta = new Venta();
        venta.setFechaHora(LocalDate.now());
        venta.setEstado(EstadoVenta.ACTIVA);

        double total = 0;
        List<DetalleVenta> detalles = new ArrayList<>();

        for (DetalleVentaRequestDTO dto : detallesDTO) {

            Producto producto = productoRepository.findById(dto.getProductoId())
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

        venta.setTotal(total);
        venta.setDetalles(detalles);

        ventaRepository.save(venta);

        return Mapper.toDTO(venta);
    }

    // Aplica el combo activo del producto (si existe) al precio de la línea.
    // Solo lectura de Promocion — no agrega otra escritura concurrente al lado
    // del @Version de Producto ya estabilizado.
    private double calcularSubtotal(Producto producto, double cantidad) {
        if ("UNIDAD".equals(producto.getTipoCantidad())) {
            var promo = promocionService.promoActivaPara(producto.getId());
            if (promo.isPresent()) {
                int lleva = promo.get().getCantidadLleva();
                int paga = promo.get().getCantidadPaga();
                long cant = (long) cantidad;
                long grupos = cant / lleva;
                long resto = cant % lleva;
                long unidadesAPagar = grupos * paga + resto;
                return unidadesAPagar * producto.getPrecioVenta();
            }
        }
        return producto.getPrecioVenta() * cantidad;
    }

    public List<VentaDTO> listarVentas() {
        return ventaRepository.findByEstado(EstadoVenta.ACTIVA)
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
        ventaRepository.save(venta);
    }
}