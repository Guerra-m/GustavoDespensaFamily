package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.util.OptimisticRetry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Antes de este handler, cualquier RuntimeException de un service (todos usan
// RuntimeException con mensaje en español, no hay una jerarquía de excepciones
// propia) llegaba sin manejar hasta Tomcat, que devolvía su página de error
// cruda (o, bajo carga concurrente, un 403 vacío engañoso). Esto le da al
// frontend una respuesta JSON consistente para mostrar.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Reintentos agotados (ver OptimisticRetry — cubre tanto conflicto de
    // @Version como deadlock real de MySQL por el lock de FK): el cliente
    // puede reintentar la operación completa.
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String, String>> handleConflictoDeStock(ConcurrencyFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error",
                        "No se pudo completar la operación: el stock fue modificado por otra venta o compra al mismo tiempo. Reintentá."));
    }

    // "Could not commit JPA transaction": Spring envuelve acá cualquier
    // fallo detectado recién al hacer commit, incluido un conflicto de
    // @Version que Hibernate solo descubre al flushear el UPDATE de
    // Producto (ver OptimisticRetry.esConflictoDeConcurrencia). Si la causa
    // real es de concurrencia, mismo tratamiento que arriba (409). Si NO lo
    // es, es un error genuino e inesperado — ahí sí logueamos el stack
    // trace completo, porque antes esto se perdía en silencio.
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Map<String, String>> handleTransactionSystem(TransactionSystemException ex) {
        if (OptimisticRetry.esConflictoDeConcurrencia(ex)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error",
                            "No se pudo completar la operación: el stock fue modificado por otra venta o compra al mismo tiempo. Reintentá."));
        }
        log.error("Fallo no reconocido al hacer commit de una transacción", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno del servidor"));
    }

    // Login fallido: siempre 401 y siempre el mismo cuerpo, exista o no el
    // usuario. Antes esto pasaba por la heuristica de mensajes de abajo, que
    // devolvia 404 para "Usuario no encontrado" y 401 para "Contraseña
    // incorrecta" — la diferencia sola alcanzaba para enumerar cuentas.
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, String>> handleCredenciales(CredencialesInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    // 429 con Retry-After: es la respuesta estandar para "esperá", y le dice
    // al frontend cuanto sin que tenga que interpretar el texto del mensaje.
    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<Map<String, String>> handleDemasiadosIntentos(DemasiadosIntentosException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(Math.max(1, ex.getEspera().toSeconds())))
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleValidacion(ConstraintViolationException ex) {
        String detalle = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Datos inválidos");
        body.put("detalle", detalle);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        HttpStatus status = resolverStatus(ex.getMessage());
        // Solo logueamos cuando NO se pudo mapear a un error de negocio
        // conocido (400 por defecto sin match de la heurística) — evita
        // llenar el log con "Stock insuficiente" y similares, que son
        // validaciones esperadas, no bugs.
        if (status == HttpStatus.BAD_REQUEST && ex.getMessage() == null) {
            log.warn("RuntimeException sin mensaje llegó al handler genérico", ex);
        }
        return ResponseEntity.status(status)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Error inesperado"));
    }

    // Heurística best-effort sobre el mensaje (en español, como ya usan todos
    // los service de este proyecto) para no devolver siempre 400 a secas.
    private HttpStatus resolverStatus(String mensaje) {
        if (mensaje == null) return HttpStatus.BAD_REQUEST;
        String m = mensaje.toLowerCase();
        if (m.contains("no encontrado") || m.contains("no encontrada")) return HttpStatus.NOT_FOUND;
        if (m.contains("incorrecta") || m.contains("incorrecto")) return HttpStatus.UNAUTHORIZED;
        if (m.contains("no esta configurado") || m.contains("no se pudo conectar")
                || m.contains("limite gratuito") || m.contains("tardó demasiado")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
