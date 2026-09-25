package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.dto.caja.ArqueoRequestDTO;
import com.guerram.DespensaFamily.dto.caja.ArqueoResultadoDTO;
import com.guerram.DespensaFamily.dto.caja.EstadoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.MovimientoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.RegistrarMovimientoRequestDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * DF-UX-006 de la auditoria del 23/09: dos hallazgos sobre la misma pantalla
 * de Caja.
 *
 * <ol>
 *   <li>Anular una venta aparecia mezclada dentro de "egresos de hoy", como
 *   si fuera un gasto real -- un dia con una sola venta grande anulada
 *   mostraba "Egresos de hoy $36.560,80" casi entero compuesto por esa
 *   anulacion. Ahora "egresos de hoy" son solo movimientos originales
 *   (revierteA IS NULL) y las anulaciones se cuentan aparte.</li>
 *   <li>Un arqueo con diferencia exacta (cont igual a lo esperado) no dejaba
 *   ninguna constancia: nadie podia saber despues que alguien conto el
 *   cajon ese dia. Ahora {@link CajaService#registrarArqueo} siempre
 *   devuelve un movimiento, incluso con monto 0, que no mueve el saldo pero
 *   queda en el historial.</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class AnulacionesYArqueoExactoTest {

    @Autowired
    private CajaService cajaService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoRepository productoRepository;

    private Producto producto(double precioVenta, double stock) {
        return productoRepository.save(Producto.builder()
                .nombre("Producto para anulacion de prueba")
                .precioCosto(precioVenta / 2).precioVenta(precioVenta).porcentajeGanancia(100)
                .stock(stock).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
    }

    // --- Anulaciones no cuentan como "egresos" ---

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void anularUnaVentaNoCambiaLosEgresosDeHoy() {
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto(20000, 5);

        VentaDTO venta = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(p.getId(), 1.0)), MetodoPago.EFECTIVO);

        ventaService.anularVenta(venta.getId());

        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getEgresosDeHoy()).isEqualTo(antes.getEgresosDeHoy());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void anularUnMovimientoManualNoCambiaLosEgresosDeHoyPeroSiLasAnulaciones() {
        // Triangulacion con una segunda categoria (GASTO en vez de VENTA):
        // el mismo criterio (revierteA IS NULL) tiene que aplicar sin
        // importar la categoria del movimiento revertido.
        EstadoCajaDTO antes = cajaService.estadoDeCaja();

        RegistrarMovimientoRequestDTO gasto = new RegistrarMovimientoRequestDTO();
        gasto.setTipo(TipoMovimiento.EGRESO);
        gasto.setCategoria(CategoriaMovimiento.GASTO);
        gasto.setMonto(4000);
        gasto.setMetodoPago(MetodoPago.EFECTIVO);
        gasto.setDescripcion("Gasto de prueba para anular");
        MovimientoCajaDTO registrado = cajaService.registrarMovimientoManual(gasto);

        // Con el gasto cargado, "egresos de hoy" sube 4000.
        EstadoCajaDTO conGasto = cajaService.estadoDeCaja();
        assertThat(conGasto.getEgresosDeHoy() - antes.getEgresosDeHoy()).isEqualTo(4000.0);

        cajaService.revertirMovimiento(registrado.getId());

        // El original no se borra ni se toca (politica del libro), asi que
        // "egresos de hoy" sigue reflejandolo -- lo que cambia es que ahora
        // la reversion aparece contada aparte, en "anulaciones de hoy".
        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getEgresosDeHoy() - antes.getEgresosDeHoy()).isEqualTo(4000.0);
        assertThat(despues.getAnulacionesDeHoy() - antes.getAnulacionesDeHoy()).isEqualTo(4000.0);
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void lasVentasNetasDeHoyDescuentanSuPropiaAnulacion() {
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto(15000, 5);

        VentaDTO venta = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(p.getId(), 1.0)), MetodoPago.EFECTIVO);

        EstadoCajaDTO conVenta = cajaService.estadoDeCaja();
        assertThat(conVenta.getVentasNetasDeHoy() - antes.getVentasNetasDeHoy()).isEqualTo(15000.0);

        ventaService.anularVenta(venta.getId());

        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getVentasNetasDeHoy()).isEqualTo(antes.getVentasNetasDeHoy());
    }

    // --- Arqueo exacto queda registrado ---

    private void sembrarEfectivoSuficiente() {
        RegistrarMovimientoRequestDTO aporte = new RegistrarMovimientoRequestDTO();
        aporte.setTipo(TipoMovimiento.INGRESO);
        aporte.setCategoria(CategoriaMovimiento.APORTE);
        aporte.setMonto(Math.max(10_000, Math.abs(cajaService.estadoDeCaja().getEfectivoEnCaja()) + 10_000));
        aporte.setMetodoPago(MetodoPago.EFECTIVO);
        aporte.setDescripcion("Piso de efectivo para el test de arqueo exacto");
        cajaService.registrarMovimientoManual(aporte);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unArqueoExactoQuedaRegistradoComoConstancia() {
        sembrarEfectivoSuficiente();
        double esperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        ArqueoRequestDTO pedido = new ArqueoRequestDTO();
        pedido.setEfectivoContado(esperado);
        pedido.setDescripcion(null);

        ArqueoResultadoDTO resultado = cajaService.registrarArqueo(pedido);

        assertThat(resultado.getDiferencia()).isEqualTo(0.0);
        assertThat(resultado.getMovimiento()).isNotNull();
        assertThat(resultado.getMovimiento().getMonto()).isEqualTo(0.0);
        assertThat(resultado.getMovimiento().getCategoria()).isEqualTo(CategoriaMovimiento.AJUSTE);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unArqueoExactoNoMueveElSaldo() {
        // Triangulacion: ademas de quedar registrado, el monto 0 no puede
        // alterar el saldo -- ni para arriba ni para abajo.
        sembrarEfectivoSuficiente();
        double esperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        ArqueoRequestDTO pedido = new ArqueoRequestDTO();
        pedido.setEfectivoContado(esperado);

        cajaService.registrarArqueo(pedido);

        assertThat(cajaService.estadoDeCaja().getEfectivoEnCaja()).isEqualTo(esperado);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void elArqueoExactoApareceEnElHistorialDeMovimientos() {
        sembrarEfectivoSuficiente();
        double esperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        ArqueoRequestDTO pedido = new ArqueoRequestDTO();
        pedido.setEfectivoContado(esperado);
        pedido.setDescripcion("Conteo de cierre, todo en orden");

        ArqueoResultadoDTO resultado = cajaService.registrarArqueo(pedido);

        assertThat(cajaService.ultimosMovimientos())
                .anySatisfy(m -> assertThat(m.getId()).isEqualTo(resultado.getMovimiento().getId()));
    }
}
