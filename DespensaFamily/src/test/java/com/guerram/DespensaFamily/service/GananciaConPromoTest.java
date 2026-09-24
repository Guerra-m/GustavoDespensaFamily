package com.guerram.DespensaFamily.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.reportes.GananciaPorFechaDTO;
import com.guerram.DespensaFamily.dto.reportes.ResumenVentasDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.Promocion;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.repository.PromocionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * La ganancia con una promocion activa tiene que dar el mismo numero la mires
 * por donde la mires.
 *
 * <p>Encontrado auditando los reportes antes de construir caja encima:
 * {@code /reportes/ganancia} calculaba
 * {@code cantidad * (precioUnitario - precioCosto)}, que ignora que un combo
 * como 3x2 cobra menos unidades de las que salieron por precio de lista. Con
 * un producto de $1.000 y costo $600, vendiendo 3 unidades con un 3x2:
 *
 * <pre>
 * Se cobraron 2 unidades (subtotal)         $2.000
 * Costaron 3 unidades                       $1.800
 * Ganancia real                               $200
 *
 * cantidad * (precioUnitario - precioCosto) = 3 * (1000-600) = $1.200
 * </pre>
 *
 * seis veces mas que la ganancia real, y encima {@code /reportes/resumen}
 * calculaba bien el mismo caso: dos endpoints, dos respuestas distintas a la
 * misma pregunta. Este test fija la ganancia correcta y la coincidencia entre
 * los dos reportes como el comportamiento esperado.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class GananciaConPromoTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ReportesService reportesService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PromocionRepository promocionRepository;

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void laGananciaConPromoNoIgnoraElDescuento() {
        LocalDate hoy = LocalDate.now();

        // La suite completa registra otras ventas "hoy": gananciaPorFecha
        // agrupa solo por fecha, asi que se mide la diferencia que produce
        // esta venta y no el total absoluto del dia, que depende de que mas
        // corrio antes en la misma corrida.
        double gananciaAntes = gananciaDeHoy(hoy);
        double ventasAntes = ventasDeHoy(hoy);

        Producto producto = productoRepository.save(Producto.builder()
                .nombre("Producto con promo 3x2")
                .precioCosto(600).precioVenta(1000).porcentajeGanancia(66.67)
                .stock(50).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        promocionRepository.save(Promocion.builder()
                .producto(producto)
                .cantidadLleva(3)
                .cantidadPaga(2)
                .activo(true)
                .build());

        ventaService.registrarVenta(List.of(
                new DetalleVentaRequestDTO(producto.getId(), 3.0)));

        // /reportes/ganancia
        double ventasDespues = ventasDeHoy(hoy);
        double gananciaDespues = gananciaDeHoy(hoy);
        assertEquals(2000.0, ventasDespues - ventasAntes, 0.01);
        assertEquals(200.0, gananciaDespues - gananciaAntes, 0.01);

        // /reportes/resumen — misma pregunta sobre el mismo estado de la base
        // (se consulta en el mismo instante, sin registrar nada en el medio),
        // asi que los dos totales ABSOLUTOS tienen que coincidir aunque
        // incluyan ventas de otros tests de la misma corrida.
        ResumenVentasDTO resumen = reportesService.resumenVentas("dia");
        assertEquals(gananciaDespues, resumen.getGanancia(), 0.01);
    }

    private double gananciaDeHoy(LocalDate hoy) {
        List<GananciaPorFechaDTO> porFecha = reportesService.gananciaPorFecha(hoy, hoy);
        return porFecha.isEmpty() ? 0.0 : porFecha.get(0).getGanancia();
    }

    private double ventasDeHoy(LocalDate hoy) {
        List<GananciaPorFechaDTO> porFecha = reportesService.gananciaPorFecha(hoy, hoy);
        return porFecha.isEmpty() ? 0.0 : porFecha.get(0).getTotalVentas();
    }
}
