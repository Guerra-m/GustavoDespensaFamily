package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guerram.DespensaFamily.dto.CompraDTO;
import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraRequestDTO;
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
 * Comprar pagada baja el saldo de caja; comprar a cuenta no. Fase 11 del
 * mapa de cambios, mismo Harness Engineering que ventas.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class CompraConCajaTest {

    @Autowired
    private CompraService compraService;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private ProductoRepository productoRepository;

    private Producto producto() {
        return productoRepository.save(Producto.builder()
                .nombre("Producto de prueba compra+caja")
                .precioCosto(BigDecimal.valueOf(100)).precioVenta(BigDecimal.valueOf(150)).porcentajeGanancia(BigDecimal.valueOf(50))
                .stock(BigDecimal.valueOf(0)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
    }

    private CompraRequestDTO pedido(Long productoId, Boolean pagada, MetodoPago metodo) {
        CompraRequestDTO request = new CompraRequestDTO();
        request.setProveedor("Proveedor de prueba");
        request.setDetalles(List.of(new DetalleCompraRequestDTO(productoId, BigDecimal.valueOf(10.0), BigDecimal.valueOf(100.0)))); // total 1000
        request.setPagada(pagada);
        request.setMetodoPago(metodo);
        return request;
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void comprarPagadaBajaElSaldoDeCaja() {
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto();

        CompraDTO compra = compraService.registrarCompra(pedido(p.getId(), true, MetodoPago.EFECTIVO));

        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(compra.isPagada()).isTrue();
        assertThat(despues.getSaldoTotal().subtract(antes.getSaldoTotal())).isEqualByComparingTo(BigDecimal.valueOf(-1000.0));
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void comprarACuentaNoMueveLaCaja() {
        // Estado inicial: caja = X
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto();

        // Accion: comprar mercaderia sin pagarla todavia
        CompraDTO compra = compraService.registrarCompra(pedido(p.getId(), false, MetodoPago.EFECTIVO));

        // Resultado esperado: entro stock (verificado en otros tests), pero
        // la caja NO se movio. El proveedor todavia no cobro.
        EstadoCajaDTO despues = cajaService.estadoDeCaja();
        assertThat(compra.isPagada()).isFalse();
        assertThat(despues.getSaldoTotal()).isEqualByComparingTo(antes.getSaldoTotal());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void marcarPagadaDespuesGeneraElEgresoRecien(){
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto();
        CompraDTO compra = compraService.registrarCompra(pedido(p.getId(), false, MetodoPago.EFECTIVO));

        // Todavia no se movio nada
        assertThat(cajaService.estadoDeCaja().getSaldoTotal()).isEqualByComparingTo(antes.getSaldoTotal());

        // Se paga despues
        CompraDTO pagada = compraService.marcarPagada(compra.getId());

        assertThat(pagada.isPagada()).isTrue();
        assertThat(cajaService.estadoDeCaja().getSaldoTotal().subtract(antes.getSaldoTotal())).isEqualByComparingTo(BigDecimal.valueOf(-1000.0));
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void noSePuedePagarUnaCompraYaPagada() {
        Producto p = producto();
        CompraDTO compra = compraService.registrarCompra(pedido(p.getId(), true, MetodoPago.EFECTIVO));

        assertThatThrownBy(() -> compraService.marcarPagada(compra.getId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void anularUnaCompraPagadaRevierteElEgreso() {
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto();
        CompraDTO compra = compraService.registrarCompra(pedido(p.getId(), true, MetodoPago.EFECTIVO));

        compraService.anularCompra(compra.getId());

        // El saldo vuelve a estar como si la compra nunca hubiera pasado
        assertThat(cajaService.estadoDeCaja().getSaldoTotal()).isEqualByComparingTo(antes.getSaldoTotal());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void anularUnaCompraACuentaNoTocaLaCaja() {
        // Como nunca salio plata, anularla tampoco tiene que mover nada —
        // y sobre todo no tiene que fallar buscando un movimiento que no existe.
        EstadoCajaDTO antes = cajaService.estadoDeCaja();
        Producto p = producto();
        CompraDTO compra = compraService.registrarCompra(pedido(p.getId(), false, MetodoPago.EFECTIVO));

        compraService.anularCompra(compra.getId());

        assertThat(cajaService.estadoDeCaja().getSaldoTotal()).isEqualByComparingTo(antes.getSaldoTotal());
    }
}
