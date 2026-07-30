package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.ProductoDTO;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.Categoria;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.CategoriaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    public ProductoDTO crearProducto(ProductoDTO dto, MultipartFile imagen) throws IOException {

        Producto producto = Mapper.toEntity(dto);
        producto.setActivo(true);

        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));
            producto.setCategoria(categoria);
        }

        //
        if (imagen != null && !imagen.isEmpty()) {
            String nombreImagen = guardarImagen(imagen);
            producto.setImagenUrl(nombreImagen);
        } else {
            producto.setImagenUrl(null);
        }

        productoRepository.save(producto);
        return Mapper.toDTO(producto);
    }
    private String guardarImagen(MultipartFile imagen) throws IOException {
         final Path ruta = Paths.get("C:/uploads/productos");


        if (!imagen.getContentType().startsWith("image/")) {
            throw new RuntimeException("El archivo no es una imagen");
        }

        String nombreArchivo = System.currentTimeMillis() + "_" + imagen.getOriginalFilename();


        if (!Files.exists(ruta)) {
            Files.createDirectories(ruta);
        }

        Files.copy(imagen.getInputStream(), ruta.resolve(nombreArchivo));

        return nombreArchivo;
    }


    public List<ProductoDTO> listarProductos() {
        return productoRepository.findByActivoTrue()
                .stream()
                .map(Mapper::toDTO)
                .toList();
    }

    public ProductoDTO buscarPorId(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (!producto.isActivo()) {
            throw new RuntimeException("Producto inactivo");
        }

        return Mapper.toDTO(producto);
    }

    //  UPDATE COMPLETO
    public ProductoDTO actualizarProducto(Long id, ProductoDTO dto, MultipartFile imagen) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        producto.setNombre(dto.getNombre());
        producto.setPrecioVenta(dto.getPrecioVenta());
        producto.setPrecioCosto(dto.getPrecioCosto());
        producto.setPorcentajeGanancia(dto.getPorcentajeGanancia());
        producto.setStock(dto.getStock());
        producto.setCodigoBarras(dto.getCodigoBarras());
        producto.setProveedor(dto.getProveedor());
        producto.setTipoCantidad(dto.getTipoCantidad() != null ? dto.getTipoCantidad() : "UNIDAD");
        producto.setUnidadMedida(dto.getUnidadMedida() != null ? dto.getUnidadMedida() : "u");
        producto.setFraccionMinima(dto.getFraccionMinima() != null ? dto.getFraccionMinima() : 1.0);

        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));
            producto.setCategoria(categoria);
        } else {
            producto.setCategoria(null);
        }

        // ✅ IMAGEN OPCIONAL (CLAVE)
        try {
            if (imagen != null && !imagen.isEmpty()) {
                String nombreImagen = guardarImagen(imagen);
                producto.setImagenUrl(nombreImagen);
            }
            // si imagen == null → NO se toca la imagen actual
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar la imagen", e);
        }

        return Mapper.toDTO(productoRepository.save(producto));
    }


    //  PATCH precio + stock
    public ProductoDTO actualizarPrecioYStock(
            Long id,
            Double precioVenta,
            Double precioCosto,
            Integer stock
    ) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (precioVenta == null && precioCosto == null && stock == null) {
            throw new RuntimeException("Debe enviar al menos un valor a actualizar");
        }

        if (precioVenta != null && precioVenta < 0) {
            throw new RuntimeException("El precio de venta no puede ser negativo");
        }

        if (precioCosto != null && precioCosto < 0) {
            throw new RuntimeException("El precio de costo no puede ser negativo");
        }

        if (stock != null && stock < 0) {
            throw new RuntimeException("El stock no puede ser negativo");
        }

        if (precioVenta != null) producto.setPrecioVenta(precioVenta);
        if (precioCosto != null) producto.setPrecioCosto(precioCosto);
        if (stock != null) producto.setStock(stock);

        return Mapper.toDTO(productoRepository.save(producto));
    }


    //  DELETE
    public void eliminarProducto(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        producto.setActivo(false); // borrado lógico
        productoRepository.save(producto);
    }


    //  BUSCAR COMBINADO
    public List<ProductoDTO> buscarProductos(
            String nombre,
            String codigoBarras,
            Long categoriaId
    ) {
        String nombreFiltro = nombre != null ? nombre.toLowerCase() : "";
        String codigoFiltro = codigoBarras != null ? codigoBarras : "";

        List<Producto> productos = productoRepository.findByActivoTrue()
                .stream()
                .filter(p -> {

                    boolean coincideNombre =
                            nombreFiltro.isEmpty() ||
                                    (p.getNombre() != null &&
                                            p.getNombre().toLowerCase().contains(nombreFiltro));

                    boolean coincideCodigo =
                            codigoFiltro.isEmpty() ||
                                    (p.getCodigoBarras() != null &&
                                            p.getCodigoBarras().contains(codigoFiltro));

                    boolean coincideCategoria =
                            categoriaId == null ||
                                    (p.getCategoria() != null &&
                                            p.getCategoria().getId().equals(categoriaId));

                    return coincideNombre && coincideCodigo && coincideCategoria;
                })
                .toList();

        return productos.stream()
                .map(Mapper::toDTO)
                .toList();
    }


}
