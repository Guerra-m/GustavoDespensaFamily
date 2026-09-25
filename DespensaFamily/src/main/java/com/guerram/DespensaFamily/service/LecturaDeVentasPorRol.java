package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.NoEncontradoException;
import com.guerram.DespensaFamily.config.PermisosDeLectura;
import com.guerram.DespensaFamily.dto.PaginaDTO;
import com.guerram.DespensaFamily.dto.VentaDTO;
import com.guerram.DespensaFamily.dto.VentaDetalleResponseDTO;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.Venta;
import com.guerram.DespensaFamily.repository.VentaDelEmpleadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DF-SEC-003: lo que ve de las ventas quien no tiene PERM_CAJA — solo las
 * suyas, del dia — y el costo de cada linea solo para quien ve costos.
 *
 * <p>Una venta ajena o de otro dia da el MISMO "Venta no encontrada" (404)
 * que una que no existe, con el mismo cuerpo: un 403 confirmaria que ese
 * numero de venta existe, y como los ids son correlativos alcanzaria con
 * recorrerlos para saber cuanto vende el negocio (AUTZ-02).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LecturaDeVentasPorRol {

    private final VentaDelEmpleadoRepository ventasDelEmpleado;

    public List<VentaDTO> deHoy(Authentication quien) {
        return ventasDelEmpleado.findByUsuarioAndFechaHoraBetweenOrderByIdAsc(
                        quien.getName(), inicioDeHoy(), finDeHoy())
                .stream()
                .map(Mapper::toDTO)
                .toList();
    }

    // DF-PERF-001: mismo filtro que deHoy(), paginado. desde/hasta del
    // pedido original se ignoran a proposito -- ver VentaController: quien
    // no tiene PERM_CAJA no puede pedir "todo el año pasado", solo hoy.
    public PaginaDTO<VentaDTO> deHoyPaginado(Authentication quien, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Venta> resultado = ventasDelEmpleado.findByUsuarioAndFechaHoraBetweenOrderByFechaHoraDesc(
                quien.getName(), inicioDeHoy(), finDeHoy(), pageable);

        return PaginaDTO.<VentaDTO>builder()
                .contenido(resultado.getContent().stream().map(Mapper::toDTO).toList())
                .totalElementos(resultado.getTotalElements())
                .totalPaginas(resultado.getTotalPages())
                .pagina(page)
                .tamanio(size)
                .build();
    }

    public VentaDTO unaDeHoy(Long id, Authentication quien) {
        return Mapper.toDTO(buscarDeHoy(id, quien));
    }

    public VentaDetalleResponseDTO detalleDeHoy(Long id, Authentication quien) {
        return segunQuien(Mapper.toVentaDetalleResponse(buscarDeHoy(id, quien)), quien);
    }

    /** Saca el costo de cada linea si quien pregunta no ve costos. */
    public VentaDetalleResponseDTO segunQuien(VentaDetalleResponseDTO detalle, Authentication quien) {
        if (!PermisosDeLectura.veCostos(quien) && detalle.getDetalles() != null) {
            detalle.getDetalles().forEach(linea -> linea.setPrecioCosto(null));
        }
        return detalle;
    }

    private Venta buscarDeHoy(Long id, Authentication quien) {
        return ventasDelEmpleado.findByIdAndUsuarioAndFechaHoraBetween(
                        id, quien.getName(), inicioDeHoy(), finDeHoy())
                .orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
    }

    // El dia del reloj de la maquina, igual que la fecha con que VentaService
    // graba cada venta (LocalDateTime.now()).
    private static LocalDateTime inicioDeHoy() {
        return LocalDate.now().atStartOfDay();
    }

    private static LocalDateTime finDeHoy() {
        return LocalDate.now().plusDays(1).atStartOfDay().minusNanos(1);
    }
}
