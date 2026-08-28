package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.PromocionDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.Promocion;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.PromocionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PromocionService {

    private final PromocionRepository promocionRepository;
    private final ProductoRepository productoRepository;

    public List<PromocionDTO> listar() {
        return promocionRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public PromocionDTO crear(PromocionDTO dto) {
        Producto producto = productoRepository.findById(dto.getProductoId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        validar(dto, producto, null);

        Promocion promocion = Promocion.builder()
                .producto(producto)
                .cantidadLleva(dto.getCantidadLleva())
                .cantidadPaga(dto.getCantidadPaga())
                .activo(dto.isActivo())
                .build();

        return toDTO(promocionRepository.save(promocion));
    }

    public PromocionDTO actualizar(Long id, PromocionDTO dto) {
        Promocion promocion = promocionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promoción no encontrada"));

        Producto producto = productoRepository.findById(dto.getProductoId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        validar(dto, producto, id);

        promocion.setProducto(producto);
        promocion.setCantidadLleva(dto.getCantidadLleva());
        promocion.setCantidadPaga(dto.getCantidadPaga());
        promocion.setActivo(dto.isActivo());

        return toDTO(promocionRepository.save(promocion));
    }

    public void eliminar(Long id) {
        if (!promocionRepository.existsById(id)) {
            throw new RuntimeException("Promoción no encontrada");
        }
        promocionRepository.deleteById(id);
    }

    public Optional<Promocion> promoActivaPara(Long productoId) {
        return promocionRepository.findByProductoIdAndActivoTrue(productoId);
    }

    private void validar(PromocionDTO dto, Producto producto, Long idActual) {
        if (!"UNIDAD".equals(producto.getTipoCantidad())) {
            throw new RuntimeException("Las promociones solo aplican a productos por unidad");
        }
        if (dto.getCantidadPaga() >= dto.getCantidadLleva()) {
            throw new RuntimeException("La cantidad a pagar debe ser menor a la cantidad que se lleva");
        }
        if (dto.isActivo()) {
            promocionRepository.findByProductoIdAndActivoTrue(dto.getProductoId())
                    .filter(existente -> !existente.getId().equals(idActual))
                    .ifPresent(existente -> {
                        throw new RuntimeException("Ya existe una promoción activa para este producto, desactivala primero");
                    });
        }
    }

    private PromocionDTO toDTO(Promocion promocion) {
        return PromocionDTO.builder()
                .id(promocion.getId())
                .productoId(promocion.getProducto().getId())
                .productoNombre(promocion.getProducto().getNombre())
                .cantidadLleva(promocion.getCantidadLleva())
                .cantidadPaga(promocion.getCantidadPaga())
                .activo(promocion.isActivo())
                .build();
    }
}
