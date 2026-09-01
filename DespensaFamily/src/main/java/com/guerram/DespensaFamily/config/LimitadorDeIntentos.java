package com.guerram.DespensaFamily.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Freno progresivo para los intentos de login fallidos.
 *
 * <p>Antes no habia ninguno: 20 intentos en 3 segundos, sin demora. Con el
 * servidor escuchando en toda la red del local y un usuario que se llama
 * "admin", adivinar una contrasena debil era cuestion de minutos.
 *
 * <p>Sigue AUTN-03, que es explicito en <b>no</b> usar bloqueo de cuenta: "cinco
 * fallos y la cuenta queda inutilizable" convierte el control en el ataque —
 * cualquiera que sepa el nombre de usuario del dueno puede dejarlo afuera de su
 * propio negocio fallando a proposito. Lo que se hace en cambio es demorar cada
 * intento nuevo un poco mas que el anterior, con techo, y olvidar los fallos
 * viejos.
 *
 * <p>El estado vive en memoria y no en la base. Para una instalacion de un solo
 * proceso en la PC del negocio alcanza y sobra; reiniciar el programa perdona
 * los fallos acumulados, que es un costo aceptable frente a escribir en disco
 * en cada intento fallido.
 */
@Component
public class LimitadorDeIntentos {

    /** Margen para el que tipea mal la contrasena sin ser un atacante. */
    static final int FALLOS_LIBRES = 3;

    /** Techo de la espera: mas que esto ya seria bloqueo de cuenta. */
    static final Duration ESPERA_MAXIMA = Duration.ofMinutes(5);

    /** Sin fallos nuevos durante este rato, el contador vuelve a cero. */
    static final Duration VENTANA_DE_OLVIDO = Duration.ofMinutes(15);

    private final Supplier<Instant> reloj;
    private final Map<String, Historial> historiales = new ConcurrentHashMap<>();

    public LimitadorDeIntentos() {
        this(Instant::now);
    }

    LimitadorDeIntentos(Supplier<Instant> reloj) {
        this.reloj = reloj;
    }

    /**
     * Cuanto falta esperar antes de aceptar otro intento con esta clave, o
     * vacio si se puede intentar ya.
     */
    public Optional<Duration> esperaPendiente(String clave) {
        Historial historial = historiales.get(clave);
        if (historial == null) {
            return Optional.empty();
        }

        Instant ahora = reloj.get();
        if (historial.caducoPara(ahora)) {
            historiales.remove(clave);
            return Optional.empty();
        }

        Instant libreDesde = historial.ultimoFallo.plus(esperaPara(historial.fallos));
        return ahora.isBefore(libreDesde)
                ? Optional.of(Duration.between(ahora, libreDesde))
                : Optional.empty();
    }

    public void registrarFallo(String clave) {
        Instant ahora = reloj.get();
        historiales.compute(clave, (k, historial) -> {
            int previos = (historial == null || historial.caducoPara(ahora)) ? 0 : historial.fallos;
            return new Historial(previos + 1, ahora);
        });
    }

    /** Entrar bien limpia la cuenta: el usuario legitimo no arrastra castigo. */
    public void registrarExito(String clave) {
        historiales.remove(clave);
    }

    int fallosRecientes(String clave) {
        Historial historial = historiales.get(clave);
        if (historial == null || historial.caducoPara(reloj.get())) {
            return 0;
        }
        return historial.fallos;
    }

    /**
     * Duplica la espera con cada fallo pasado el margen libre: 1s, 2s, 4s, 8s…
     * hasta el techo. Crece rapido para el que prueba miles de contrasenas y
     * es imperceptible para el que se equivoco una vez.
     */
    private Duration esperaPara(int fallos) {
        int excedente = fallos - FALLOS_LIBRES;
        if (excedente <= 0) {
            return Duration.ZERO;
        }
        // El tope del exponente evita que el shift desborde con muchos fallos.
        long segundos = 1L << Math.min(excedente - 1, 20);
        Duration espera = Duration.ofSeconds(segundos);
        return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
    }

    private record Historial(int fallos, Instant ultimoFallo) {
        boolean caducoPara(Instant ahora) {
            return ultimoFallo.plus(VENTANA_DE_OLVIDO).isBefore(ahora);
        }
    }
}
