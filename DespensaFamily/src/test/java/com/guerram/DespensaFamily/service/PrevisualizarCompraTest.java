package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.CompraCotizacionDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraRequestDTO;
import com.guerram.DespensaFamily.dto.LineaCompraCotizadaDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DF-RISK-001: antes de confirmar una compra, el frontend tiene que poder
 * mostrar "precio actual -> precio nuevo" por linea SIN registrar nada, y
 * con la MISMA formula que usa CompraService.registrarCompra (Dinero.
 * precioConGanancia) — no una segunda cuenta duplicada en el frontend.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class PrevisualizarCompraTest {

    @Autowired
    private CompraService compraService;

    @Autowired
    private ProductoRepository productoRepository;

    private Producto productoConCosto(double costo, double porcentajeGanancia) {
        return productoRepository.save(Producto.builder()
                .nombre("Fideos de prueba")
                .precioCosto(costo)
                .precioVenta(costo + costo * porcentajeGanancia / 100)
                .porcentajeGanancia(porcentajeGanancia)
                .stock(10).tipoCantidad("UNIDAD").unidadMedida("u")
                .fraccionMinima(1.0).activo(true)
                .build());
    }

    @Test
    void previsualizarNoTocaNadaYMuestraElPrecioActualYElNuevo() {
        // costo 500 -> 800, 30% de ganancia: precio actual 650, nuevo 1040
        // (evidencia de la auditoria: DF-RISK-001).
        Producto p = productoConCosto(500, 30);

        CompraCotizacionDTO cotizacion = compraService.previsualizar(
                List.of(new DetalleCompraRequestDTO(p.getId(), 10.0, 800.0)));

        assertThat(cotizacion.getLineas()).hasSize(1);
        LineaCompraCotizadaDTO linea = cotizacion.getLineas().get(0);
        assertThat(linea.getPrecioVentaActual()).isEqualTo(650.0);
        assertThat(linea.getPrecioVentaNuevo()).isEqualTo(1040.0);
        assertThat(linea.isCambioSignificativo())
                .as("650 -> 1040 es +60%, mas del 50% que la auditoria pide resaltar")
                .isTrue();

        // No registro nada: ni costo ni precio de venta cambiaron en la base.
        Producto sinTocar = productoRepository.findById(p.getId()).orElseThrow();
        assertThat(sinTocar.getPrecioCosto()).isEqualTo(500.0);
        assertThat(sinTocar.getPrecioVenta()).isEqualTo(650.0);
    }

    @Test
    void unCambioChicoNoSeMarcaComoSignificativo() {
        // costo 500 -> 520, 30% de ganancia: 650 -> 676, +4%.
        Producto p = productoConCosto(500, 30);

        CompraCotizacionDTO cotizacion = compraService.previsualizar(
                List.of(new DetalleCompraRequestDTO(p.getId(), 1.0, 520.0)));

        LineaCompraCotizadaDTO linea = cotizacion.getLineas().get(0);
        assertThat(linea.getPrecioVentaNuevo()).isEqualTo(676.0);
        assertThat(linea.isCambioSignificativo()).isFalse();
    }
}
