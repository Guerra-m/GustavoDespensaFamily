package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.ClaveIaPorVariableDeEntornoException;
import com.guerram.DespensaFamily.dto.ia.ClaveIaDTO;
import com.guerram.DespensaFamily.dto.ia.EstadoIaDTO;
import com.guerram.DespensaFamily.ia.ClaveIa;
import com.guerram.DespensaFamily.ia.GeminiClienteExtraccionIa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Configurar la clave de Gemini desde Ajustes (F5): guardarla en
 * config.properties preservando el resto del archivo (sobre todo
 * jwt.secret), aplicarla en caliente sin reiniciar, y respetar que la
 * variable de entorno GEMINI_API_KEY gane si esta definida.
 */
class ConfiguracionIaServiceTest {

    private static final String CLAVE_VALIDA_1 = "AIzaSy-clave-de-prueba-1234567890";
    private static final String CLAVE_VALIDA_2 = "AIzaSy-otra-clave-de-prueba-9999999";

    private ConfiguracionIaService crearServicio(Path configProperties, ClaveIa claveIa) {
        return new ConfiguracionIaService(claveIa, configProperties);
    }

    @Test
    void alPrincipioNoHayClaveConfigurada(@TempDir Path carpeta) {
        var service = crearServicio(carpeta.resolve("config.properties"), new ClaveIa(""));

        EstadoIaDTO estado = service.obtenerEstado();

        assertFalse(estado.configurada());
        assertEquals(EstadoIaDTO.ORIGEN_NINGUNO, estado.origen());
    }

    @Test
    void actualizarAgregaLaClaveCuandoNoHabiaNinguna(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        var claveIa = new ClaveIa("");
        var service = crearServicio(config, claveIa);

        EstadoIaDTO estado = service.actualizar(new ClaveIaDTO(CLAVE_VALIDA_1));

        assertTrue(estado.configurada());
        assertEquals(EstadoIaDTO.ORIGEN_ARCHIVO, estado.origen());
        assertTrue(Files.readString(config).contains("despensa.ia.gemini.api-key=" + CLAVE_VALIDA_1));
    }

    @Test
    void actualizarPreservaElSecretoJwtYOtrasLineas(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "# comentario de instalacion\njwt.secret=el-secreto-de-sesion\nserver.port=9100\n");
        var service = crearServicio(config, new ClaveIa(""));

        service.actualizar(new ClaveIaDTO(CLAVE_VALIDA_1));

        String contenido = Files.readString(config);
        assertTrue(contenido.contains("jwt.secret=el-secreto-de-sesion"), "se perdio el secreto de sesion");
        assertTrue(contenido.contains("server.port=9100"), "se perdio otra propiedad");
        assertTrue(contenido.contains("despensa.ia.gemini.api-key=" + CLAVE_VALIDA_1));
    }

    @Test
    void actualizarReemplazaUnaClaveExistenteSinDuplicarla(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "despensa.ia.gemini.api-key=" + CLAVE_VALIDA_1 + "\njwt.secret=intacto\n");
        var service = crearServicio(config, new ClaveIa(CLAVE_VALIDA_1));

        service.actualizar(new ClaveIaDTO(CLAVE_VALIDA_2));

        String contenido = Files.readString(config);
        assertTrue(contenido.contains("despensa.ia.gemini.api-key=" + CLAVE_VALIDA_2));
        assertFalse(contenido.contains(CLAVE_VALIDA_1));
        assertTrue(contenido.contains("jwt.secret=intacto"));
        assertEquals(1, contenido.split("despensa\\.ia\\.gemini\\.api-key=", -1).length - 1);
    }

    @Test
    void quitarBorraLaLineaYDejaElRestoIntacto(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "despensa.ia.gemini.api-key=" + CLAVE_VALIDA_1 + "\njwt.secret=intacto\n");
        var service = crearServicio(config, new ClaveIa(CLAVE_VALIDA_1));

        EstadoIaDTO estado = service.quitar();

        assertFalse(estado.configurada());
        String contenido = Files.readString(config);
        assertFalse(contenido.contains("despensa.ia.gemini.api-key"));
        assertTrue(contenido.contains("jwt.secret=intacto"));
    }

    @Test
    void cargarQuitarYVolverACargarNoAcumulaComentarios(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "# comentario del secreto\njwt.secret=intacto\n");
        var service = crearServicio(config, new ClaveIa(""));

        service.actualizar(new ClaveIaDTO(CLAVE_VALIDA_1));
        service.quitar();
        service.actualizar(new ClaveIaDTO(CLAVE_VALIDA_2));
        service.quitar();

        assertEquals("# comentario del secreto\njwt.secret=intacto\n", Files.readString(config));
    }

    @Test
    void actualizarAplicaLaClaveEnCalienteAlClienteDeGemini(@TempDir Path carpeta) {
        var claveIa = new ClaveIa("");
        var geminiCliente = new GeminiClienteExtraccionIa(claveIa, "gemini-3.6-flash", 45,
                new tools.jackson.databind.ObjectMapper());
        var service = crearServicio(carpeta.resolve("config.properties"), claveIa);

        assertFalse(geminiCliente.estaConfigurado(), "no deberia estar configurado antes del alta");

        service.actualizar(new ClaveIaDTO(CLAVE_VALIDA_1));

        assertTrue(geminiCliente.estaConfigurado(), "el cliente tiene que ver la clave nueva sin reiniciar");
    }

    @Test
    void quitarAplicaElCambioEnCalienteAlClienteDeGemini(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        Files.writeString(config, "despensa.ia.gemini.api-key=" + CLAVE_VALIDA_1 + "\n");
        var claveIa = new ClaveIa(CLAVE_VALIDA_1);
        var geminiCliente = new GeminiClienteExtraccionIa(claveIa, "gemini-3.6-flash", 45,
                new tools.jackson.databind.ObjectMapper());
        var service = crearServicio(config, claveIa);

        assertTrue(geminiCliente.estaConfigurado());

        service.quitar();

        assertFalse(geminiCliente.estaConfigurado(), "el cliente tiene que dejar de verse configurado sin reiniciar");
    }

    @Test
    void actualizarRechazaUnaClaveConSaltoDeLinea(@TempDir Path carpeta) {
        var service = crearServicio(carpeta.resolve("config.properties"), new ClaveIa(""));
        assertThrows(RuntimeException.class,
                () -> service.actualizar(new ClaveIaDTO(CLAVE_VALIDA_1 + "\njwt.secret=hackeado")));
    }

    @Test
    void actualizarRechazaUnaClaveConIgual(@TempDir Path carpeta) {
        var service = crearServicio(carpeta.resolve("config.properties"), new ClaveIa(""));
        assertThrows(RuntimeException.class,
                () -> service.actualizar(new ClaveIaDTO("clave=inyectada-en-el-archivo-de-propiedades")));
    }

    @Test
    void actualizarRechazaUnaClaveConNumeral(@TempDir Path carpeta) {
        var service = crearServicio(carpeta.resolve("config.properties"), new ClaveIa(""));
        assertThrows(RuntimeException.class,
                () -> service.actualizar(new ClaveIaDTO("clave-de-prueba-con-numeral-#comentario")));
    }

    @Test
    void actualizarRechazaUnaClaveVacia(@TempDir Path carpeta) {
        var service = crearServicio(carpeta.resolve("config.properties"), new ClaveIa(""));
        assertThrows(RuntimeException.class, () -> service.actualizar(new ClaveIaDTO("   ")));
    }

    @Test
    void actualizarRechazaUnaClaveDemasiadoCorta(@TempDir Path carpeta) {
        var service = crearServicio(carpeta.resolve("config.properties"), new ClaveIa(""));
        assertThrows(RuntimeException.class, () -> service.actualizar(new ClaveIaDTO("corta123")));
    }

    @Test
    void actualizarRechazaUnaClaveDemasiadoLarga(@TempDir Path carpeta) {
        var service = crearServicio(carpeta.resolve("config.properties"), new ClaveIa(""));
        assertThrows(RuntimeException.class,
                () -> service.actualizar(new ClaveIaDTO("x".repeat(201))));
    }

    @Test
    void actualizarAceptaConEspaciosAlrededorYLosRecorta(@TempDir Path carpeta) throws IOException {
        Path config = carpeta.resolve("config.properties");
        var service = crearServicio(config, new ClaveIa(""));

        service.actualizar(new ClaveIaDTO("  " + CLAVE_VALIDA_1 + "  "));

        assertTrue(Files.readString(config).contains("despensa.ia.gemini.api-key=" + CLAVE_VALIDA_1));
    }

    // ------------------------------------------------------- variable de entorno

    @Test
    void actualizarRechazaSiLaClaveVieneDeUnaVariableDeEntorno(@TempDir Path carpeta) {
        ClaveIa claveIa = claveDesdeVariableDeEntorno();
        var service = crearServicio(carpeta.resolve("config.properties"), claveIa);

        assertThrows(ClaveIaPorVariableDeEntornoException.class,
                () -> service.actualizar(new ClaveIaDTO(CLAVE_VALIDA_1)));
    }

    @Test
    void quitarRechazaSiLaClaveVieneDeUnaVariableDeEntorno(@TempDir Path carpeta) {
        ClaveIa claveIa = claveDesdeVariableDeEntorno();
        var service = crearServicio(carpeta.resolve("config.properties"), claveIa);

        assertThrows(ClaveIaPorVariableDeEntornoException.class, service::quitar);
    }

    @Test
    void obtenerEstadoInformaElOrigenVariableDeEntorno(@TempDir Path carpeta) {
        ClaveIa claveIa = claveDesdeVariableDeEntorno();
        var service = crearServicio(carpeta.resolve("config.properties"), claveIa);

        EstadoIaDTO estado = service.obtenerEstado();

        assertTrue(estado.configurada());
        assertEquals(EstadoIaDTO.ORIGEN_VARIABLE_DE_ENTORNO, estado.origen());
    }

    /**
     * ClaveIa no expone un constructor publico para forzar el origen (a
     * proposito: en produccion el origen lo decide la variable de entorno
     * real, no quien llama). Se simula guardando y quitando un valor: el
     * unico camino publico es el paquete, asi que este test vive en el mismo
     * modulo que ClaveIa a traves de un helper de test dedicado.
     */
    private ClaveIa claveDesdeVariableDeEntorno() {
        return com.guerram.DespensaFamily.ia.ClaveIaTestSupport.conVariableDeEntorno(CLAVE_VALIDA_1);
    }
}
