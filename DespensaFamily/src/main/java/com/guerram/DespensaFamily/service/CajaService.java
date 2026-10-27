package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.NoEncontradoException;
import com.guerram.DespensaFamily.dto.caja.ArqueoRequestDTO;
import com.guerram.DespensaFamily.dto.caja.ArqueoResultadoDTO;
import com.guerram.DespensaFamily.dto.caja.EstadoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.MovimientoCajaDTO;
import com.guerram.DespensaFamily.dto.caja.RegistrarMovimientoRequestDTO;
import com.guerram.DespensaFamily.model.CategoriaMovimiento;
import com.guerram.DespensaFamily.model.MetodoPago;
import com.guerram.DespensaFamily.model.MovimientoCaja;
import com.guerram.DespensaFamily.model.TipoMovimiento;
import com.guerram.DespensaFamily.repository.MovimientoCajaRepository;
import com.guerram.DespensaFamily.util.Dinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El libro de movimientos: registrar, revertir, y contestar cuanto hay.
 *
 * <p>No hay apertura ni cierre de caja (ver seccion 6 del mapa de cambios,
 * decision explicita de no agregarlo). El saldo es historico completo desde
 * siempre, no de una sesion — mismo criterio que ya usa el stock de un
 * producto, que tampoco "abre" y "cierra" por dia.
 */
@Service
@RequiredArgsConstructor
public class CajaService {

    /** Categorias que sólo se cargan a mano. VENTA y COMPRA las genera el flujo propio. */
    private static final Set<CategoriaMovimiento> CATEGORIAS_MANUALES =
            EnumSet.of(CategoriaMovimiento.GASTO, CategoriaMovimiento.RETIRO,
                    CategoriaMovimiento.APORTE, CategoriaMovimiento.AJUSTE);

    private final MovimientoCajaRepository movimientoCajaRepository;

    /**
     * Uso interno: lo llaman VentaService y CompraService dentro de su propia
     * transaccion, para que el movimiento se guarde junto con la venta o la
     * compra como una sola operacion atomica (ver Fase 10/11 del mapa). Si
     * esto falla, la venta tampoco se guarda.
     */
    // @Transactional tambien aca: la llamada interna de abajo es una
    // auto-invocacion (this.registrarMovimiento(...)), y el proxy de Spring
    // que hace funcionar @Transactional no intercepta llamadas de un metodo a
    // otro DENTRO del mismo bean. Sin esto, quien entra por este overload
    // corre sin transaccion activa (mismo bug que ya aparecio en
    // VentaService.registrarVenta — ver el comentario ahi).
    @Transactional
    public MovimientoCaja registrarMovimiento(
            TipoMovimiento tipo,
            CategoriaMovimiento categoria,
            BigDecimal monto,
            MetodoPago metodoPago,
            String descripcion,
            Long ventaId,
            Long compraId) {
        return registrarMovimiento(tipo, categoria, monto, metodoPago, descripcion, ventaId, compraId, null);
    }

    /** Version completa, con a que empleado se le pago (categoria SUELDO). */
    @Transactional
    public MovimientoCaja registrarMovimiento(
            TipoMovimiento tipo,
            CategoriaMovimiento categoria,
            BigDecimal monto,
            MetodoPago metodoPago,
            String descripcion,
            Long ventaId,
            Long compraId,
            Long empleadoId) {

        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto tiene que ser mayor a cero");
        }

        // Efecto colateral encontrado corriendo la suite completa para
        // DF-PERF-002: con miles de filas en el libro, algunas con importes
        // que vienen de un porcentaje (ganancia, descuentos) y no caen justo
        // en el centavo, MovimientoCajaRepository.saldoActual arrastraba
        // error de precision de double hasta hacerse visible en una
        // diferencia de saldo (9999.999999999998 en vez de 10000.0).
        // Redondear ACA, al guardar, evita que ese ruido entre al libro sea
        // cual sea el origen del movimiento (venta, compra, o carga manual);
        // arqueo ya hacia lo mismo, esto lo generaliza.
        MovimientoCaja movimiento = MovimientoCaja.builder()
                .fechaHora(LocalDateTime.now())
                .tipo(tipo)
                .categoria(categoria)
                .monto(Dinero.redondear(monto))
                .metodoPago(metodoPago)
                .descripcion(descripcion)
                .usuario(usuarioActual())
                .ventaId(ventaId)
                .compraId(compraId)
                .empleadoId(empleadoId)
                .build();

        return movimientoCajaRepository.save(movimiento);
    }

    /** Como {@link #registrarMovimiento}, pero devuelve el DTO — lo usa
     * EmpleadoService.pagar para no reconstruir el mapeo a mano. */
    @Transactional
    public MovimientoCajaDTO registrarMovimientoComoDTO(
            TipoMovimiento tipo, CategoriaMovimiento categoria, BigDecimal monto, MetodoPago metodoPago,
            String descripcion, Long ventaId, Long compraId, Long empleadoId) {
        return toDTO(registrarMovimiento(
                tipo, categoria, monto, metodoPago, descripcion, ventaId, compraId, empleadoId));
    }

    /** La carga manual: gasto, retiro, aporte o ajuste. Pide ADMIN (ver SecurityConfig). */
    @Transactional
    public MovimientoCajaDTO registrarMovimientoManual(RegistrarMovimientoRequestDTO dto) {
        if (CATEGORIAS_MANUALES.contains(dto.getCategoria())
                && (dto.getDescripcion() == null || dto.getDescripcion().isBlank())) {
            throw new IllegalArgumentException(
                    "Los movimientos de " + dto.getCategoria() + " necesitan una descripción");
        }
        if (!CATEGORIAS_MANUALES.contains(dto.getCategoria())) {
            // VENTA y COMPRA solo las genera el flujo propio: cargarlas a mano
            // rompe la trazabilidad de ventaId/compraId que ese flujo arma.
            throw new IllegalArgumentException(
                    dto.getCategoria() + " no se carga a mano: sale del registro de la venta o la compra");
        }

        MovimientoCaja movimiento = registrarMovimiento(
                dto.getTipo(), dto.getCategoria(), dto.getMonto(), dto.getMetodoPago(),
                dto.getDescripcion(), null, null);

        return toDTO(movimiento);
    }

    /**
     * Revierte un movimiento con uno nuevo de signo contrario. El original
     * NO se toca: quedan los dos, y sumados netean a cero. Pide ADMIN.
     */
    @Transactional
    public MovimientoCajaDTO revertirMovimiento(Long id) {
        MovimientoCaja original = movimientoCajaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Movimiento no encontrado"));

        if (yaFueRevertido(id)) {
            throw new IllegalStateException("Este movimiento ya fue revertido");
        }
        if (original.getRevierteA() != null) {
            throw new IllegalStateException("No se puede revertir una reversion");
        }

        TipoMovimiento tipoContrario = original.getTipo() == TipoMovimiento.INGRESO
                ? TipoMovimiento.EGRESO
                : TipoMovimiento.INGRESO;

        MovimientoCaja reversion = MovimientoCaja.builder()
                .fechaHora(LocalDateTime.now())
                .tipo(tipoContrario)
                .categoria(original.getCategoria())
                .monto(original.getMonto())
                .metodoPago(original.getMetodoPago())
                .descripcion("Reversión de #" + original.getId()
                        + (original.getDescripcion() != null ? ": " + original.getDescripcion() : ""))
                .usuario(usuarioActual())
                .ventaId(original.getVentaId())
                .compraId(original.getCompraId())
                .empleadoId(original.getEmpleadoId())
                .revierteA(original.getId())
                .build();

        return toDTO(movimientoCajaRepository.save(reversion));
    }

    public boolean yaFueRevertido(Long movimientoId) {
        return movimientoCajaRepository.existsByRevierteA(movimientoId);
    }

    /**
     * Cuenta el cajón y registra la diferencia contra lo que la caja
     * esperaba, como un movimiento AJUSTE más — sin apertura ni cierre de
     * sesión (decisión explícita, ver sección 6 del mapa de cambios: en un
     * local de una o dos personas, una máquina de estados de apertura/cierre
     * puede terminar bloqueando el mostrador).
     *
     * <p>Se compara solo contra el EFECTIVO: es lo único que se puede contar
     * a mano. Lo cobrado con tarjeta o transferencia no está en el cajón.
     */
    @Transactional
    public ArqueoResultadoDTO registrarArqueo(ArqueoRequestDTO request) {
        // Un cajon no puede tener menos que nada. Se conto "-99999" en la
        // auditoria del 02/09/2026 y quedo grabado un egreso de ajuste de
        // $99.999 (F-12): un signo de mas mal tipeado no puede terminar en un
        // faltante enorme escrito en el libro.
        //
        // El control va aca y no como anotacion en el DTO por una razon
        // concreta: hoy toda validacion de @Valid se le devuelve al usuario
        // como un 403 vacio (F-07, todavia sin corregir), asi que el mensaje
        // no llegaria. Desde el service llega como 400 con texto. Cuando F-07
        // este arreglado, conviene ademas anotar el DTO.
        if (request.getEfectivoContado() == null || request.getEfectivoContado().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El efectivo contado no puede ser negativo");
        }

        BigDecimal esperado = movimientoCajaRepository.saldoActual(MetodoPago.EFECTIVO);
        BigDecimal contado = Dinero.redondear(request.getEfectivoContado());
        BigDecimal diferencia = Dinero.redondear(contado.subtract(esperado));

        String descripcion = (request.getDescripcion() != null && !request.getDescripcion().isBlank())
                ? request.getDescripcion()
                : String.format("Arqueo: contado %.2f, esperado %.2f", contado, esperado);

        // Diferencia real y no exactamente cero: da igual, no hay nada que
        // ajustar en el saldo. Comparar contra 0.0 a secas con doubles es
        // fragil, pero los dos lados ya pasaron por Dinero.redondear
        // (centavo), asi que la resta de dos valores redondeados a 2
        // decimales es exacta.
        //
        // DF-UX-006 de la auditoria del 23/09: antes, un arqueo exacto no
        // dejaba NINGUN rastro -- si alguien conto el cajon y daba perfecto,
        // no habia forma de saberlo despues mirando el historial. Ahora se
        // guarda igual, con monto 0 (no mueve el saldo: el signo que le pongo
        // no importa a la suma) y una categoria y descripcion que dejan claro
        // que fue un conteo sin diferencia, no un ajuste real.
        MovimientoCaja ajuste = diferencia.compareTo(BigDecimal.ZERO) == 0
                ? registrarConstanciaDeArqueoExacto(descripcion)
                : registrarMovimiento(
                        diferencia.compareTo(BigDecimal.ZERO) > 0 ? TipoMovimiento.INGRESO : TipoMovimiento.EGRESO,
                        CategoriaMovimiento.AJUSTE,
                        diferencia.abs(),
                        MetodoPago.EFECTIVO,
                        descripcion,
                        null,
                        null);

        return ArqueoResultadoDTO.builder()
                .efectivoEsperado(esperado)
                .efectivoContado(contado)
                .diferencia(diferencia)
                .movimiento(toDTO(ajuste))
                .build();
    }

    /**
     * Un arqueo sin diferencia: monto 0 a proposito (no puede mover el saldo
     * ni para arriba ni para abajo), por eso pasa por afuera de
     * {@link #registrarMovimiento}, que rechaza monto <= 0 -- esa regla es
     * correcta para cualquier carga manual real, pero esta constancia no es
     * un movimiento de plata, es la prueba de que alguien conto el cajon.
     */
    @Transactional
    protected MovimientoCaja registrarConstanciaDeArqueoExacto(String descripcion) {
        MovimientoCaja constancia = MovimientoCaja.builder()
                .fechaHora(LocalDateTime.now())
                .tipo(TipoMovimiento.INGRESO)
                .categoria(CategoriaMovimiento.AJUSTE)
                .monto(BigDecimal.ZERO.setScale(Dinero.ESCALA))
                .metodoPago(MetodoPago.EFECTIVO)
                .descripcion(descripcion)
                .usuario(usuarioActual())
                .build();

        return movimientoCajaRepository.save(constancia);
    }

    // @Transactional(readOnly = true): son 6 consultas separadas (4 saldos +
    // 2 totales de hoy). Sin una transaccion que las agrupe, cada una puede
    // tomar una conexion distinta del pool y ver un instante distinto de la
    // base si algo mas esta escribiendo al mismo tiempo — se vio como fallas
    // intermitentes (1 de cada 6 corridas) en tests que miden el saldo antes
    // y despues de una accion. readOnly=true ademas evita el overhead de
    // trackear cambios en una consulta que no escribe nada.
    @Transactional(readOnly = true)
    public EstadoCajaDTO estadoDeCaja() {
        // Los saldos ya no miran el reloj (ver saldoActual en el repositorio):
        // son la suma del libro entero. Las fechas de abajo siguen haciendo
        // falta, pero solo para acotar los totales DEL DIA, que si son una
        // pregunta sobre un rango.
        LocalDateTime inicioDeHoy = LocalDate.now().atStartOfDay();
        LocalDateTime finDeHoy = LocalDate.now().atTime(23, 59, 59, 999_999_999);

        BigDecimal efectivo = movimientoCajaRepository.saldoActual(MetodoPago.EFECTIVO);
        BigDecimal tarjeta = movimientoCajaRepository.saldoActual(MetodoPago.TARJETA);
        BigDecimal transferencia = movimientoCajaRepository.saldoActual(MetodoPago.TRANSFERENCIA);
        BigDecimal otro = movimientoCajaRepository.saldoActual(MetodoPago.OTRO);
        BigDecimal electronico = tarjeta.add(transferencia).add(otro);

        BigDecimal ingresosHoy = movimientoCajaRepository.totalIngresosEntreFechas(inicioDeHoy, finDeHoy);
        // DF-UX-006: "egresos de hoy" ya no mezcla anulaciones (revertir una
        // venta genera un EGRESO de categoria VENTA) con gastos reales.
        BigDecimal egresosHoy = movimientoCajaRepository.totalEgresosRealesEntreFechas(inicioDeHoy, finDeHoy);
        BigDecimal anulacionesHoy = movimientoCajaRepository.totalAnulacionesEntreFechas(inicioDeHoy, finDeHoy);
        // totalNetoPorCategoriaEntreFechas suma EGRESO positivo e INGRESO
        // negativo; una venta es normalmente INGRESO, asi que el resultado da
        // el neto en negativo. Invertido queda "cuanto se vendio de verdad
        // hoy", ya descontando la venta que se haya anulado en el dia.
        BigDecimal ventasNetasHoy = movimientoCajaRepository
                .totalNetoPorCategoriaEntreFechas(CategoriaMovimiento.VENTA, inicioDeHoy, finDeHoy)
                .negate();

        return EstadoCajaDTO.builder()
                .saldoTotal(efectivo.add(electronico))
                .efectivoEnCaja(efectivo)
                .saldoElectronico(electronico)
                .ingresosDeHoy(ingresosHoy)
                .egresosDeHoy(egresosHoy)
                .resultadoDeHoy(ingresosHoy.subtract(egresosHoy))
                .ventasNetasDeHoy(ventasNetasHoy)
                .anulacionesDeHoy(anulacionesHoy)
                .build();
    }

    public List<MovimientoCajaDTO> ultimosMovimientos() {
        return toDTOs(movimientoCajaRepository.findTop20ByOrderByFechaHoraDesc());
    }

    public List<MovimientoCajaDTO> listarMovimientos(LocalDate desde, LocalDate hasta) {
        return toDTOs(movimientoCajaRepository.findByFechaHoraBetweenOrderByFechaHoraDesc(
                desde.atStartOfDay(), hasta.atTime(23, 59, 59, 999_999_999)));
    }

    /** Una sola consulta para saber cuales del lote ya fueron revertidos, en vez de una por fila. */
    private List<MovimientoCajaDTO> toDTOs(List<MovimientoCaja> movimientos) {
        List<Long> ids = movimientos.stream().map(MovimientoCaja::getId).toList();
        Set<Long> revertidos = ids.isEmpty()
                ? Set.of()
                : Set.copyOf(movimientoCajaRepository.idsYaRevertidosEntre(ids));

        return movimientos.stream().map(m -> toDTO(m, revertidos.contains(m.getId()))).toList();
    }

    private MovimientoCajaDTO toDTO(MovimientoCaja m) {
        return toDTO(m, m.getRevierteA() == null && yaFueRevertido(m.getId()));
    }

    private MovimientoCajaDTO toDTO(MovimientoCaja m, boolean revertido) {
        return MovimientoCajaDTO.builder()
                .id(m.getId())
                .fechaHora(m.getFechaHora())
                .tipo(m.getTipo())
                .categoria(m.getCategoria())
                .monto(m.getMonto())
                .metodoPago(m.getMetodoPago())
                .descripcion(m.getDescripcion())
                .usuario(m.getUsuario())
                .ventaId(m.getVentaId())
                .compraId(m.getCompraId())
                .empleadoId(m.getEmpleadoId())
                .revierteA(m.getRevierteA())
                .revertido(revertido)
                .build();
    }

    private String usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
