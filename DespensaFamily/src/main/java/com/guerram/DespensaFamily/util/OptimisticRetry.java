package com.guerram.DespensaFamily.util;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.TransactionSystemException;

import java.util.function.Supplier;

// Reintenta una operación que puede chocar por concurrencia sobre el mismo
// Producto bajo ventas/compras simultáneas (ver VentaService, CompraService).
// ConcurrencyFailureException es la superclase común de DOS casos distintos
// que hay que tratar igual acá:
//   - OptimisticLockingFailureException: el UPDATE con WHERE version=? no
//     afectó filas porque otra transacción ya cambió el producto (conflicto
//     de @Version, detectado por Hibernate).
//   - CannotAcquireLockException (deadlock real de MySQL, ErrorCode 1213):
//     el INSERT de DetalleVenta/DetalleCompra toma un lock compartido sobre
//     la fila de Producto por la FK, que choca con el UPDATE de stock de
//     otra transacción concurrente sobre el mismo producto — un deadlock
//     conocido de InnoDB con este patrón (insertar hijo con FK + actualizar
//     padre), no algo que @Version por sí solo evite. Confirmado en vivo:
//     con el retry acotado solo a OptimisticLockingFailureException, este
//     deadlock seguía llegando sin reintentar hasta el cliente.
//   - TransactionSystemException ("Could not commit JPA transaction") con
//     una causa de optimistic locking en la cadena: el UPDATE de Producto
//     (por el @Version) no se ejecuta explícitamente en el método — Hibernate
//     recién lo flushea al hacer commit, así que si falla el chequeo de
//     versión ahí, Spring lo envuelve genérico en vez de traducirlo a
//     OptimisticLockingFailureException. También confirmado en vivo: sin
//     este chequeo de causa, el conflicto llegaba como un 400 sin reintentar
//     y sin ningún detalle útil en el mensaje.
// Vive en el controller (no dentro del service) a propósito: @Transactional
// se aplica vía proxy de Spring, así que reintentar llamando al método
// transaccional "desde adentro" (this.metodo()) NO lo re-ejecutaría dentro
// de una transacción nueva — hay que volver a entrar por el proxy en cada
// intento, y el proxy solo intercepta llamadas que llegan desde afuera del
// bean (por eso el retry vive acá, un nivel arriba).
public final class OptimisticRetry {

    private static final int MAX_INTENTOS = 8;

    // Backoff con jitter entre reintentos: sin esto, dos hilos que chocaron
    // porque leyeron la misma version vuelven a leer y a chocar casi al
    // instante (la lectura del segundo intento es tan rapida que cae dentro
    // de la misma ventana de la escritura que todavia esta compitiendo).
    // Confirmado con un test de 20 ventas simultaneas sobre el mismo
    // producto (VentaConcurrenciaTest): con 3 intentos y sin espera, el
    // retry se agotaba y una venta terminaba en error real para el usuario.
    private static final long ESPERA_BASE_MS = 5;

    private OptimisticRetry() {
    }

    public static <T> T ejecutar(Supplier<T> operacion) {
        RuntimeException ultimoError = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return operacion.get();
            } catch (RuntimeException ex) {
                if (!esConflictoDeConcurrencia(ex)) throw ex; // error de negocio real, no reintentar
                ultimoError = ex;
                esperarAntesDeReintentar(intento);
            }
        }
        throw ultimoError;
    }

    private static void esperarAntesDeReintentar(int intento) {
        long jitterMs = (long) (Math.random() * ESPERA_BASE_MS * intento);
        try {
            Thread.sleep(jitterMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Recorre la cadena de causas: cubre tanto el caso directo
    // (ConcurrencyFailureException) como el caso envuelto por Spring en
    // TransactionSystemException cuando el conflicto de @Version solo se
    // detecta al hacer commit (ver comentario de arriba).
    public static boolean esConflictoDeConcurrencia(Throwable ex) {
        Throwable actual = ex;
        while (actual != null) {
            if (actual instanceof ConcurrencyFailureException) return true;
            if (actual instanceof jakarta.persistence.OptimisticLockException) return true;
            if (actual instanceof org.hibernate.StaleObjectStateException) return true;
            if (actual instanceof org.hibernate.StaleStateException) return true;
            actual = actual.getCause();
        }
        return false;
    }

    public static void ejecutar(Runnable operacion) {
        ejecutar(() -> {
            operacion.run();
            return null;
        });
    }
}
