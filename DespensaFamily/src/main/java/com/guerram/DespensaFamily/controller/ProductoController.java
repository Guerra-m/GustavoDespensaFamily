package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.ProductoDTO;
import com.guerram.DespensaFamily.service.ProductoService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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



    @GetMapping
    public List<ProductoDTO> listarProductos() {
        return productoService.listarProductos();
    }

    @GetMapping("/{id}")
    public ProductoDTO buscarPorId(@PathVariable Long id) {
        return productoService.buscarPorId(id);
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

        return productoService.actualizarProducto(id, dto, imagen);
    }

    private void validarProducto(ProductoDTO dto) {
        Set<jakarta.validation.ConstraintViolation<ProductoDTO>> violaciones = validator.validate(dto);
        if (!violaciones.isEmpty()) {
            throw new ConstraintViolationException(violaciones);
        }
    }



    @PatchMapping("/{id}/stock-precio")
    public ProductoDTO actualizarStockPrecio(
            @PathVariable Long id,
            @RequestParam(required = false) Double precioVenta,
            @RequestParam(required = false) Double precioCosto,
            @RequestParam(required = false) Integer stock
    ) {
        return productoService.actualizarPrecioYStock(id, precioVenta, precioCosto, stock);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        productoService.eliminarProducto(id);
    }

    @GetMapping("/buscar")
    public List<ProductoDTO> buscar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String codigoBarras,
            @RequestParam(required = false) Long categoriaId
    ) {
        return productoService.buscarProductos(nombre, codigoBarras, categoriaId);
    }
}
