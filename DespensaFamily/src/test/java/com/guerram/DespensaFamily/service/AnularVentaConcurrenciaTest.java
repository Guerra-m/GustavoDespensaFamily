package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
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

// DF-DEBT-003: anularVenta devuelve stock tocando detalle.getProducto()
// directo (la entidad SIN lock que ya trajo el findById de la venta), a
// diferencia de registrarVenta/CompraService que siempre pasan por
// buscarParaActualizarStock (SELECT ... FOR UPDATE). Este test demuestra el
// mismo tipo de escenario que VentaConcurrenciaTest: una anulacion
// concurrente con ventas nuevas del MISMO producto. Si anularVenta no toma
// el lock pesimista, el +stock de la anulacion puede perderse (lost update)
// contra los -stock de las ventas que corren al mismo tiempo, y el stock
// final no cierra.
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class AnularVentaConcurrenciaTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    void anularUnaVentaAlMismoTiempoQueSeVendeElMismoProductoNoPierdeElStockDevuelto()
            throws InterruptedException, ExecutionException {
        final double stockInicial = 100;

        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Anulacion concurrente " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(5))
                .precioVenta(BigDecimal.valueOf(10))
                .porcentajeGanancia(BigDecimal.valueOf(30))
                .stock(BigDecimal.valueOf(stockInicial))
                .tipoCantidad("UNIDAD")
                .unidadMedida("u")
                .fraccionMinima(BigDecimal.valueOf(1.0))
                .activo(true)
                .build());

        // Una venta previa de 10 unidades: esta es la que se va a anular en
        // paralelo con otras ventas nuevas del mismo producto.
        VentaDTO ventaAAnular = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(10))));

        final int hilosDeVenta = 19;
        ExecutorService pool = Executors.newFixedThreadPool(hilosDeVenta + 1);
        CountDownLatch arrancarTodosJuntos = new CountDownLatch(1);

        List<Future<?>> resultados = IntStream.range(0, hilosDeVenta)
                .mapToObj(i -> pool.submit(() -> {
                    try {
                        arrancarTodosJuntos.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    OptimisticRetry.ejecutar(() ->
                            ventaService.registrarVenta(
                                    List.of(new DetalleVentaRequestDTO(producto.getId(), BigDecimal.valueOf(1)))
                            )
                    );
                }))
                .collect(Collectors.toList());

        Future<?> anulacion = pool.submit(() -> {
            try {
                arrancarTodosJuntos.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            OptimisticRetry.ejecutar(() -> {
                ventaService.anularVenta(ventaAAnular.getId());
                return null;
            });
        });

        arrancarTodosJuntos.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "las operaciones no terminaron a tiempo");

        for (Future<?> resultado : resultados) {
            resultado.get();
        }
        anulacion.get();

        // Esperado: 100 - 10 (venta original) + 10 (la anulacion la devuelve)
        // - 19 (las ventas concurrentes) = 81. Si anularVenta pierde su
        // +10 contra alguna de las ventas concurrentes (lost update por
        // falta de lock pesimista), el stock final da menos que esto.
        Producto actualizado = productoRepository.findById(producto.getId()).orElseThrow();
        BigDecimal esperado = BigDecimal.valueOf(stockInicial - 10 + 10 - hilosDeVenta);
        assertEquals(0, esperado.compareTo(actualizado.getStock()),
                "el stock final tiene que reflejar la venta original, su anulacion y las "
                        + hilosDeVenta + " ventas concurrentes, sin perder ningun ajuste");
    }
}
