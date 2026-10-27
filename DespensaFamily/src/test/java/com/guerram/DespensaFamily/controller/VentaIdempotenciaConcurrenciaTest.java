package com.guerram.DespensaFamily.controller;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * DF-BUG-003: dos peticiones HTTP concurrentes con la MISMA Idempotency-Key
 * (el caso real de un doble clic en "Confirmar Venta", donde las dos
 * peticiones salen casi juntas y ninguna alcanza a ver el commit de la
 * otra antes de intentar insertar) tienen que terminar en UNA sola venta,
 * no en un 500 ni en dos filas. Mismo patron de latch que
 * VentaConcurrenciaTest, corrido varias veces para descartar flakiness.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
@AutoConfigureMockMvc
class VentaIdempotenciaConcurrenciaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    private static final RequestPostProcessor EMPLEADO = user("empleado").roles("EMPLEADO");
    private static final ObjectMapper JSON = new ObjectMapper();

    @RepeatedTest(3)
    void dosPeticionesConcurrentesConLaMismaClaveGeneranUnaSolaVenta() throws Exception {
        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Idempotencia concurrente " + System.nanoTime())
                .precioCosto(BigDecimal.valueOf(500)).precioVenta(BigDecimal.valueOf(1000)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(50)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        String clave = UUID.randomUUID().toString();
        String body = JSON.writeValueAsString(java.util.Map.of(
                "detalles", List.of(java.util.Map.of("productoId", producto.getId(), "cantidad", 1)),
                "metodoPago", "EFECTIVO"));

        int hilos = 5;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch arrancarJuntos = new CountDownLatch(1);

        List<Future<Long>> resultados = IntStream.range(0, hilos)
                .mapToObj(i -> pool.submit(() -> {
                    arrancarJuntos.await();
                    var resultado = mockMvc.perform(post("/ventas")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("Idempotency-Key", clave)
                                    .content(body)
                                    .with(EMPLEADO))
                            .andReturn();
                    assertTrue(resultado.getResponse().getStatus() < 500,
                            "no tendria que devolver un error de servidor: " + resultado.getResponse().getContentAsString());
                    var json = JSON.readTree(resultado.getResponse().getContentAsString());
                    return json.get("id").asLong();
                }))
                .collect(Collectors.toList());

        arrancarJuntos.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "las peticiones no terminaron a tiempo");

        Set<Long> idsDistintos = new HashSet<>();
        for (Future<Long> resultado : resultados) {
            idsDistintos.add(resultado.get());
        }

        assertEquals(1, idsDistintos.size(),
                "todas las peticiones concurrentes con la misma clave tendrian que devolver la MISMA venta");

        Producto releido = productoRepository.findById(producto.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(49.0).compareTo(releido.getStock()),
                "el stock solo se tiene que haber descontado una vez");
    }
}
