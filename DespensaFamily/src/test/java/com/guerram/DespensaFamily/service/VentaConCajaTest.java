package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guerram.DespensaFamily.dto.DetalleVentaRequestDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.dto.caja.EstadoCajaDTO;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Vender genera un ingreso de caja, y las dos cosas son una sola operacion.
 *
 * <p>Fase 10 del mapa de cambios, Harness Engineering pedido explicitamente:
 * estado inicial, accion, resultado esperado, para cada invariante.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class VentaConCajaTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private ProductoRepository productoRepository;

    private Producto producto(double precioVenta, double stock) {
        return productoRepository.save(Producto.builder()
                .nombre("Producto de prueba caja")
                .precioCosto(BigDecimal.valueOf(precioVenta / 2)).precioVenta(BigDecimal.valueOf(precioVenta))
                .porcentajeGanancia(BigDecimal.valueOf(100))
                .stock(BigDecimal.valueOf(stock)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void vender10000EnEfectivoSubeElSaldo10000() {
        // Caja = X
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto(10000, 5);

        // Registrar venta de $10.000 en efectivo
        VentaDTO venta = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(p.getId(), BigDecimal.valueOf(1.0))), MetodoPago.EFECTIVO);

        // Resultado: Caja = X + 10.000, venta registrada, movimiento +$10.000
        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(venta.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(10000.0));
        assertThat(despues.getSaldoTotal().subtract(antes.getSaldoTotal())).isEqualByComparingTo(BigDecimal.valueOf(10000.0));
        assertThat(despues.getEfectivoEnCaja().subtract(antes.getEfectivoEnCaja())).isEqualByComparingTo(BigDecimal.valueOf(10000.0));
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void venderConTarjetaNoMueveElEfectivoFisico() {
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto(5000, 5);

        ventaService.registrarVenta(List.of(new DetalleVentaRequestDTO(p.getId(), BigDecimal.valueOf(1.0))), MetodoPago.TARJETA);

        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getSaldoTotal().subtract(antes.getSaldoTotal())).isEqualByComparingTo(BigDecimal.valueOf(5000.0));
        assertThat(despues.getSaldoElectronico().subtract(antes.getSaldoElectronico())).isEqualByComparingTo(BigDecimal.valueOf(5000.0));
        assertThat(despues.getEfectivoEnCaja()).isEqualByComparingTo(antes.getEfectivoEnCaja());
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void anularUnaVentaRevierteElIngresoSinBorrarlo() {
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto(8000, 5);

        VentaDTO venta = ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(p.getId(), BigDecimal.valueOf(1.0))), MetodoPago.EFECTIVO);

        ventaService.anularVenta(venta.getId());

        // El saldo vuelve a estar como si la venta nunca hubiera pasado
        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(despues.getSaldoTotal()).isEqualByComparingTo(antes.getSaldoTotal());

        // Pero el movimiento original sigue en el libro (no se borra, se revierte)
        assertThat(cajaService.ultimosMovimientos())
                .anySatisfy(m -> assertThat(m.getVentaId()).isEqualTo(venta.getId()));
    }

    @Test
    @WithMockUser(username = "cajera-de-prueba", roles = "EMPLEADO")
    void siElMovimientoFallaNoQuedaLaVentaNiElStockDescontado() {
        // Estado inicial: producto con 5 de stock, sin ninguna venta.
        Producto p = producto(3000, 5);
        long ventasAntes = ventaService.listarVentas().size();

        // Accion: se registra una venta pasando metodoPago=null. La venta en
        // si podria guardarse (Venta.prePersist le pone EFECTIVO por
        // default), pero MovimientoCaja no tiene ese default: su columna
        // metodo_pago es NOT NULL en la base y el insert falla al hacer
        // flush de la transaccion.
        assertThatThrownBy(() -> ventaService.registrarVenta(
                List.of(new DetalleVentaRequestDTO(p.getId(), BigDecimal.valueOf(2.0))), null))
                .isInstanceOf(RuntimeException.class);

        // Resultado esperado: NO debe existir una venta nueva, y el stock del
        // producto tiene que seguir en 5 — la venta guardada pero el
        // movimiento de caja fallido habria sido peor que ninguno de los dos.
        assertThat(ventaService.listarVentas()).hasSize((int) ventasAntes);
        Producto releido = productoRepository.findById(p.getId()).orElseThrow();
        assertThat(releido.getStock()).isEqualByComparingTo(BigDecimal.valueOf(5.0));
    }
}
