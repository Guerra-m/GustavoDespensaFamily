package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.caja.RegistrarMovimientoRequestDTO;
import com.guerram.DespensaFamily.dto.reportes.RentabilidadDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Fase 8/9 del mapa de cambios: la pantalla de rentabilidad no puede
 * confundir ventas con ingresos ni compras con gastos, y no puede inventar
 * un "resultado neto" cuando nadie cargó gastos.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class RentabilidadTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private ReportesService reportesService;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void sinGastosCargadosElResultadoNetoNoSeMuestraComoUnDatoReal() {
        // Un dia en el pasado sin ningun movimiento, y no "hoy": otros tests
        // de esta misma corrida (CajaSaldoTest.registrarUnEgresoRestaDelSaldo)
        // cargan un GASTO fechado hoy, y compartimos la base con toda la
        // suite. Consultar hoy haria este test fragil segun el orden en que
        // JUnit decida correr las clases.
        LocalDate diaSinMovimientos = LocalDate.of(2000, 1, 1);

        // No se carga ningun gasto en este test — es justo el punto.
        RentabilidadDTO r = reportesService.rentabilidad(diaSinMovimientos, diaSinMovimientos);

        // gastosCargados en false es la señal que la pantalla usa para NO
        // mostrar el resultado neto como si fuera un numero confiable — la
        // API igual devuelve gananciaBruta - 0, pero el flag dice que ese 0
        // no es un dato, es la ausencia de uno.
        assertThat(r.isGastosCargados()).isFalse();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unGastoCargadoBajaElResultadoNetoYNoTocaLaGananciaBruta() {
        LocalDate hoy = LocalDate.now();
        Producto p = productoRepository.save(Producto.builder()
                .nombre("Producto rentabilidad")
                .precioCosto(600).precioVenta(1000).porcentajeGanancia(66.67)
                .stock(10).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        RentabilidadDTO antes = reportesService.rentabilidad(hoy, hoy);

        // Vender genera ganancia bruta ($400 por unidad: 1000 - 600)
        ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(p.getId(), 1.0)), MetodoPago.EFECTIVO);

        // Cargar un gasto de $150 no relacionado con mercaderia
        RegistrarMovimientoRequestDTO gasto = new RegistrarMovimientoRequestDTO();
        gasto.setTipo(TipoMovimiento.EGRESO);
        gasto.setCategoria(CategoriaMovimiento.GASTO);
        gasto.setMonto(150);
        gasto.setMetodoPago(MetodoPago.EFECTIVO);
        gasto.setDescripcion("Insumos de limpieza");
        cajaService.registrarMovimientoManual(gasto);

        RentabilidadDTO despues = reportesService.rentabilidad(hoy, hoy);

        assertThat(despues.isGastosCargados()).isTrue();
        assertThat(despues.getGananciaBruta() - antes.getGananciaBruta()).isEqualTo(400.0);
        assertThat(despues.getGastosTotales() - antes.getGastosTotales()).isEqualTo(150.0);
        // El resultado neto SI baja por el gasto — pero la ganancia bruta no,
        // porque comprar/gastar y vender son cosas distintas (Fase 9).
        assertThat(despues.getResultadoNeto() - antes.getResultadoNeto()).isEqualTo(250.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unRetiroNoCuentaComoGastoOperativo() {
        // RETIRO es plata que el dueño saca para uso personal: no es un gasto
        // del negocio, es un movimiento de capital. Si contara como gasto,
        // sacar plata del cajon "empeoraria" artificialmente la rentabilidad.
        LocalDate hoy = LocalDate.now();
        RentabilidadDTO antes = reportesService.rentabilidad(hoy, hoy);

        RegistrarMovimientoRequestDTO retiro = new RegistrarMovimientoRequestDTO();
        retiro.setTipo(TipoMovimiento.EGRESO);
        retiro.setCategoria(CategoriaMovimiento.RETIRO);
        retiro.setMonto(9999);
        retiro.setMetodoPago(MetodoPago.EFECTIVO);
        retiro.setDescripcion("Retiro personal");
        cajaService.registrarMovimientoManual(retiro);

        RentabilidadDTO despues = reportesService.rentabilidad(hoy, hoy);

        assertThat(despues.getGastosTotales()).isEqualTo(antes.getGastosTotales());
        assertThat(despues.getResultadoNeto()).isEqualTo(antes.getResultadoNeto());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void comprarMercaderiaNoEsUnaPerdida() {
        // Fase 9, el ejemplo exacto del pedido: comprar $100.000 de mercaderia
        // reduce caja y sube stock, pero no significa perder $100.000. La
        // ganancia bruta no se mueve por una compra — aparece cuando esa
        // mercaderia se vende.
        LocalDate hoy = LocalDate.now();
        Producto p = productoRepository.save(Producto.builder()
                .nombre("Producto compra sin venta")
                .precioCosto(50).precioVenta(75).porcentajeGanancia(50)
                .stock(0).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        RentabilidadDTO antes = reportesService.rentabilidad(hoy, hoy);

        cajaService.registrarMovimiento(
                TipoMovimiento.EGRESO, CategoriaMovimiento.COMPRA, 500, MetodoPago.EFECTIVO,
                "Compra de prueba", null, null);

        RentabilidadDTO despues = reportesService.rentabilidad(hoy, hoy);

        assertThat(despues.getComprasTotales() - antes.getComprasTotales()).isEqualTo(500.0);
        assertThat(despues.getGananciaBruta()).isEqualTo(antes.getGananciaBruta());
        assertThat(despues.getResultadoNeto()).isEqualTo(antes.getResultadoNeto());
    }
}
