package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guerram.DespensaFamily.dto.caja.EstadoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.MovimientoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.RegistrarMovimientoRequestDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Los invariantes del libro de movimientos, como arnes: estado inicial,
 * accion, resultado esperado. Pedido explicito de Martin (Harness
 * Engineering) para esta funcionalidad.
 *
 * <p>Todas las aserciones de saldo son sobre la DIFERENCIA que produce la
 * accion del test y no sobre el numero absoluto: {@link CajaService#estadoDeCaja()}
 * es historico completo y esta suite comparte la base con el resto de los
 * tests de la corrida (mismo motivo que en {@code GananciaConPromoTest}).
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class CajaSaldoTest {

    @Autowired
    private CajaService cajaService;

    private RegistrarMovimientoRequestDTO manual(
            TipoMovimiento tipo, CategoriaMovimiento categoria, double monto,
            MetodoPago metodo, String descripcion) {
        RegistrarMovimientoRequestDTO dto = new RegistrarMovimientoRequestDTO();
        dto.setTipo(tipo);
        dto.setCategoria(categoria);
        dto.setMonto(monto);
        dto.setMetodoPago(metodo);
        dto.setDescripcion(descripcion);
        return dto;
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void registrarUnIngresoSumaAlSaldo() {
        // Caja = X (lo que sea que haya de la corrida)
        EstadoCajaDTO antes = cajaService.estadoDeCaja();

        // Registrar un ingreso de $10.000 en efectivo
        cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.INGRESO, CategoriaMovimiento.APORTE, 10000, MetodoPago.EFECTIVO, "Prueba"));

        // Resultado: Caja = X + 10.000, y el efectivo tambien sube 10.000
        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getSaldoTotal() - antes.getSaldoTotal()).isEqualTo(10000.0);
        assertThat(despues.getEfectivoEnCaja() - antes.getEfectivoEnCaja()).isEqualTo(10000.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void registrarUnEgresoRestaDelSaldo() {
        // Caja = X
        EstadoCajaDTO antes = cajaService.estadoDeCaja();

        // Registrar un egreso de $20.000
        cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.EGRESO, CategoriaMovimiento.GASTO, 20000, MetodoPago.EFECTIVO, "Factura de luz"));

        // Resultado: Caja = X - 20.000
        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getSaldoTotal() - antes.getSaldoTotal()).isEqualTo(-20000.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void revertirUnMovimientoDejaElSaldoComoAntes() {
        EstadoCajaDTO antes = cajaService.estadoDeCaja();

        MovimientoCajaDTO movimiento = cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.EGRESO, CategoriaMovimiento.RETIRO, 5000, MetodoPago.EFECTIVO, "Retiro de prueba"));

        cajaService.revertirMovimiento(movimiento.getId());

        // El saldo vuelve a estar como si el movimiento nunca hubiera pasado
        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getSaldoTotal()).isEqualTo(antes.getSaldoTotal());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void elMovimientoOriginalNoSeBorraAlRevertir() {
        MovimientoCajaDTO original = cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.INGRESO, CategoriaMovimiento.APORTE, 3000, MetodoPago.EFECTIVO, "Aporte"));

        cajaService.revertirMovimiento(original.getId());

        // Quedan los dos: el original y la reversion. No se edita ni se borra.
        assertThat(cajaService.ultimosMovimientos())
                .anySatisfy(m -> assertThat(m.getId()).isEqualTo(original.getId()));
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void noSePuedeRevertirDosVeces() {
        MovimientoCajaDTO movimiento = cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.EGRESO, CategoriaMovimiento.GASTO, 1000, MetodoPago.EFECTIVO, "Prueba"));

        cajaService.revertirMovimiento(movimiento.getId());

        assertThatThrownBy(() -> cajaService.revertirMovimiento(movimiento.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void noSePuedeRevertirUnaReversion() {
        MovimientoCajaDTO original = cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.EGRESO, CategoriaMovimiento.GASTO, 1000, MetodoPago.EFECTIVO, "Prueba"));
        MovimientoCajaDTO reversion = cajaService.revertirMovimiento(original.getId());

        assertThatThrownBy(() -> cajaService.revertirMovimiento(reversion.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unMontoCeroONegativoSeRechaza() {
        assertThatThrownBy(() -> cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.EGRESO, CategoriaMovimiento.GASTO, 0, MetodoPago.EFECTIVO, "Prueba")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unGastoSinDescripcionSeRechaza() {
        assertThatThrownBy(() -> cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.EGRESO, CategoriaMovimiento.GASTO, 500, MetodoPago.EFECTIVO, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unaVentaNoSeCargaAMano() {
        // VENTA y COMPRA las genera el flujo propio (VentaService, CompraService).
        // Cargarlas por acá rompe la trazabilidad de ventaId/compraId.
        assertThatThrownBy(() -> cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.INGRESO, CategoriaMovimiento.VENTA, 1000, MetodoPago.EFECTIVO, "Intento")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void elCajonFisicoNoIncluyeLoElectronico() {
        EstadoCajaDTO antes = cajaService.estadoDeCaja();

        cajaService.registrarMovimientoManual(
                manual(TipoMovimiento.INGRESO, CategoriaMovimiento.APORTE, 7000, MetodoPago.TARJETA, "Prueba tarjeta"));

        // Sube el saldo total y el electronico, pero el efectivo del cajon no se mueve.
        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getSaldoTotal() - antes.getSaldoTotal()).isEqualTo(7000.0);
        assertThat(despues.getSaldoElectronico() - antes.getSaldoElectronico()).isEqualTo(7000.0);
        assertThat(despues.getEfectivoEnCaja()).isEqualTo(antes.getEfectivoEnCaja());
    }
}
