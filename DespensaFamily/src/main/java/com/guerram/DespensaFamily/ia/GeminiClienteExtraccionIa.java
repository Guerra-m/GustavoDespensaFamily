package com.guerram.DespensaFamily.ia;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.guerram.DespensaFamily.dto.ia.ProductoExtraidoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

// Implementacion real contra la API de Gemini (modelo gratuito de chat con
// vision, gemini-2.5-flash por defecto). Ver ClienteExtraccionIa para el
// contrato que el resto de la app conoce.
@Component
public class GeminiClienteExtraccionIa implements ClienteExtraccionIa {

    private static final Logger log = LoggerFactory.getLogger(GeminiClienteExtraccionIa.class);

    private static final String PROMPT_BASE = """
            Sos un asistente que ayuda a cargar productos de un almacen/despensa a partir de \
            texto y/o una foto (puede ser una etiqueta, una factura, una lista escrita a mano, o \
            texto suelto, o una foto de una gondola con varios productos y carteles de precio). \
            Identifica cada producto distinto y devolvé un array JSON, uno por producto, con \
            estos campos exactos: nombre, marca, presentacion (ej "2.25L", "x6"), unidad ("u" o \
            "kg"), categoriaSugerida, codigoBarras, precio (numero o null), \
            confianza ("ALTA", "MEDIA" o "BAJA").

            Reglas estrictas:
            - Si un dato NO esta visible o no estas seguro, poné null en ese campo (especialmente \
            precio y codigoBarras) y bajá "confianza" a "MEDIA" o "BAJA". NUNCA inventes un \
            precio, un codigo de barras, o cualquier otro dato que no puedas leer con certeza.
            - No agregues productos que no esten explicitamente mencionados o visibles.
            - Devolvé unicamente el array JSON, sin texto adicional.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String modelo;

    public GeminiClienteExtraccionIa(
            @Value("${despensa.ia.gemini.api-key:}") String apiKey,
            // El default sigue al que usa application.properties: cuando eran
            // distintos, correr sin el properties caia en un modelo viejo sin
            // que nada lo dijera.
            @Value("${despensa.ia.gemini.modelo:gemini-3.6-flash}") String modelo,
            @Value("${despensa.ia.gemini.timeout-segundos:45}") int timeoutSegundos,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.objectMapper = objectMapper;

        // Con timeout explicito: sin esto, una llamada que queda colgada del
        // otro lado se lleva un hilo de Tomcat de forma indefinida.
        var fabrica = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(java.time.Duration.ofSeconds(10));
        fabrica.setReadTimeout(java.time.Duration.ofSeconds(timeoutSegundos));

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(fabrica)
                .build();
    }

    public boolean estaConfigurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<ProductoExtraidoDTO> extraer(String texto, byte[] imagen, String imagenMimeType) {
        if (!estaConfigurado()) {
            throw new RuntimeException("El asistente de IA no esta configurado en este servidor");
        }

        List<Map<String, Object>> parts = new ArrayList<>();
        String prompt = PROMPT_BASE + (texto != null && !texto.isBlank() ? "\n\nTexto del usuario:\n" + texto : "");
        parts.add(Map.of("text", prompt));
        if (imagen != null) {
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", imagenMimeType,
                    "data", Base64.getEncoder().encodeToString(imagen)
            )));
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", esquemaRespuesta()
                )
        );

        JsonNode respuesta;
        try {
            respuesta = restClient.post()
                    .uri("/models/{modelo}:generateContent", modelo)
                    // Header, no query param: evita que la clave quede en logs de
                    // proxies/balanceadores que sí suelen registrar la URL completa
                    // (ver SEC-05, canales de fuga de secretos).
                    .header("x-goog-api-key", apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new RuntimeException("Se alcanzo el limite gratuito de Gemini por ahora. Probá de nuevo mas tarde o cargá el producto a mano.");
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            // Timeout o red caida: se distingue del resto para que el mensaje
            // no mande a revisar internet cuando en realidad Gemini tardo.
            log.warn("La llamada a Gemini no respondio a tiempo", ex);
            throw new RuntimeException("El asistente de IA tardó demasiado en responder. Probá de nuevo o cargá el producto a mano.");
        } catch (RestClientException ex) {
            log.warn("No se pudo llamar a la API de Gemini", ex);
            throw new RuntimeException("No se pudo conectar con el asistente de IA. Revisá la conexion a internet.");
        }

        return parsearProductos(respuesta);
    }

    private List<ProductoExtraidoDTO> parsearProductos(JsonNode respuesta) {
        try {
            String textoJson = respuesta
                    .path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
            ProductoExtraidoDTO[] productos = objectMapper.readValue(textoJson, ProductoExtraidoDTO[].class);
            return List.of(productos);
        } catch (Exception ex) {
            log.warn("Respuesta de Gemini con formato inesperado: {}", respuesta, ex);
            throw new RuntimeException("El asistente de IA devolvio una respuesta que no se pudo interpretar. Probá de nuevo.");
        }
    }

    // Fuerza la forma del JSON (JSON mode + schema de Gemini): reduce el
    // riesgo de que el modelo devuelva texto libre o campos con otro nombre,
    // no el riesgo de que invente un valor dentro de un campo (eso lo cubre
    // el prompt, no el schema).
    private Map<String, Object> esquemaRespuesta() {
        Map<String, Object> item = Map.of(
                "type", "OBJECT",
                "properties", Map.ofEntries(
                        Map.entry("nombre", Map.of("type", "STRING")),
                        Map.entry("marca", Map.of("type", "STRING", "nullable", true)),
                        Map.entry("presentacion", Map.of("type", "STRING", "nullable", true)),
                        Map.entry("unidad", Map.of("type", "STRING")),
                        Map.entry("categoriaSugerida", Map.of("type", "STRING", "nullable", true)),
                        Map.entry("codigoBarras", Map.of("type", "STRING", "nullable", true)),
                        Map.entry("precio", Map.of("type", "NUMBER", "nullable", true)),
                        Map.entry("confianza", Map.of("type", "STRING", "enum", List.of("ALTA", "MEDIA", "BAJA")))
                ),
                "required", List.of("nombre", "unidad", "confianza")
        );
        return Map.of("type", "ARRAY", "items", item);
    }
}
