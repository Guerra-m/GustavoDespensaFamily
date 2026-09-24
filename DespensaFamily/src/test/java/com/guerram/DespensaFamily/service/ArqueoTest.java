package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guerram.DespensaFamily.dto.caja.ArqueoRequestDTO;
import com.guerram.DespensaFamily.dto.caja.ArqueoResultadoDTO;
import com.guerram.DespensaFamily.dto.caja.EstadoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.RegistrarMovimientoRequestDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Contar el cajón contra lo que la caja espera. Sección 6 del mapa de
 * cambios: sin apertura ni cierre, un movimiento AJUSTE más.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class ArqueoTest {

    @Autowired
    private CajaService cajaService;

    /**
     * Deja el cajon con efectivo de sobra antes de contar.
     *
     * <p>Estos tests cuentan "lo esperado menos $1.500", y el efectivo esperado
     * es el del libro entero, compartido con toda la corrida: si otra clase
     * dejo la caja en negativo, ese "menos 1.500" da un numero negativo. Contar
     * un negativo ahora se rechaza (F-12), y con razon — un cajon no puede
     * tener menos que nada. Sembrar el piso hace que el test pruebe lo que dice
     * probar sin depender del orden en que corran las demas clases.
     */
    private void sembrarEfectivoSuficiente() {
        RegistrarMovimientoRequestDTO aporte = new RegistrarMovimientoRequestDTO();
        aporte.setTipo(TipoMovimiento.INGRESO);
        aporte.setCategoria(CategoriaMovimiento.APORTE);
        aporte.setMonto(Math.max(10_000, Math.abs(cajaService.estadoDeCaja().getEfectivoEnCaja()) + 10_000));
        aporte.setMetodoPago(MetodoPago.EFECTIVO);
        aporte.setDescripcion("Piso de efectivo para el test de arqueo");
        cajaService.registrarMovimientoManual(aporte);
    }

    private ArqueoRequestDTO pedido(double contado, String descripcion) {
        ArqueoRequestDTO dto = new ArqueoRequestDTO();
        dto.setEfectivoContado(contado);
        dto.setDescripcion(descripcion);
        return dto;
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void contarMenosDeLoEsperadoGeneraUnAjusteNegativo() {
        sembrarEfectivoSuficiente();

        // Caja en efectivo = X (lo que sea que haya)
        double esperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        // Se cuenta el cajón y falta $1.500 contra lo esperado
        ArqueoResultadoDTO resultado = cajaService.registrarArqueo(pedido(esperado - 1500, null));

        assertThat(resultado.getDiferencia()).isEqualTo(-1500.0);
        assertThat(resultado.getMovimiento()).isNotNull();
        assertThat(resultado.getMovimiento().getTipo()).isEqualTo(TipoMovimiento.EGRESO);
        assertThat(resultado.getMovimiento().getCategoria()).isEqualTo(CategoriaMovimiento.AJUSTE);
        assertThat(resultado.getMovimiento().getMonto()).isEqualTo(1500.0);

        // Y el saldo de efectivo queda exactamente en lo contado, no en lo esperado
        assertThat(cajaService.estadoDeCaja().getEfectivoEnCaja()).isEqualTo(esperado - 1500);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void contarMasDeLoEsperadoGeneraUnAjustePositivo() {
        double esperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        ArqueoResultadoDTO resultado = cajaService.registrarArqueo(pedido(esperado + 200, null));

        assertThat(resultado.getDiferencia()).isEqualTo(200.0);
        assertThat(resultado.getMovimiento().getTipo()).isEqualTo(TipoMovimiento.INGRESO);
        assertThat(cajaService.estadoDeCaja().getEfectivoEnCaja()).isEqualTo(esperado + 200);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void siDaExactoNoSeCreaNingunMovimiento() {
        double esperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        ArqueoResultadoDTO resultado = cajaService.registrarArqueo(pedido(esperado, null));

        assertThat(resultado.getDiferencia()).isEqualTo(0.0);
        assertThat(resultado.getMovimiento()).isNull();
        // El saldo no se movio en absoluto: no hay ningun ajuste de $0 en el libro.
        assertThat(cajaService.estadoDeCaja().getEfectivoEnCaja()).isEqualTo(esperado);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void elArqueoSoloMiraElEfectivoNoLoElectronico() {
        // Estado inicial: metemos plata por tarjeta a proposito
        EstadoCajaDTO antesDeTarjeta = cajaService.estadoDeCaja();
        RegistrarMovimientoRequestDTO tarjeta = new RegistrarMovimientoRequestDTO();
        tarjeta.setTipo(TipoMovimiento.INGRESO);
        tarjeta.setCategoria(CategoriaMovimiento.APORTE);
        tarjeta.setMonto(50000);
        tarjeta.setMetodoPago(MetodoPago.TARJETA);
        tarjeta.setDescripcion("Prueba: no deberia afectar el arqueo");
        cajaService.registrarMovimientoManual(tarjeta);

        double efectivoEsperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        // El arqueo cuenta el mismo efectivo de antes: no tiene que aparecer
        // ninguna diferencia por la plata que entro con tarjeta.
        ArqueoResultadoDTO resultado = cajaService.registrarArqueo(pedido(efectivoEsperado, null));

        assertThat(resultado.getDiferencia()).isEqualTo(0.0);
        assertThat(resultado.getMovimiento()).isNull();
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unaDescripcionPropiaSeConservaYSinoQuedaUnaAutomatica() {
        sembrarEfectivoSuficiente();

        double esperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        ArqueoResultadoDTO conNota = cajaService.registrarArqueo(pedido(esperado - 100, "Faltante del turno tarde"));
        assertThat(conNota.getMovimiento().getDescripcion()).isEqualTo("Faltante del turno tarde");

        double esperado2 = cajaService.estadoDeCaja().getEfectivoEnCaja();
        ArqueoResultadoDTO sinNota = cajaService.registrarArqueo(pedido(esperado2 - 50, null));
        assertThat(sinNota.getMovimiento().getDescripcion()).contains("Arqueo");
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void contarUnaCantidadNegativaSeRechaza() {
        // Hallazgo F-12 de la auditoria del 02/09/2026: se conto "-99999" y la
        // caja acepto el numero sin chistar, grabando un egreso de ajuste de
        // $99.999. Un cajon no puede tener menos que nada: un signo de mas
        // mal tipeado no puede terminar en un faltante enorme escrito en el
        // libro (revertible, pero el rastro queda).
        assertThatThrownBy(() -> cajaService.registrarArqueo(pedido(-99999, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void contarCeroSiEsValido() {
        // Triangulacion: cero no es negativo. Un cajon vacio es un estado
        // perfectamente normal —el dueño se llevo todo— y tiene que poder
        // contarse.
        sembrarEfectivoSuficiente();

        double esperado = cajaService.estadoDeCaja().getEfectivoEnCaja();

        ArqueoResultadoDTO resultado = cajaService.registrarArqueo(pedido(0, "Cajon vacio"));

        assertThat(resultado.getEfectivoContado()).isEqualTo(0.0);
        assertThat(resultado.getDiferencia()).isEqualTo(-esperado);
    }
}
