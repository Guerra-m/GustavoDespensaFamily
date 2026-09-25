package com.guerram.DespensaFamily.service;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guerram.DespensaFamily.dto.CompraDTO;
import com.guerram.DespensaFamily.dto.CompraRequestDTO;
import com.guerram.DespensaFamily.dto.DetalleCompraRequestDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * El total de una compra se congela al registrarla, igual que Venta.total.
 *
 * <p>Antes no existia el campo: el total se recalculaba sumando
 * detalle_compra cada vez que hacia falta mostrarlo. Un total que se
 * recalcula no es un hecho historico — si el dia de manana cambia como se
 * redondea un subtotal, las compras viejas cambiarian de importe con
 * retroactividad. V3__compra_total.sql agrega la columna y hace el backfill
 * de las compras que ya existian sumando sus detalles una sola vez.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class CompraTotalTest {

    @Autowired
    private CompraService compraService;

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    @WithMockUser(username = "duenio-de-prueba")
    void elTotalQuedaCongeladoAlRegistrarLaCompra() {
        Producto uno = productoRepository.save(Producto.builder()
                .nombre("Producto uno")
                .precioCosto(BigDecimal.valueOf(50)).precioVenta(BigDecimal.valueOf(75)).porcentajeGanancia(BigDecimal.valueOf(50))
                .stock(BigDecimal.valueOf(0)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
        Producto dos = productoRepository.save(Producto.builder()
                .nombre("Producto dos")
                .precioCosto(BigDecimal.valueOf(200)).precioVenta(BigDecimal.valueOf(300)).porcentajeGanancia(BigDecimal.valueOf(50))
                .stock(BigDecimal.valueOf(0)).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        CompraRequestDTO request = new CompraRequestDTO();
        request.setProveedor("Proveedor de prueba");
        request.setDetalles(List.of(
                new DetalleCompraRequestDTO(uno.getId(), BigDecimal.valueOf(3.0), BigDecimal.valueOf(50.0)),    // 150
                new DetalleCompraRequestDTO(dos.getId(), BigDecimal.valueOf(2.0), BigDecimal.valueOf(200.0)))); // 400
        request.setMetodoPago(com.guerram.DespensaFamily.model.MetodoPago.EFECTIVO);

        CompraDTO compra = compraService.registrarCompra(request);

        assertEquals(0, compra.getTotal().compareTo(BigDecimal.valueOf(550.0)));

        // Y el mismo valor congelado al volver a leerla, no recalculado.
        CompraDTO releida = compraService.buscarPorId(compra.getId());
        assertEquals(0, releida.getTotal().compareTo(BigDecimal.valueOf(550.0)));
    }
}
