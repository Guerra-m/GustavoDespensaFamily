package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.util.OptimisticRetry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
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

    // La clave de Gemini la fijo una variable de entorno de Windows: no se
    // puede pisar desde la app. 409 porque el pedido es valido pero choca con
    // el estado actual del servidor, no con datos invalidos del cliente.
    @ExceptionHandler(ClaveIaPorVariableDeEntornoException.class)
    public ResponseEntity<Map<String, String>> handleClaveIaPorVariableDeEntorno(ClaveIaPorVariableDeEntornoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
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

    // DF-BUG-006. Las cuatro de abajo son errores del pedido, no del
    // servidor. Antes las dos primeras (que no son RuntimeException) caian
    // al manejo por defecto de Spring, que manda el error a /error; como
    // /error no estaba permitido, el cliente recibia un 403 vacio que
    // parecia un problema de permisos. Las otras dos si llegaban a
    // handleRuntime, pero con el mensaje crudo de Jackson o de conversion
    // —nombres de clase incluidos, com.guerram...MetodoPago— (API-08).
    // Mismo formato que handleValidacion: {error, detalle}.

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBodyInvalido(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::mensajeDeCampo)
                .distinct()
                .collect(Collectors.joining("; "));
        return datosInvalidos(detalle.isEmpty() ? "Revisá los datos enviados" : detalle);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleParametroFaltante(MissingServletRequestParameterException ex) {
        return datosInvalidos("Falta el parámetro obligatorio '" + ex.getParameterName() + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleParametroConFormatoInvalido(MethodArgumentTypeMismatchException ex) {
        return datosInvalidos("El parámetro '" + ex.getName() + "' tiene un formato inválido");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleCuerpoIlegible(HttpMessageNotReadableException ex) {
        InvalidFormatException formato = causaDeTipo(ex, InvalidFormatException.class);
        if (formato != null) {
            String campo = formato.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .filter(nombre -> nombre != null)
                    .collect(Collectors.joining("."));
            String detalle = "El valor '" + formato.getValue() + "' no es válido"
                    + (campo.isEmpty() ? "" : " para '" + campo + "'");
            Class<?> tipo = formato.getTargetType();
            if (tipo != null && tipo.isEnum()) {
                detalle += ". Valores posibles: " + Arrays.stream(tipo.getEnumConstants())
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "));
            }
            return datosInvalidos(detalle);
        }
        return datosInvalidos("El cuerpo del pedido no es un JSON válido");
    }

    private static String mensajeDeCampo(FieldError error) {
        // Si el DTO trae su propio mensaje (message = "..."), ya esta en
        // castellano y se usa tal cual. Si es el de la libreria, depende del
        // idioma de la JVM ("must not be blank"): se arma uno propio.
        String plantilla = null;
        if (error.contains(ConstraintViolation.class)) {
            plantilla = error.unwrap(ConstraintViolation.class).getMessageTemplate();
        }
        boolean mensajePropio = plantilla != null && !plantilla.startsWith("{");
        if (mensajePropio) {
            return error.getDefaultMessage();
        }
        String codigo = error.getCode() == null ? "" : error.getCode();
        return switch (codigo) {
            case "NotBlank", "NotNull", "NotEmpty" -> "El campo '" + error.getField() + "' es obligatorio";
            default -> "El campo '" + error.getField() + "' no es válido";
        };
    }

    private static <T extends Throwable> T causaDeTipo(Throwable ex, Class<T> tipo) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (tipo.isInstance(t)) {
                return tipo.cast(t);
            }
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> datosInvalidos(String detalle) {
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
        // Ningun mensaje de negocio se mapea a 401 (DF-BUG-007). El 401
        // significa "no hay sesion valida" y el frontend, ante cualquier
        // 401, borra la sesion y manda al login: "Contraseña actual
        // incorrecta" en Mi Perfil sacaba al usuario sin mostrarle nada. El
        // login fallido tiene su propio 401 (CredencialesInvalidasException),
        // y el interceptor lo exceptua a proposito.
        if (m.contains("no esta configurado") || m.contains("no se pudo conectar")
                || m.contains("limite gratuito") || m.contains("tardó demasiado")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
