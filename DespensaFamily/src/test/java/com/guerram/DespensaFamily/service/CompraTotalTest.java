package com.guerram.DespensaFamily.service;

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
                .precioCosto(50).precioVenta(75).porcentajeGanancia(50)
                .stock(0).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
        Producto dos = productoRepository.save(Producto.builder()
                .nombre("Producto dos")
                .precioCosto(200).precioVenta(300).porcentajeGanancia(50)
                .stock(0).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());

        CompraRequestDTO request = new CompraRequestDTO();
        request.setProveedor("Proveedor de prueba");
        request.setDetalles(List.of(
                new DetalleCompraRequestDTO(uno.getId(), 3.0, 50.0),    // 150
                new DetalleCompraRequestDTO(dos.getId(), 2.0, 200.0))); // 400
        request.setMetodoPago(com.guerram.DespensaFamily.model.MetodoPago.EFECTIVO);

        CompraDTO compra = compraService.registrarCompra(request);

        assertEquals(550.0, compra.getTotal(), 0.01);

        // Y el mismo valor congelado al volver a leerla, no recalculado.
        CompraDTO releida = compraService.buscarPorId(compra.getId());
        assertEquals(550.0, releida.getTotal(), 0.01);
    }
}
