package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.ConfiguracionDTO;
import com.guerram.DespensaFamily.model.Configuracion;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ConfiguracionRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.util.Dinero;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    // Fila única: no hay múltiples configuraciones, así que no hace falta
    // buscarla por ningún criterio más que este id fijo.
    private static final Long ID_UNICO = 1L;

    private final ConfiguracionRepository configuracionRepository;
    private final ProductoRepository productoRepository;

    public ConfiguracionDTO obtener() {
        return toDTO(obtenerOCrear());
    }

    private Configuracion obtenerOCrear() {
        return configuracionRepository.findById(ID_UNICO).orElseGet(this::crearPorDefecto);
    }

    private Configuracion crearPorDefecto() {
        Configuracion c = Configuracion.builder()
                .id(ID_UNICO)
                .umbralStockBajo(BigDecimal.valueOf(10.0))
                .nombreNegocio("Despensa Family")
                .gananciaPorDefecto(BigDecimal.valueOf(30.0))
                .build();
        return configuracionRepository.save(c);
    }

    @Transactional
    public ConfiguracionDTO actualizar(ConfiguracionDTO dto) {
        Configuracion c = obtenerOCrear();

        if (dto.getUmbralStockBajo() != null) {
            if (dto.getUmbralStockBajo().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("El umbral de stock bajo no puede ser negativo");
            }
            c.setUmbralStockBajo(dto.getUmbralStockBajo());
        }

        if (dto.getNombreNegocio() != null) {
            if (dto.getNombreNegocio().isBlank()) {
                throw new RuntimeException("El nombre del negocio no puede estar vacío");
            }
            c.setNombreNegocio(dto.getNombreNegocio().trim());
        }

        return toDTO(configuracionRepository.save(c));
    }

    // Acción de alto impacto: toca el precio de venta de TODOS los productos
    // activos de una sola vez. Es idempotente a propósito (recalcula desde
    // precioCosto, no incrementa nada), así que si el controller la reintenta
    // por un conflicto de concurrencia (ver OptimisticRetry), repetirla entera
    // no duplica ni corrompe nada — el resultado final es el mismo sin
    // importar cuántas veces se ejecute con el mismo porcentaje.
    @Transactional
    public int aplicarGananciaATodos(BigDecimal porcentaje) {
        if (porcentaje == null || porcentaje.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("El porcentaje de ganancia no puede ser negativo");
        }

        List<Producto> productos = productoRepository.findByActivoTrue();
        for (Producto producto : productos) {
            producto.setPorcentajeGanancia(porcentaje);
            producto.setPrecioVenta(Dinero.precioConGanancia(producto.getPrecioCosto(), porcentaje));
        }
        productoRepository.saveAll(productos);

        Configuracion c = obtenerOCrear();
        c.setGananciaPorDefecto(porcentaje);
        configuracionRepository.save(c);

        return productos.size();
    }

    private ConfiguracionDTO toDTO(Configuracion c) {
        return new ConfiguracionDTO(c.getUmbralStockBajo(), c.getNombreNegocio(), c.getGananciaPorDefecto());
    }
}
