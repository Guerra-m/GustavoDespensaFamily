package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Efecto colateral encontrado corriendo la suite completa para DF-PERF-002:
 * con mas datos de prueba en la corrida, VentaConCajaTest.vender10000EnEfectivoSubeElSaldo10000
 * empezo a fallar por 2e-12 de diferencia (9999.999999999998 vs 10000.0) --
 * arrastre de precision de {@code double} en {@link MovimientoCajaRepository#saldoActual}
 * al sumar de a miles de filas, algunas con importes que vienen de un
 * porcentaje (ganancia, descuentos) y no caen justo en el centavo.
 *
 * <p>Arqueo ya redondeaba con {@link com.guerram.DespensaFamily.util.Dinero#redondear}
 * antes de guardar; esta clase generaliza lo mismo a TODO movimiento de caja,
 * sea manual, de venta, de compra o una reversion -- la plata en el libro
 * nunca deberia tener mas de dos decimales guardados, sea cual sea el origen.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class MontoDeCajaSeRedondeaAlCentavoTest {

    @Autowired
    private CajaService cajaService;

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unMontoConMasDeDosDecimalesQuedaRedondeadoAlGuardarse() {
        RegistrarMovimientoRequestDTO dto = new RegistrarMovimientoRequestDTO();
        dto.setTipo(TipoMovimiento.INGRESO);
        dto.setCategoria(CategoriaMovimiento.APORTE);
        dto.setMonto(1234.5678);
        dto.setMetodoPago(MetodoPago.EFECTIVO);
        dto.setDescripcion("Prueba de redondeo");

        MovimientoCajaDTO registrado = cajaService.registrarMovimientoManual(dto);

        assertThat(registrado.getMonto()).isEqualTo(1234.57);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unMontoYaExactoAlCentavoNoCambia() {
        // Triangulacion: el redondeo no puede alterar un valor que ya era
        // exacto (caso mas comun: montos enteros como 10000).
        RegistrarMovimientoRequestDTO dto = new RegistrarMovimientoRequestDTO();
        dto.setTipo(TipoMovimiento.EGRESO);
        dto.setCategoria(CategoriaMovimiento.GASTO);
        dto.setMonto(500.25);
        dto.setMetodoPago(MetodoPago.EFECTIVO);
        dto.setDescripcion("Prueba de redondeo exacto");

        MovimientoCajaDTO registrado = cajaService.registrarMovimientoManual(dto);

        assertThat(registrado.getMonto()).isEqualTo(500.25);
    }
}
