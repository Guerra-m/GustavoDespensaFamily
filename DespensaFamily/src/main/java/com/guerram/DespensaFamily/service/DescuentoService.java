package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.DescuentoDTO;
import com.guerram.DespensaFamily.model.AlcanceDescuento;
import com.guerram.DespensaFamily.model.Categoria;
import com.guerram.DespensaFamily.model.Descuento;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.CategoriaRepository;
import com.guerram.DespensaFamily.repository.DescuentoRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Promos por porcentaje (distintas del combo "lleva x paga y" de
 * Promocion, ver esa clase). Alcanza a un producto puntual o a toda una
 * categoria, y aplica a cualquier tipoCantidad — no solo UNIDAD.
 *
 * <p>Precedencia cuando un producto tiene descuento propio Y su categoria
 * tambien: gana el del producto, por ser mas especifico (ver
 * porcentajeEfectivoPara). No se valida cruce con Promocion: si un producto
 * tuviera las dos cosas activas a la vez, VentaService.calcularSubtotal
 * prioriza el Descuento y listo — es un caso de borde que el dueño no pidio
 * cubrir con una regla propia.
 */
@Service
@RequiredArgsConstructor
public class DescuentoService {

    private final DescuentoRepository descuentoRepository;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    public List<DescuentoDTO> listar() {
        return descuentoRepository.findAll().stream().map(this::toDTO).toList();
    }

    public DescuentoDTO crear(DescuentoDTO dto) {
        Descuento descuento = new Descuento();
        aplicarDatos(descuento, dto);
        return toDTO(descuentoRepository.save(descuento));
    }

    public DescuentoDTO actualizar(Long id, DescuentoDTO dto) {
        Descuento descuento = descuentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Descuento no encontrado"));
        aplicarDatos(descuento, dto);
        return toDTO(descuentoRepository.save(descuento));
    }

    public void eliminar(Long id) {
        if (!descuentoRepository.existsById(id)) {
            throw new RuntimeException("Descuento no encontrado");
        }
        descuentoRepository.deleteById(id);
    }

    /** 0 si no hay descuento activo. Producto puntual gana sobre categoria. */
    public double porcentajeEfectivoPara(Producto producto) {
        var directo = descuentoRepository.findByProductoIdAndActivoTrue(producto.getId());
        if (directo.isPresent()) {
            return directo.get().getPorcentaje();
        }
        if (producto.getCategoria() == null) {
            return 0;
        }
        return descuentoRepository.findByCategoriaIdAndActivoTrue(producto.getCategoria().getId())
                .map(Descuento::getPorcentaje)
                .orElse(0.0);
    }

    private void aplicarDatos(Descuento descuento, DescuentoDTO dto) {
        descuento.setAlcance(dto.getAlcance());
        descuento.setPorcentaje(dto.getPorcentaje());
        descuento.setActivo(dto.isActivo());

        if (dto.getAlcance() == AlcanceDescuento.PRODUCTO) {
            if (dto.getProductoId() == null) {
                throw new RuntimeException("Falta el producto para un descuento por producto");
            }
            Producto producto = productoRepository.findById(dto.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
            descuento.setProducto(producto);
            descuento.setCategoria(null);
        } else {
            if (dto.getCategoriaId() == null) {
                throw new RuntimeException("Falta la categoría para un descuento por categoría");
            }
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
            descuento.setCategoria(categoria);
            descuento.setProducto(null);
        }
    }

    private DescuentoDTO toDTO(Descuento d) {
        return DescuentoDTO.builder()
                .id(d.getId())
                .alcance(d.getAlcance())
                .productoId(d.getProducto() != null ? d.getProducto().getId() : null)
                .productoNombre(d.getProducto() != null ? d.getProducto().getNombre() : null)
                .categoriaId(d.getCategoria() != null ? d.getCategoria().getId() : null)
                .categoriaNombre(d.getCategoria() != null ? d.getCategoria().getNombre() : null)
                .porcentaje(d.getPorcentaje())
                .activo(d.isActivo())
                .build();
    }
}
