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

        // 1. Crear la compra
        Compra compra = new Compra();
        compra.setFechaHora(LocalDate.now());
        compra.setProveedor(request.getProveedor());

        List<DetalleCompra> detalles = new ArrayList<>();

        // 2. Recorrer los detalles del request
        for (DetalleCompraRequestDTO dto : request.getDetalles()) {

            Producto producto = productoRepository.findById(dto.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            // 3. Sumar stock
            producto.setStock(producto.getStock() + dto.getCantidad());
            //4. actualizar precio de costo
            producto.setPrecioCosto(dto.getPrecioCosto());
            //5. Recalcular precio de venta manteniendo porcentaje
            double porcentaje = producto.getPorcentajeGanancia();
            double nuevoPrecioVenta =
                    dto.getPrecioCosto() + (dto.getPrecioCosto() * porcentaje / 100);

            producto.setPrecioVenta(nuevoPrecioVenta);
            // 6. Crear detalle de compra
            DetalleCompra dc = new DetalleCompra();
            dc.setProducto(producto);
            dc.setCantidad(dto.getCantidad());
            dc.setPrecioCosto(dto.getPrecioCosto());
            dc.setSubtotal(dto.getCantidad() * dto.getPrecioCosto());
            dc.setCompra(compra);

            detalles.add(dc);
        }

        // 5. Asociar detalles y guardar
        compra.setDetalles(detalles);
        compraRepository.save(compra);

        // 6. Devolver DTO
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
