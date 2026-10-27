package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.CompraDTO;
import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraRequestDTO;
import com.guerram.DespensaFamily.dto.caja.MovimientoCajaDTO;
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
 * Deshacer algo no puede sumar. Hallazgo F-05 de la auditoria del 02/09/2026,
 * medido contra la aplicacion corriendo: una compra pagada de $5.000, al
 * anularse, dejaba {@code comprasTotales} en $10.000 en vez de en 0, y un gasto
 * de $1.000 revertido daba vuelta el signo del resultado neto.
 *
 * <p>La causa es que una reversion conserva la categoria del movimiento
 * original y solo le cambia el tipo, mientras que la consulta de rentabilidad
 * sumaba montos sin mirar el tipo ni si la fila era una reversion.
 *
 * <p>Todas las aserciones son sobre la DIFERENCIA que produce la accion del
 * test, no sobre el numero absoluto: rentabilidad se consulta por dia y esta
 * suite comparte la base con el resto de la corrida (mismo criterio que
 * {@code CajaSaldoTest}).
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class RentabilidadConReversionesTest {

    @Autowired
    private CompraService compraService;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private ReportesService reportesService;

    @Autowired
    private ProductoRepository productoRepository;

    private static final LocalDate HOY = LocalDate.now();

    private RentabilidadDTO hoy() {
        return reportesService.rentabilidad(HOY, HOY);
    }

    private Producto productoDePrueba() {
        return productoRepository.save(Producto.builder()
                .nombre("Producto de prueba reversiones")
                .precioCosto(BigDecimal.valueOf(100)).precioVenta(BigDecimal.valueOf(200)).porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(0)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
    }

    private CompraDTO comprar(Producto producto, double cantidad, double costoUnitario, boolean pagada) {
        DetalleCompraRequestDTO detalle = new DetalleCompraRequestDTO();
        detalle.setProductoId(producto.getId());
        detalle.setCantidad(BigDecimal.valueOf(cantidad));
        detalle.setPrecioCosto(BigDecimal.valueOf(costoUnitario));

        CompraRequestDTO compra = new CompraRequestDTO();
        compra.setProveedor("Proveedor de prueba");
        compra.setPagada(pagada);
        compra.setMetodoPago(MetodoPago.EFECTIVO);
        compra.setDetalles(List.of(detalle));

        return compraService.registrarCompra(compra);
    }

    private MovimientoCajaDTO gasto(double monto, String descripcion) {
        RegistrarMovimientoRequestDTO dto = new RegistrarMovimientoRequestDTO();
        dto.setTipo(TipoMovimiento.EGRESO);
        dto.setCategoria(CategoriaMovimiento.GASTO);
        dto.setMonto(BigDecimal.valueOf(monto));
        dto.setMetodoPago(MetodoPago.EFECTIVO);
        dto.setDescripcion(descripcion);
        return cajaService.registrarMovimientoManual(dto);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void anularUnaCompraPagadaLaSacaDeComprasTotales() {
        // Estado: rentabilidad de hoy, con lo que haya.
        BigDecimal comprasAntes = hoy().getComprasTotales();
        Producto producto = productoDePrueba();

        // Accion: comprar $5.000 pagando en el momento.
        CompraDTO compra = comprar(producto, 10, 500, true);
        assertThat(hoy().getComprasTotales().subtract(comprasAntes))
                .as("la compra pagada tiene que sumar una vez")
                .isEqualByComparingTo(BigDecimal.valueOf(5000.0));

        // Accion: anularla. La caja se revierte con un movimiento de signo
        // contrario, que es lo que la consulta contaba como otra compra.
        compraService.anularCompra(compra.getId());

        // Esperado: es como si la compra nunca hubiera pasado.
        assertThat(hoy().getComprasTotales().subtract(comprasAntes))
                .as("una compra anulada no puede seguir contando, y menos contar el doble")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void revertirUnGastoLoSacaDeLosGastosYDelResultadoNeto() {
        RentabilidadDTO antes = hoy();

        MovimientoCajaDTO movimiento = gasto(1000, "Luz de prueba");
        RentabilidadDTO conGasto = hoy();
        assertThat(conGasto.getGastosTotales().subtract(antes.getGastosTotales()))
                .isEqualByComparingTo(BigDecimal.valueOf(1000.0));
        assertThat(conGasto.getResultadoNeto().subtract(antes.getResultadoNeto()))
                .isEqualByComparingTo(BigDecimal.valueOf(-1000.0));

        cajaService.revertirMovimiento(movimiento.getId());

        RentabilidadDTO despues = hoy();
        assertThat(despues.getGastosTotales().subtract(antes.getGastosTotales()))
                .as("un gasto revertido no es un gasto")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(despues.getResultadoNeto().subtract(antes.getResultadoNeto()))
                .as("y el resultado neto tiene que volver a donde estaba, no darse vuelta")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unGastoQueNadieRevirtioSigueContando() {
        // Triangulacion: el arreglo no puede pasarse de largo y dejar de
        // contar los movimientos normales, que son la mayoria.
        BigDecimal gastosAntes = hoy().getGastosTotales();

        gasto(700, "Insumos de prueba");

        assertThat(hoy().getGastosTotales().subtract(gastosAntes))
                .isEqualByComparingTo(BigDecimal.valueOf(700.0));
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unaCompraACuentaNoCuentaHastaQueSePaga() {
        // Triangulacion por el otro lado: comprasTotales sale del libro de
        // caja, asi que una compra que todavia no saco plata no puede figurar.
        BigDecimal comprasAntes = hoy().getComprasTotales();
        Producto producto = productoDePrueba();

        CompraDTO compra = comprar(producto, 4, 250, false);
        assertThat(hoy().getComprasTotales().subtract(comprasAntes))
                .as("una compra a cuenta todavia no movio plata")
                .isEqualByComparingTo(BigDecimal.ZERO);

        compraService.marcarPagada(compra.getId());
        assertThat(hoy().getComprasTotales().subtract(comprasAntes))
                .as("y al pagarla, cuenta una sola vez")
                .isEqualByComparingTo(BigDecimal.valueOf(1000.0));
    }
}
