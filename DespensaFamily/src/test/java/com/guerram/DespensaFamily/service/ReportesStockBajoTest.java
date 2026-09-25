package com.guerram.DespensaFamily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.guerram.DespensaFamily.dto.ConfiguracionDTO;
import com.guerram.DespensaFamily.dto.reportes.ProductoBajoStockDTO;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * DF-BUG-008 de la auditoria: el stock bajo de Reportes usaba un umbral fijo
 * de 5 cuando no llegaba 'limite' por query param, pero el front de Reportes
 * (a diferencia de Ventas y Productos) nunca lo manda — asi que Reportes
 * terminaba mostrando el umbral de fabrica en vez del que el dueño configuro
 * en Ajustes. Con umbral 11, un producto con stock 8 tiene que aparecer.
 */
@SpringBootTest(properties = {
        "despensa.escritorio=false",
        "despensa.respaldos.al-arrancar=false"
})
class ReportesStockBajoTest {

    @Autowired
    private ReportesService reportesService;

    @Autowired
    private ConfiguracionService configuracionService;

    @Autowired
    private ProductoRepository productoRepository;

    private Producto productoConStock(String nombre, double stock) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre)
                .precioCosto(100).precioVenta(150).porcentajeGanancia(50)
                .stock(stock).tipoCantidad("UNIDAD").unidadMedida("u")
                .activo(true)
                .build());
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void sinLimiteExplicitoUsaElUmbralConfiguradoYNoElFijoDeCinco() {
        ConfiguracionDTO dto = new ConfiguracionDTO();
        dto.setUmbralStockBajo(11.0);
        configuracionService.actualizar(dto);

        Producto producto = productoConStock("DF-BUG-008 stock 8 con umbral 11", 8.0);

        List<ProductoBajoStockDTO> resultado = reportesService.productosConBajoStock(null);

        assertThat(resultado).anySatisfy(p -> assertThat(p.getId()).isEqualTo(producto.getId()));
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void conUnUmbralMenorElMismoProductoDejaDeAparecer() {
        // Triangulacion: bajando el umbral configurado a 3, un producto con
        // stock 8 no puede seguir apareciendo como "bajo stock".
        ConfiguracionDTO dto = new ConfiguracionDTO();
        dto.setUmbralStockBajo(3.0);
        configuracionService.actualizar(dto);

        Producto producto = productoConStock("DF-BUG-008 stock 8 con umbral 3", 8.0);

        List<ProductoBajoStockDTO> resultado = reportesService.productosConBajoStock(null);

        assertThat(resultado).noneSatisfy(p -> assertThat(p.getId()).isEqualTo(producto.getId()));

        // Restaurar el umbral por defecto para no afectar otros tests de la corrida.
        ConfiguracionDTO restaurar = new ConfiguracionDTO();
        restaurar.setUmbralStockBajo(10.0);
        configuracionService.actualizar(restaurar);
    }

    @Test
    @WithMockUser(username = "duenio-de-prueba", roles = "ADMIN")
    void unLimiteExplicitoSigueTeniendoPrioridadSobreElConfigurado() {
        ConfiguracionDTO dto = new ConfiguracionDTO();
        dto.setUmbralStockBajo(2.0);
        configuracionService.actualizar(dto);

        Producto producto = productoConStock("DF-BUG-008 limite explicito", 8.0);

        // Aunque el umbral configurado sea 2 (no alcanzaria), un limite
        // explicito de 11 en la llamada tiene que seguir mandando.
        List<ProductoBajoStockDTO> resultado = reportesService.productosConBajoStock(11.0);

        assertThat(resultado).anySatisfy(p -> assertThat(p.getId()).isEqualTo(producto.getId()));

        ConfiguracionDTO restaurar = new ConfiguracionDTO();
        restaurar.setUmbralStockBajo(10.0);
        configuracionService.actualizar(restaurar);
    }
}
