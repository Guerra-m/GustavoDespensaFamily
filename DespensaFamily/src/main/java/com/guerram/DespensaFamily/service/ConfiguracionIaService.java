package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.ReglaDeNegocioException;
import com.guerram.DespensaFamily.config.AppPaths;
import com.guerram.DespensaFamily.config.ClaveIaPorVariableDeEntornoException;
import com.guerram.DespensaFamily.config.EditorArchivoPropiedades;
import com.guerram.DespensaFamily.dto.ia.ClaveIaDTO;
import com.guerram.DespensaFamily.dto.ia.EstadoIaDTO;
import com.guerram.DespensaFamily.ia.ClaveIa;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Alta, cambio y baja de la clave de Gemini desde Ajustes (F5).
 *
 * <p>La clave se guarda en config.properties (mismo archivo que el secreto
 * de sesion, ver SecretoJwt) con escritura atomica (SEC-02) y se aplica en
 * caliente sobre {@link ClaveIa}, sin reiniciar el servidor. Si
 * GEMINI_API_KEY esta definida como variable de entorno de Windows, esa
 * gana siempre: ni actualizar ni quitar tocan el archivo en ese caso.
 */
@Service
public class ConfiguracionIaService {

    // Misma clave que despensa.ia.gemini.api-key en application.properties
    // y que usa el instalador para escribir en config.properties.
    public static final String CLAVE_PROPIEDAD = "despensa.ia.gemini.api-key";

    private static final int LARGO_MINIMO = 20;
    private static final int LARGO_MAXIMO = 200;

    private final ClaveIa claveIa;
    private final Path configProperties;

    @Autowired
    public ConfiguracionIaService(ClaveIa claveIa) {
        this(claveIa, Paths.get(System.getProperty(AppPaths.PROP_HOME)).resolve("config.properties"));
    }

    /** Visible para tests: permite apuntar a un config.properties temporal. */
    ConfiguracionIaService(ClaveIa claveIa, Path configProperties) {
        this.claveIa = claveIa;
        this.configProperties = configProperties;
    }

    public EstadoIaDTO obtenerEstado() {
        if (!claveIa.estaConfigurada()) {
            return new EstadoIaDTO(false, EstadoIaDTO.ORIGEN_NINGUNO);
        }
        String origen = claveIa.vieneDeVariableDeEntorno()
                ? EstadoIaDTO.ORIGEN_VARIABLE_DE_ENTORNO
                : EstadoIaDTO.ORIGEN_ARCHIVO;
        return new EstadoIaDTO(true, origen);
    }

    public EstadoIaDTO actualizar(ClaveIaDTO dto) {
        rechazarSiVieneDeVariableDeEntorno();
        String apiKey = validar(dto == null ? null : dto.getApiKey());

        try {
            EditorArchivoPropiedades.escribirValor(configProperties, CLAVE_PROPIEDAD, apiKey, cabecera());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la clave del asistente de IA", e);
        }

        claveIa.establecer(apiKey);
        return obtenerEstado();
    }

    public EstadoIaDTO quitar() {
        rechazarSiVieneDeVariableDeEntorno();

        try {
            EditorArchivoPropiedades.quitarValor(configProperties, CLAVE_PROPIEDAD, cabecera());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo quitar la clave del asistente de IA", e);
        }

        claveIa.quitar();
        return obtenerEstado();
    }

    private void rechazarSiVieneDeVariableDeEntorno() {
        if (claveIa.vieneDeVariableDeEntorno()) {
            throw new ClaveIaPorVariableDeEntornoException();
        }
    }

    // VAL-06 (inyeccion de propiedades): '=', '#' o un salto de linea en el
    // valor le permitirian a quien carga la clave agregar o pisar cualquier
    // otra propiedad del archivo, jwt.secret incluido.
    private String validar(String apiKey) {
        if (apiKey == null) {
            throw new ReglaDeNegocioException("Ingresá una clave");
        }
        String recortada = apiKey.trim();
        if (recortada.isEmpty()) {
            throw new ReglaDeNegocioException("Ingresá una clave");
        }
        if (recortada.length() < LARGO_MINIMO || recortada.length() > LARGO_MAXIMO) {
            throw new ReglaDeNegocioException(
                    "La clave tiene que tener entre " + LARGO_MINIMO + " y " + LARGO_MAXIMO + " caracteres");
        }
        if (recortada.chars().anyMatch(Character::isWhitespace)) {
            throw new ReglaDeNegocioException("La clave no puede tener espacios ni saltos de línea");
        }
        if (recortada.indexOf('=') >= 0 || recortada.indexOf('#') >= 0) {
            throw new ReglaDeNegocioException("La clave no puede contener '=' ni '#'");
        }
        return recortada;
    }

    private String cabecera() {
        return """
                # Clave de la API de Gemini para el asistente de carga de productos.
                # Se puede cargar, cambiar o quitar desde Ajustes sin reiniciar la app.
                """;
    }
}
