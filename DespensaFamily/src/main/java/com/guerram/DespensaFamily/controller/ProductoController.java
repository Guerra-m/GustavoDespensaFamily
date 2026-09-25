package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.ActualizarStockPrecioDTO;
import com.guerram.DespensaFamily.config.PermisosDeLectura;
import com.guerram.DespensaFamily.dto.ProductoDTO;
import com.guerram.DespensaFamily.dto.ProductoParaVentaDTO;
import com.guerram.DespensaFamily.util.OptimisticRetry;
import com.guerram.DespensaFamily.service.ProductoService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    // Como el DTO acá llega como JSON dentro de un multipart (no vía
    // @RequestBody), Spring nunca corre @Valid solo. Validamos a mano con el
    // mismo Validator que Spring ya expone (VAL-01: la validación tiene que
    // pasar por el servidor, no confiar en que el navegador la haga).
    private final Validator validator;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductoDTO crear(
            @RequestPart("producto") String productoJson,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen
    ) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ProductoDTO dto = mapper.readValue(productoJson, ProductoDTO.class);
        validarProducto(dto);

        return productoService.crearProducto(dto, imagen);
    }



    // DF-SEC-003: los tres GET devuelven el producto sin costo, margen ni
    // proveedor a quien no gestiona el catalogo (ver PermisosDeLectura). El
    // recorte es del servidor: "el frontend no lo muestra" no alcanza (API-04).
    @GetMapping
    public List<?> listarProductos(Authentication quien) {
        return segunQuien(productoService.listarProductos(), quien);
    }

    @GetMapping("/{id}")
    public Object buscarPorId(@PathVariable Long id, Authentication quien) {
        ProductoDTO producto = productoService.buscarPorId(id);
        return PermisosDeLectura.veCostos(quien) ? producto : ProductoParaVentaDTO.de(producto);
    }

    private static List<?> segunQuien(List<ProductoDTO> productos, Authentication quien) {
        if (PermisosDeLectura.veCostos(quien)) return productos;
        return productos.stream().map(ProductoParaVentaDTO::de).toList();
    }
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ProductoDTO actualizar(
            @PathVariable Long id,
            @RequestPart("producto") String productoJson,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen
    ) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ProductoDTO dto = mapper.readValue(productoJson, ProductoDTO.class);
        validarProducto(dto);

        return OptimisticRetry.ejecutar(() -> productoService.actualizarProducto(id, dto, imagen));
    }

    private void validarProducto(ProductoDTO dto) {
        Set<jakarta.validation.ConstraintViolation<ProductoDTO>> violaciones = validator.validate(dto);
        if (!violaciones.isEmpty()) {
            throw new ConstraintViolationException(violaciones);
        }
    }



    // Cuerpo JSON, no query params. El frontend siempre llamo a esto con PUT y
    // un JSON, mientras el backend esperaba PATCH con ?precioVenta=…: nunca
    // coincidieron, asi que el endpoint no funcionaba desde la aplicacion
    // (daba 403 por metodo no mapeado). Se deja en PATCH, que es el verbo
    // correcto para una actualizacion parcial, y se corrige el frontend.
    //
    // Con OptimisticRetry porque toca el stock de un producto que puede
    // estar vendiendose al mismo tiempo desde la otra caja.
    @PatchMapping("/{id}/stock-precio")
    public ProductoDTO actualizarStockPrecio(
            @PathVariable Long id,
            @RequestBody ActualizarStockPrecioDTO cambios
    ) {
        return OptimisticRetry.ejecutar(() -> productoService.actualizarPrecioYStock(
                id, cambios.getPrecioVenta(), cambios.getPrecioCosto(), cambios.getStock()));
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        productoService.eliminarProducto(id);
    }

    @GetMapping("/buscar")
    public List<?> buscar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String codigoBarras,
            @RequestParam(required = false) Long categoriaId,
            Authentication quien
    ) {
        return segunQuien(productoService.buscarProductos(nombre, codigoBarras, categoriaId), quien);
    }
}
