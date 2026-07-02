package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.*;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.Compra;
import com.guerram.DespensaFamily.model.DetalleCompra;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.CompraRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProductoRepository productoRepository;

    public CompraDTO registrarCompra(CompraRequestDTO request) {

        Compra compra = new Compra();
        compra.setFechaHora(LocalDate.now());
        compra.setProveedor(request.getProveedor());

        List<DetalleCompra> detalles = new ArrayList<>();

        for (DetalleCompraRequestDTO dto : request.getDetalles()) {

            Producto producto = productoRepository.findById(dto.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            // REDONDEO
            double cantidad = Math.round(dto.getCantidad() * 1000.0) / 1000.0;

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
            producto.setPrecioCosto(dto.getPrecioCosto());

            double porcentaje = producto.getPorcentajeGanancia();
            double nuevoPrecioVenta =
                    dto.getPrecioCosto() + (dto.getPrecioCosto() * porcentaje / 100);

            producto.setPrecioVenta(nuevoPrecioVenta);

            // DETALLE
            DetalleCompra dc = new DetalleCompra();
            dc.setProducto(producto);
            dc.setCantidad(cantidad);
            dc.setPrecioCosto(dto.getPrecioCosto());
            dc.setSubtotal(cantidad * dto.getPrecioCosto());
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
}