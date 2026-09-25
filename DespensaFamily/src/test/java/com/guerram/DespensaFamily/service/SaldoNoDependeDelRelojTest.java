package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.caja.ArqueoRequestDTO;
import com.guerram.DespensaFamily.dto.caja.ArqueoResultadoDTO;
import com.guerram.DespensaFamily.dto.caja.RegistrarMovimientoRequestDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.MovimientoCaja;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import com.guerram.DespensaFamily.repository.MovimientoCajaRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * El saldo de caja es la suma de lo que hay en el libro, y punto: no puede
 * depender de que dos lecturas del reloj salgan en orden.
 *
 * <p>Hallazgo F-11 de la auditoria del 02/09/2026. {@code estadoDeCaja()} tomaba
 * {@code LocalDateTime.now()} y pedia el saldo <i>hasta ese instante</i>,
 * mientras que cada movimiento se guarda con su propia marca de tiempo. Si las
 * dos lecturas no salen ordenadas, el movimiento recien grabado queda afuera
 * del saldo. Es la unica explicacion que encaja con lo que se vio en la suite:
 * tres corridas de ocho fallando siempre igual, con una diferencia de
 * exactamente 0.0 — el ultimo movimiento invisible.
 *
 * <p>En la pantalla eso seria un parpadeo. En el arqueo no: la diferencia
 * contra lo esperado se graba como un ajuste permanente, asi que un movimiento
 * que no entro en la cuenta se convierte en un faltante escrito en el libro.
 *
 * <p>Estos tests fuerzan el caso de forma determinista guardando un movimiento
 * con la fecha unos segundos adelantada, que es exactamente lo que produce un
 * reloj que se corre. El filtro por "ahora" no aporta nada —el saldo actual es
 * todo el libro— y es lo que se saca.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class SaldoNoDependeDelRelojTest {

    @Autowired
    private CajaService cajaService;

    @Autowired
    private MovimientoCajaRepository movimientoCajaRepository;

    /** Un movimiento ya confirmado, con la marca de tiempo levemente adelantada. */
    private void guardarMovimientoConFechaAdelantada(double monto) {
        movimientoCajaRepository.save(MovimientoCaja.builder()
                .fechaHora(LocalDateTime.now().plusSeconds(5))
                .tipo(TipoMovimiento.INGRESO)
                .categoria(CategoriaMovimiento.APORTE)
                .monto(monto)
                .metodoPago(MetodoPago.EFECTIVO)
                .descripcion("Movimiento con el reloj corrido")
                .usuario("duenio-de-prueba")
                .build());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unMovimientoConLaFechaCorridaIgualEntraEnElSaldo() {
        double antes = cajaService.estadoDeCaja().getSaldoTotal();

        guardarMovimientoConFechaAdelantada(1234);

        assertThat(cajaService.estadoDeCaja().getSaldoTotal() - antes)
                .as("el saldo es lo que dice el libro, no lo que el reloj alcanzo a ver")
                .isEqualTo(1234.0);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void elEfectivoDelCajonTampocoSeSalteaEseMovimiento() {
        double antes = cajaService.estadoDeCaja().getEfectivoEnCaja();

        guardarMovimientoConFechaAdelantada(500);

        assertThat(cajaService.estadoDeCaja().getEfectivoEnCaja() - antes).isEqualTo(500.0);
    }

    /**
     * Deja el cajon con efectivo de sobra. Hace falta porque el saldo es el del
     * libro entero, compartido con toda la corrida: si otra clase lo dejo en
     * negativo, contar ese mismo numero seria contar un negativo, y eso ahora
     * se rechaza (F-12). Lo agarro la suite completa, no la clase sola.
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

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void elArqueoEsperaLoQueDiceElLibroCompleto() {
        // Este es el caso caro: si el arqueo no ve un movimiento, graba un
        // ajuste por una diferencia que no existe.
        sembrarEfectivoSuficiente();

        double efectivoAntes = cajaService.estadoDeCaja().getEfectivoEnCaja();

        guardarMovimientoConFechaAdelantada(800);

        ArqueoRequestDTO pedido = new ArqueoRequestDTO();
        pedido.setEfectivoContado(efectivoAntes + 800);

        ArqueoResultadoDTO resultado = cajaService.registrarArqueo(pedido);

        assertThat(resultado.getEfectivoEsperado())
                .as("lo esperado tiene que incluir el movimiento que ya esta guardado")
                .isEqualTo(efectivoAntes + 800);
        assertThat(resultado.getDiferencia())
                .as("y entonces contar bien no puede generar un faltante inventado")
                .isEqualTo(0.0);
        assertThat(resultado.getMovimiento())
                .as("sin diferencia real no se escribe un AJUSTE de plata, pero DF-UX-006"
                        + " igual deja una constancia de monto 0 en el historial")
                .isNotNull();
        assertThat(resultado.getMovimiento().getMonto())
                .as("la constancia no puede mover el saldo")
                .isEqualTo(0.0);
    }
}
