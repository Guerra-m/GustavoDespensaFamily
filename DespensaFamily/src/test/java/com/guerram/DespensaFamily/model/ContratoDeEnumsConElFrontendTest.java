package com.guerram.DespensaFamily.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Los enums que viajan al navegador son un contrato, y este test es lo que lo
 * hace cumplir.
 *
 * <p>Nace de un bug real (F-27 de la auditoria del 02/09/2026): se agrego
 * SUELDO a {@link CategoriaMovimiento} y nadie lo agrego al tipo del frontend,
 * que quedo con seis valores contra siete. El mapa de etiquetas de la pantalla
 * de Caja esta tipado como {@code Record<CategoriaMovimiento, string>}, asi que
 * en teoria el compilador de TypeScript tendria que haberlo exigido — pero el
 * que estaba incompleto era el propio tipo, y el compilador no puede pedir un
 * valor que nadie declaro. Resultado: la columna Categoria mostraba
 * {@code undefined} en cada pago de sueldo, en una pantalla de plata.
 *
 * <p>Por eso el control vive de este lado: acá es donde se agrega el valor
 * nuevo, y acá es donde tiene que sonar la alarma. Del lado del frontend, en
 * cuanto el tipo esta completo, el {@code Record} obliga a completar las
 * etiquetas: los dos controles juntos cierran el circuito.
 *
 * <p>Si el frontend no esta presente (alguien clono solo el backend), el test
 * se saltea en vez de fallar: no es una regresion del backend.
 */
class ContratoDeEnumsConElFrontendTest {

    /** Desde DespensaFamily/, el frontend es un hermano. */
    private static final Path MODELOS_DEL_FRONT =
            Paths.get("..", "FrontDespensaFamily", "src", "models");

    private record Contrato(Class<? extends Enum<?>> enumJava, String archivoTs) {
        @Override
        public String toString() {
            return enumJava.getSimpleName() + " ↔ " + archivoTs;
        }
    }

    static List<Contrato> contratos() {
        return List.of(
                new Contrato(CategoriaMovimiento.class, "CategoriaMovimiento.ts"),
                new Contrato(TipoMovimiento.class, "TipoMovimiento.ts"),
                new Contrato(MetodoPago.class, "MetodoPago.ts"),
                // AlcanceDescuento vive dentro de Descuento.ts, no en su propio
                // archivo: el contrato es por valores, no por nombre de archivo.
                new Contrato(AlcanceDescuento.class, "Descuento.ts"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contratos")
    @DisplayName("cada valor del enum de Java existe en el tipo de TypeScript")
    void elTipoDelFrontendTieneTodosLosValores(Contrato contrato) throws IOException {
        Path archivo = MODELOS_DEL_FRONT.resolve(contrato.archivoTs());
        assumeTrue(Files.isRegularFile(archivo),
                "No esta el frontend en " + archivo.toAbsolutePath() + ": se saltea");

        String typescript = Files.readString(archivo, StandardCharsets.UTF_8);

        List<String> faltantes = Arrays.stream(contrato.enumJava().getEnumConstants())
                .map(Enum::name)
                .filter(valor -> !typescript.contains("\"" + valor + "\""))
                .toList();

        assertThat(faltantes)
                .as("Valores de %s que el frontend no conoce. Agregalos a %s "
                                + "(y despues completa el mapa de etiquetas que el compilador va a exigir).",
                        contrato.enumJava().getSimpleName(), contrato.archivoTs())
                .isEmpty();
    }
}
