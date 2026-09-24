package com.guerram.DespensaFamily.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Puente para escanear codigos de barras desde el celular: la pantalla de
 * Ventas pide una sesion, muestra un QR con la URL de esa sesion, el
 * celular escanea con SU camara (algo que la PC de mostrador no tiene) y
 * manda cada codigo por HTTP; Ventas lo recibe en vivo por SSE y lo procesa
 * exactamente igual que si lo hubiera tipeado o leido con el lector fisico.
 *
 * <p>Todo en memoria y a proposito: es un canal efimero de un turno de
 * trabajo, no un dato del negocio que tenga que sobrevivir un reinicio ni
 * cruzar instalaciones (no hace falta Flyway ni tabla).
 *
 * <p>La sesion es un UUID al azar (122 bits) creado por un usuario ya
 * autenticado — el celular en si NO se loguea, solo conoce ese UUID (viaja
 * en la URL del QR). El riesgo de que alguien mas en la misma red local
 * adivine un UUID activo y mande un codigo falso es el mismo tipo de riesgo
 * que ya existe con el lector fisico de mostrador (cualquiera cerca podria
 * acercar un codigo raro al lector): agrega un item equivocado al carrito,
 * nunca plata ni datos de otra cuenta. Vencen solas a los 30 minutos.
 */
@Service
public class EscaneoService {

    private record Sesion(SseEmitter emitter, Instant creada) {}

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();

    public String crearSesion() {
        limpiarVencidas();
        String id = UUID.randomUUID().toString();
        sesiones.put(id, new Sesion(null, Instant.now()));
        return id;
    }

    public SseEmitter suscribirse(String sesionId) {
        if (!sesiones.containsKey(sesionId)) {
            throw new RuntimeException("Sesión de escaneo no encontrada o vencida");
        }
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        sesiones.put(sesionId, new Sesion(emitter, sesiones.get(sesionId).creada()));

        emitter.onCompletion(() -> sesiones.remove(sesionId));
        emitter.onTimeout(() -> sesiones.remove(sesionId));
        emitter.onError(e -> sesiones.remove(sesionId));

        return emitter;
    }

    public void publicarCodigo(String sesionId, String codigo) {
        Sesion sesion = sesiones.get(sesionId);
        if (sesion == null) {
            throw new RuntimeException("Sesión de escaneo no encontrada o vencida");
        }
        if (sesion.emitter() == null) {
            throw new RuntimeException("Todavía no se abrió la pantalla de Ventas para esta sesión");
        }
        try {
            sesion.emitter().send(SseEmitter.event().name("codigo").data(codigo));
        } catch (Exception e) {
            sesiones.remove(sesionId);
            throw new RuntimeException("La sesión de escaneo se cerró");
        }
    }

    private void limpiarVencidas() {
        Instant limite = Instant.now().minusSeconds(30 * 60);
        sesiones.entrySet().removeIf(e -> e.getValue().creada().isBefore(limite));
    }
}
