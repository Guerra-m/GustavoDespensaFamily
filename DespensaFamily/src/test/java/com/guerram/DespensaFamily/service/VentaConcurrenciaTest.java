package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.util.OptimisticRetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Prueba el escenario del roadmap "concurrencia multi-dispositivo": dos o mas
// sesiones (PC + celular) vendiendo el mismo producto al mismo tiempo. La
// proteccion ya existe (@Version en Producto + OptimisticRetry en el
// controller, ver comentarios ahi) — este test no la implementa, la
// demuestra: si el mecanismo fallara, o bien alguna venta tiraria error sin
// reintentar, o el stock final quedaria mal (una escritura pisando a otra,
// el clasico "lost update").
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class VentaConcurrenciaTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    void ventasSimultaneasSobreElMismoProductoNoPierdenStock() throws InterruptedException, ExecutionException {
        final int hilos = 20;
        final double stockInicial = 100;

        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto de prueba de concurrencia")
                .precioCosto(5)
                .precioVenta(10)
                .porcentajeGanancia(30)
                .stock(stockInicial)
                .tipoCantidad("UNIDAD")
                .unidadMedida("u")
                .fraccionMinima(1.0)
                .activo(true)
                .build());

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch arrancarTodosJuntos = new CountDownLatch(1);

        // Cada hilo simula una sesion distinta (PC o celular) vendiendo 1
        // unidad del mismo producto. El latch los sincroniza para que
        // arranquen lo mas juntos posible y maximizar la chance real de
        // choque de version, no solo ejecutarlos uno detras del otro.
        List<Future<?>> resultados = IntStream.range(0, hilos)
                .mapToObj(i -> pool.submit(() -> {
                    try {
                        arrancarTodosJuntos.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    OptimisticRetry.ejecutar(() ->
                            ventaService.registrarVenta(
                                    List.of(new DetalleVentaRequestDTO(producto.getId(), 1))
                            )
                    );
                }))
                .collect(Collectors.toList());

        arrancarTodosJuntos.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "las ventas no terminaron a tiempo");

        // Si el retry se agoto (3 intentos, ver OptimisticRetry) esto
        // relanza la excepcion del hilo y el test falla — es la senal de
        // que la contencion fue mas alta de lo que el mecanismo tolera.
        for (Future<?> resultado : resultados) {
            resultado.get();
        }

        Producto actualizado = productoRepository.findById(producto.getId()).orElseThrow();
        assertEquals(stockInicial - hilos, actualizado.getStock(),
                "el stock final tiene que reflejar las " + hilos + " ventas, ninguna se puede haber perdido");
    }
}
