package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import com.guerram.DespensaFamily.dto.ConfiguracionDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

// @Transactional acá (a diferencia de VentaConcurrenciaTest, que lo evita a
// propósito porque necesita commits reales entre hilos): cada test corre en
// su propia transacción que se deshace al final, así ningún test deja la
// fila única de Configuracion o productos de prueba contaminando al
// siguiente — importante porque JUnit no garantiza el orden de los métodos.
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@Transactional
class ConfiguracionServiceTest {

    @Autowired
    private ConfiguracionService configuracionService;

    @Autowired
    private ProductoRepository productoRepository;

    private Producto crearProducto(String nombre, double precioCosto, boolean activo) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre)
                .precioCosto(BigDecimal.valueOf(precioCosto))
                .precioVenta(BigDecimal.valueOf(precioCosto)) // arranca sin ganancia aplicada
                .porcentajeGanancia(BigDecimal.valueOf(0))
                .stock(BigDecimal.valueOf(5))
                .tipoCantidad("UNIDAD")
                .unidadMedida("u")
                .fraccionMinima(BigDecimal.valueOf(1.0))
                .activo(activo)
                .build());
    }

    private static void assertBigDecimalEquals(double esperado, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(esperado).compareTo(actual),
                () -> "esperaba " + esperado + " pero fue " + actual);
    }

    @Test
    void obtenerCreaConfiguracionPorDefectoLaPrimeraVez() {
        ConfiguracionDTO dto = configuracionService.obtener();

        assertBigDecimalEquals(10.0, dto.getUmbralStockBajo());
        assertEquals("Despensa Family", dto.getNombreNegocio());
        assertBigDecimalEquals(30.0, dto.getGananciaPorDefecto());
    }

    @Test
    void actualizarCambiaSoloLosCamposEnviados() {
        configuracionService.actualizar(new ConfiguracionDTO(BigDecimal.valueOf(15.0), null, null));
        ConfiguracionDTO despues = configuracionService.obtener();

        assertBigDecimalEquals(15.0, despues.getUmbralStockBajo());
        assertEquals("Despensa Family", despues.getNombreNegocio()); // no se tocó
    }

    @Test
    void actualizarRechazaUmbralNegativo() {
        assertThrows(RuntimeException.class,
                () -> configuracionService.actualizar(new ConfiguracionDTO(BigDecimal.valueOf(-1.0), null, null)));
    }

    @Test
    void actualizarRechazaNombreVacio() {
        assertThrows(RuntimeException.class,
                () -> configuracionService.actualizar(new ConfiguracionDTO(null, "   ", null)));
    }

    @Test
    void aplicarGananciaRecalculaElPrecioDeVentaDeTodosLosProductosActivos() {
        Producto p1 = crearProducto("Producto A", 100.0, true);
        Producto p2 = crearProducto("Producto B", 50.0, true);

        int actualizados = configuracionService.aplicarGananciaATodos(BigDecimal.valueOf(20.0));

        Producto p1Actualizado = productoRepository.findById(p1.getId()).orElseThrow();
        Producto p2Actualizado = productoRepository.findById(p2.getId()).orElseThrow();

        assertTrue(actualizados >= 2);
        assertBigDecimalEquals(20.0, p1Actualizado.getPorcentajeGanancia());
        assertBigDecimalEquals(120.0, p1Actualizado.getPrecioVenta());
        assertBigDecimalEquals(20.0, p2Actualizado.getPorcentajeGanancia());
        assertBigDecimalEquals(60.0, p2Actualizado.getPrecioVenta());
    }

    @Test
    void aplicarGananciaNoTocaProductosInactivos() {
        Producto inactivo = crearProducto("Producto Borrado", 100.0, false);

        configuracionService.aplicarGananciaATodos(BigDecimal.valueOf(50.0));

        Producto sigueIgual = productoRepository.findById(inactivo.getId()).orElseThrow();
        assertBigDecimalEquals(0.0, sigueIgual.getPorcentajeGanancia());
        assertBigDecimalEquals(100.0, sigueIgual.getPrecioVenta());
    }

    @Test
    void aplicarGananciaEsIdempotente() {
        Producto p = crearProducto("Producto Idempotente", 200.0, true);

        configuracionService.aplicarGananciaATodos(BigDecimal.valueOf(10.0));
        configuracionService.aplicarGananciaATodos(BigDecimal.valueOf(10.0)); // reintento simulado

        Producto resultado = productoRepository.findById(p.getId()).orElseThrow();
        assertBigDecimalEquals(220.0, resultado.getPrecioVenta());
    }

    @Test
    void aplicarGananciaRechazaPorcentajeNegativo() {
        assertThrows(RuntimeException.class,
                () -> configuracionService.aplicarGananciaATodos(BigDecimal.valueOf(-5.0)));
    }

    @Test
    void aplicarGananciaActualizaLaGananciaPorDefectoGuardada() {
        configuracionService.aplicarGananciaATodos(BigDecimal.valueOf(25.0));
        assertBigDecimalEquals(25.0, configuracionService.obtener().getGananciaPorDefecto());
    }
}
