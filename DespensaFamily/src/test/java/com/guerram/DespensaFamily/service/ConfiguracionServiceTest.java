package com.guerram.DespensaFamily.service;

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
                .precioCosto(precioCosto)
                .precioVenta(precioCosto) // arranca sin ganancia aplicada
                .porcentajeGanancia(0)
                .stock(5)
                .tipoCantidad("UNIDAD")
                .unidadMedida("u")
                .fraccionMinima(1.0)
                .activo(activo)
                .build());
    }

    @Test
    void obtenerCreaConfiguracionPorDefectoLaPrimeraVez() {
        ConfiguracionDTO dto = configuracionService.obtener();

        assertEquals(10.0, dto.getUmbralStockBajo());
        assertEquals("Despensa Family", dto.getNombreNegocio());
        assertEquals(30.0, dto.getGananciaPorDefecto());
    }

    @Test
    void actualizarCambiaSoloLosCamposEnviados() {
        configuracionService.actualizar(new ConfiguracionDTO(15.0, null, null));
        ConfiguracionDTO despues = configuracionService.obtener();

        assertEquals(15.0, despues.getUmbralStockBajo());
        assertEquals("Despensa Family", despues.getNombreNegocio()); // no se tocó
    }

    @Test
    void actualizarRechazaUmbralNegativo() {
        assertThrows(RuntimeException.class,
                () -> configuracionService.actualizar(new ConfiguracionDTO(-1.0, null, null)));
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

        int actualizados = configuracionService.aplicarGananciaATodos(20.0);

        Producto p1Actualizado = productoRepository.findById(p1.getId()).orElseThrow();
        Producto p2Actualizado = productoRepository.findById(p2.getId()).orElseThrow();

        assertTrue(actualizados >= 2);
        assertEquals(20.0, p1Actualizado.getPorcentajeGanancia());
        assertEquals(120.0, p1Actualizado.getPrecioVenta());
        assertEquals(20.0, p2Actualizado.getPorcentajeGanancia());
        assertEquals(60.0, p2Actualizado.getPrecioVenta());
    }

    @Test
    void aplicarGananciaNoTocaProductosInactivos() {
        Producto inactivo = crearProducto("Producto Borrado", 100.0, false);

        configuracionService.aplicarGananciaATodos(50.0);

        Producto sigueIgual = productoRepository.findById(inactivo.getId()).orElseThrow();
        assertEquals(0.0, sigueIgual.getPorcentajeGanancia());
        assertEquals(100.0, sigueIgual.getPrecioVenta());
    }

    @Test
    void aplicarGananciaEsIdempotente() {
        Producto p = crearProducto("Producto Idempotente", 200.0, true);

        configuracionService.aplicarGananciaATodos(10.0);
        configuracionService.aplicarGananciaATodos(10.0); // reintento simulado

        Producto resultado = productoRepository.findById(p.getId()).orElseThrow();
        assertEquals(220.0, resultado.getPrecioVenta());
    }

    @Test
    void aplicarGananciaRechazaPorcentajeNegativo() {
        assertThrows(RuntimeException.class, () -> configuracionService.aplicarGananciaATodos(-5.0));
    }

    @Test
    void aplicarGananciaActualizaLaGananciaPorDefectoGuardada() {
        configuracionService.aplicarGananciaATodos(25.0);
        assertEquals(25.0, configuracionService.obtener().getGananciaPorDefecto());
    }
}
