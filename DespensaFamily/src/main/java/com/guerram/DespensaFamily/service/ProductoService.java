package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.dto.ProductoDTO;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.Categoria;
import com.guerram.DespensaFamily.model.Producto;
import com.guerram.DespensaFamily.repository.CategoriaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import com.guerram.DespensaFamily.util.Cantidades;
import com.guerram.DespensaFamily.util.TipoDeImagen;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    // Carpeta de datos de la instalacion, no una ruta fija en C:.
    @Value("${despensa.imagenes}")
    private final String carpetaImagenes;

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
    // Lista blanca de tipos de imagen soportados. Deliberadamente sin SVG:
    // un SVG puede llevar <script> embebido (UPL-08), no es un formato
    // seguro para aceptar de un usuario y servirlo después tal cual.
    private static final Map<String, String> EXTENSIONES_PERMITIDAS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    /** Tope de la foto de un producto. Coincide con el del asistente de IA. */
    private static final long TAMANO_MAXIMO_BYTES = 8L * 1024 * 1024;

    private String guardarImagen(MultipartFile imagen) throws IOException {
        final Path ruta = Paths.get(carpetaImagenes);

        if (imagen.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new RuntimeException("La imagen supera el tamaño máximo permitido (8 MB)");
        }

        // El tipo se decide por el contenido, no por el Content-Type que
        // declaro el cliente: ese lo controla quien sube el archivo (UPL-02).
        // Antes esto confiaba en el header, mientras el asistente de IA —que
        // recibe exactamente el mismo tipo de archivo— si miraba los bytes.
        byte[] contenido = imagen.getBytes();
        String tipoReal = TipoDeImagen.detectar(contenido);
        // El null se chequea antes del get: EXTENSIONES_PERMITIDAS es un
        // Map.of(), que tira NullPointerException con una clave nula en vez de
        // devolver null. Sin esto, subir un archivo que no es imagen respondia
        // 'Cannot invoke "Object.hashCode()" because "pk" is null' en lugar de
        // explicar el problema.
        String extension = tipoReal != null ? EXTENSIONES_PERMITIDAS.get(tipoReal) : null;
        if (extension == null) {
            throw new RuntimeException("El archivo no es una imagen soportada (jpg, png, webp o gif)");
        }

        // Path traversal (UPL-03): el nombre que manda el cliente NUNCA se
        // usa para la ruta física — antes `imagen.getOriginalFilename()` iba
        // directo al nombre de archivo, así que un nombre con "../" escribía
        // fuera de la carpeta esperada. El servidor genera su propio nombre;
        // si en algún momento hace falta el nombre original, se guarda aparte
        // como metadata, nunca para resolver una ruta.
        String nombreArchivo = UUID.randomUUID() + extension;

        if (!Files.exists(ruta)) {
            Files.createDirectories(ruta);
        }

        Files.write(ruta.resolve(nombreArchivo), contenido);

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
    // @Transactional: hace leer-modificar-escribir sobre una entidad con
    // @Version. Sin transaccion, el conflicto aparecia recien al flushear y
    // llegaba al usuario como un 500 sin explicacion.
    @Transactional
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
        producto.setUnidadesPorPaquete(dto.getUnidadesPorPaquete());

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
    // stock es Double y no Integer: la columna es double porque hay productos
    // por peso. Con Integer, un queso con 2,5 kg no se podia siquiera enviar
    // (daba 400), y mandar 3 lo redondeaba borrando la fraccion.
    @Transactional
    public ProductoDTO actualizarPrecioYStock(
            Long id,
            Double precioVenta,
            Double precioCosto,
            Double stock
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
        if (stock != null) producto.setStock(Cantidades.redondear(stock));

        return Mapper.toDTO(productoRepository.save(producto));
    }


    //  DELETE
    @Transactional
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
