package com.guerram.DespensaFamily.ia;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holder mutable de la clave de la API de Gemini.
 *
 * <p>Antes la clave se inyectaba una unica vez en el constructor de
 * {@link GeminiClienteExtraccionIa} via {@code @Value}: cambiarla exigia
 * reiniciar el servidor. Este bean singleton guarda el valor en un
 * {@link AtomicReference} para que ConfiguracionIaService pueda cambiarlo en
 * caliente desde Ajustes y el cliente de Gemini lo lea siempre actualizado.
 *
 * <p>Si la variable de entorno de Windows {@code GEMINI_API_KEY} esta
 * definida, esa gana siempre sobre lo que diga config.properties: no se
 * puede pisar desde la app (ver ConfiguracionIaService, que responde 409 si
 * se intenta).
 */
@Component
public class ClaveIa {

    private final AtomicReference<String> valor;
    private final boolean vieneDeVariableDeEntorno;

    @Autowired
    public ClaveIa(@Value("${despensa.ia.gemini.api-key:}") String valorResuelto) {
        this(valorResuelto, tieneValor(System.getenv("GEMINI_API_KEY")));
    }

    /** Para tests: fuerza el origen sin depender de la variable de entorno real de la maquina. */
    ClaveIa(String valorInicial, boolean vieneDeVariableDeEntorno) {
        this.vieneDeVariableDeEntorno = vieneDeVariableDeEntorno;
        String variableDeEntorno = System.getenv("GEMINI_API_KEY");
        String inicial = vieneDeVariableDeEntorno && tieneValor(variableDeEntorno)
                ? variableDeEntorno.trim()
                : normalizar(valorInicial);
        this.valor = new AtomicReference<>(inicial);
    }

    public String obtener() {
        return valor.get();
    }

    public void establecer(String nuevaClave) {
        valor.set(normalizar(nuevaClave));
    }

    public void quitar() {
        valor.set("");
    }

    public boolean estaConfigurada() {
        return !valor.get().isBlank();
    }

    public boolean vieneDeVariableDeEntorno() {
        return vieneDeVariableDeEntorno;
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static boolean tieneValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
