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
import com.guerram.DespensaFamily.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;

    public VentaDTO registrarVenta(List<DetalleVentaRequestDTO> detallesDTO) {

        Venta venta = new Venta();
        venta.setFechaHora(LocalDate.now());
        venta.setEstado(EstadoVenta.ACTIVA);

        double total = 0;
        List<DetalleVenta> detalles = new ArrayList<>();

        for (DetalleVentaRequestDTO dto : detallesDTO) {

            Producto producto = productoRepository.findById(dto.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));


            double cantidad = Math.round(dto.getCantidad() * 1000.0) / 1000.0;

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
            dv.setSubtotal(producto.getPrecioVenta() * cantidad);
            dv.setVenta(venta);

            total += dv.getSubtotal();
            detalles.add(dv);
        }

        venta.setTotal(total);
        venta.setDetalles(detalles);

        ventaRepository.save(venta);

        return Mapper.toDTO(venta);
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