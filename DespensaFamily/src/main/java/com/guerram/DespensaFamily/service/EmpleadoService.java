package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.NoEncontradoException;
import com.guerram.DespensaFamily.dto.caja.MovimientoCajaDTO;
import com.guerram.DespensaFamily.dto.empleados.CrearEmpleadoRequestDTO;
import com.guerram.DespensaFamily.dto.empleados.DiaTrabajadoDTO;
import com.guerram.DespensaFamily.dto.empleados.EmpleadoDTO;
import com.guerram.DespensaFamily.dto.empleados.PagarEmpleadoRequestDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.DiaTrabajado;
import com.guerram.DespensaFamily.model.Empleado;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import com.guerram.DespensaFamily.repository.DiaTrabajadoRepository;
import com.guerram.DespensaFamily.repository.EmpleadoRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alcance agregado despues del mapa original (ver PLAN-INSTALADOR.md, seccion
 * "Empleados: pagos y dias trabajados"). No toca el camino de la venta.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final DiaTrabajadoRepository diaTrabajadoRepository;
    private final CajaService cajaService;

    /**
     * Crea la ficha de Empleado que corresponde a un Usuario recien creado,
     * si todavia no existe una. La llama UsuarioService.crearUsuario.
     *
     * <p>Es el motivo por el que Empleado y Usuario, aun siendo cosas
     * distintas a proposito (ver el comentario en la entidad Empleado), no
     * quedan desconectados en el uso normal: crear una cuenta de acceso para
     * alguien que va a trabajar en el local tambien lo deja disponible para
     * marcar sus dias y pagarle, sin que el dueño tenga que cargarlo dos
     * veces. Un Empleado sin cuenta sigue siendo un caso valido (se crea a
     * mano desde Empleados), esto solo cubre el camino inverso.
     */
    @Transactional
    public void vincularConUsuario(String username, String nombre) {
        if (empleadoRepository.findByUsername(username).isPresent()) {
            return;
        }
        empleadoRepository.save(Empleado.builder()
                .nombre(nombre)
                .activo(true)
                .username(username)
                .build());
    }

    @Transactional
    public EmpleadoDTO crear(CrearEmpleadoRequestDTO request) {
        Empleado empleado = Empleado.builder()
                .nombre(request.getNombre().trim())
                .activo(true)
                .username(vacioComoNull(request.getUsername()))
                .build();
        return toDTO(empleadoRepository.save(empleado));
    }

    public List<EmpleadoDTO> listar() {
        return empleadoRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional
    public void desactivar(Long id) {
        Empleado empleado = empleadoRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Empleado no encontrado"));
        empleado.setActivo(false);
        empleadoRepository.save(empleado);
    }

    /** Todos los dias marcados en el rango, de cualquier empleado — para la grilla semanal. */
    public List<DiaTrabajadoDTO> diasTrabajados(LocalDate desde, LocalDate hasta) {
        return diaTrabajadoRepository.findByFechaBetween(desde, hasta).stream()
                .map(d -> DiaTrabajadoDTO.builder()
                        .empleadoId(d.getEmpleado().getId())
                        .fecha(d.getFecha())
                        .build())
                .toList();
    }

    /**
     * Marca un dia como trabajado. Idempotente a proposito: tocar el mismo
     * casillero dos veces en la grilla no tiene que romper nada, tiene que
     * quedar igual que la primera vez.
     */
    @Transactional
    public void marcarDia(Long empleadoId, LocalDate fecha) {
        if (diaTrabajadoRepository.findByEmpleadoIdAndFecha(empleadoId, fecha).isPresent()) {
            return;
        }
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new NoEncontradoException("Empleado no encontrado"));
        diaTrabajadoRepository.save(DiaTrabajado.builder().empleado(empleado).fecha(fecha).build());
    }

    @Transactional
    public void desmarcarDia(Long empleadoId, LocalDate fecha) {
        diaTrabajadoRepository.findByEmpleadoIdAndFecha(empleadoId, fecha)
                .ifPresent(diaTrabajadoRepository::delete);
    }

    /**
     * Registra un pago: un egreso de caja categoria SUELDO, con monto libre y
     * sin periodo fijo (decision del dueño — ver PagarEmpleadoRequestDTO).
     */
    @Transactional
    public MovimientoCajaDTO pagar(Long empleadoId, PagarEmpleadoRequestDTO request) {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new NoEncontradoException("Empleado no encontrado"));

        String descripcion = (request.getDescripcion() != null && !request.getDescripcion().isBlank())
                ? request.getDescripcion()
                : "Pago a " + empleado.getNombre();

        return cajaService.registrarMovimientoComoDTO(
                TipoMovimiento.EGRESO,
                CategoriaMovimiento.SUELDO,
                request.getMonto(),
                request.getMetodoPago(),
                descripcion,
                null,
                null,
                empleadoId);
    }

    private EmpleadoDTO toDTO(Empleado e) {
        return EmpleadoDTO.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .activo(e.isActivo())
                .username(e.getUsername())
                .build();
    }

    private static String vacioComoNull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
