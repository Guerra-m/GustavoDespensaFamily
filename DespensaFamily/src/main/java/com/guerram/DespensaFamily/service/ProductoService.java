package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.NoEncontradoException;
import com.guerram.DespensaFamily.config.ReglaDeNegocioException;
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
import java.math.BigDecimal;
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

    private static final String UNIDAD = "UNIDAD";
    private static final String PESO = "PESO";

    // DF-DATA-001: alta/edicion aceptaban tipoCantidad nulo, stock decimal en
    // un producto por unidad y combinaciones incoherentes (UNIDAD + "kg", el
    // caso real de "Fiambres variados"). Mapper.toEntity ya pone defaults
    // sensatos al crear, pero un tipoCantidad/unidadMedida explicito e
    // incoherente enviado por API (Postman, el asistente de IA, etc. — no
    // todo pasa por el formulario, que ya sincroniza esto en el frontend) no
    // pasaba por ningun control del lado del servidor.
    private void normalizarYValidarTipoYUnidad(Producto producto) {
        String tipo = producto.getTipoCantidad();
        if (tipo == null) tipo = UNIDAD;
        if (!UNIDAD.equals(tipo) && !PESO.equals(tipo)) {
            throw new ReglaDeNegocioException("tipoCantidad debe ser UNIDAD o PESO");
        }
        producto.setTipoCantidad(tipo);

        String unidad = producto.getUnidadMedida();
        if (UNIDAD.equals(tipo)) {
            if (unidad == null || unidad.isBlank()) unidad = "u";
            if (!"u".equals(unidad)) {
                throw new ReglaDeNegocioException(
                        "Un producto por unidad no puede tener unidad de medida '" + unidad + "'; debe ser 'u'");
            }
            // El stock de un producto por unidad se cuenta, no se pesa: 2,5
            // unidades no es un dato valido de negocio (es media unidad
            // partida, y eso solo tiene sentido para lo que se vende por
            // peso). getStock() ya viene redondeado a 3 decimales
            // (Cantidades), asi que el modulo detecta cualquier fraccion real.
            if (producto.getStock().stripTrailingZeros().scale() > 0) {
                throw new ReglaDeNegocioException("El stock debe ser un número entero para un producto por unidad");
            }
        } else {
            // PESO: cualquier unidad de peso/volumen sirve (kg, g, lt...),
            // menos "u" -eso es exactamente la incoherencia de "Fiambres
            // variados"-. Sin unidad explicita, kg es el default razonable
            // del rubro almacen.
            if (unidad == null || unidad.isBlank() || "u".equals(unidad)) unidad = "kg";
        }
        producto.setUnidadMedida(unidad);
    }

    // DF-BUG-009: sin esto, dos productos con el mismo codigo de barras
    // convivian en la base y el escaner del punto de venta elegia siempre el
    // primero (ventas.ts usaba productos.find). Ignora vacio/nulo -varios
    // productos sin codigo no chocan entre si- y, a proposito, NO filtra por
    // activo: un producto dado de baja sigue "dueño" de su codigo hasta que
    // alguien decida reusarlo a mano (ver V14__codigo_barras_unico.sql).
    private void validarCodigoBarrasUnico(Long idActual, String codigoBarras) {
        if (codigoBarras == null || codigoBarras.isBlank()) return;

        productoRepository.findByCodigoBarras(codigoBarras)
                .filter(existente -> !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ReglaDeNegocioException(
                            "Ya existe el producto '" + existente.getNombre() + "' con ese código de barras");
                });
    }

    public ProductoDTO crearProducto(ProductoDTO dto, MultipartFile imagen) throws IOException {

        Producto producto = Mapper.toEntity(dto);
        producto.setActivo(true);
        normalizarYValidarTipoYUnidad(producto);
        validarCodigoBarrasUnico(null, producto.getCodigoBarras());

        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new NoEncontradoException("Categoria no encontrada"));
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
            throw new ReglaDeNegocioException("La imagen supera el tamaño máximo permitido (8 MB)");
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
            throw new ReglaDeNegocioException("El archivo no es una imagen soportada (jpg, png, webp o gif)");
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
                .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));

        if (!producto.isActivo()) {
            throw new ReglaDeNegocioException("Producto inactivo");
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
                .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));

        // Validar el código de barras ANTES de tocar la entidad gestionada:
        // findByCodigoBarras es un SELECT, y Hibernate hace autoflush de
        // cualquier cambio pendiente antes de correr un SELECT. Si el
        // producto ya tuviera el código nuevo seteado, ese autoflush disparaba
        // la violación de constraint directo en la base (un
        // ConstraintViolationException feo) en vez de este mensaje claro.
        validarCodigoBarrasUnico(id, dto.getCodigoBarras());

        producto.setNombre(dto.getNombre());
        producto.setPrecioVenta(dto.getPrecioVenta());
        producto.setPrecioCosto(dto.getPrecioCosto());
        producto.setPorcentajeGanancia(dto.getPorcentajeGanancia());
        producto.setStock(dto.getStock());
        producto.setCodigoBarras(dto.getCodigoBarras());
        producto.setProveedor(dto.getProveedor());
        producto.setTipoCantidad(dto.getTipoCantidad());
        producto.setUnidadMedida(dto.getUnidadMedida());
        producto.setFraccionMinima(dto.getFraccionMinima() != null ? dto.getFraccionMinima() : BigDecimal.ONE);
        producto.setUnidadesPorPaquete(dto.getUnidadesPorPaquete());
        normalizarYValidarTipoYUnidad(producto);

        if (dto.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new NoEncontradoException("Categoria no encontrada"));
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
            throw new ReglaDeNegocioException("Error al guardar la imagen");
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
            BigDecimal precioVenta,
            BigDecimal precioCosto,
            BigDecimal stock
    ) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));

        if (precioVenta == null && precioCosto == null && stock == null) {
            throw new ReglaDeNegocioException("Debe enviar al menos un valor a actualizar");
        }

        if (precioVenta != null && precioVenta.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReglaDeNegocioException("El precio de venta no puede ser negativo");
        }

        if (precioCosto != null && precioCosto.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReglaDeNegocioException("El precio de costo no puede ser negativo");
        }

        if (stock != null && stock.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReglaDeNegocioException("El stock no puede ser negativo");
        }

        // DF-DATA-001: el PATCH es el otro camino (ademas de alta/edicion) por
        // el que un producto por unidad podia terminar con stock fraccionado
        // (ej. dejar 2,5 chupetines) sin pasar nunca por VentaService.
        if (stock != null && "UNIDAD".equals(producto.getTipoCantidad())
                && Cantidades.redondear(stock).stripTrailingZeros().scale() > 0) {
            throw new ReglaDeNegocioException("El stock debe ser un número entero para un producto por unidad");
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
                .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));

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
