package com.guerram.DespensaFamily.config;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import org.h2.tools.RunScript;

/**
 * Deja una base en memoria con el esquema que tenia ANTES de que existieran las
 * migraciones, para poder probar que Flyway la levanta.
 *
 * Se siembra desde un bloque estatico del test y no con la clausula INIT de la
 * URL de H2, que fue el primer intento: INIT corre en CADA conexion que abre el
 * pool, asi que la segunda conexion volvia a ejecutar los CREATE TABLE y
 * reventaba con "la tabla ya existe". Aca se corre una sola vez, antes de que
 * Spring levante el contexto, que es cuando la clase del test se carga.
 *
 * DB_CLOSE_DELAY=-1 en la URL es lo que mantiene viva la base entre esa conexion
 * y las que abre despues la aplicacion.
 */
final class EsquemaDePrueba {

    private EsquemaDePrueba() {
    }

    static void sembrar(String url, String recurso) {
        try (Connection conexion = DriverManager.getConnection(url, "sa", "");
                Reader guion = new InputStreamReader(
                        EsquemaDePrueba.class.getResourceAsStream(recurso), StandardCharsets.UTF_8)) {
            RunScript.execute(conexion, guion);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
