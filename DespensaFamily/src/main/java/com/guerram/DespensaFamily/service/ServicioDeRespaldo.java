package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.Escritorio;
import com.guerram.DespensaFamily.config.Respaldos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * Respaldos de la base: uno automatico al arrancar y otro a pedido desde el
 * icono de la bandeja.
 *
 * <p>Que sea al arrancar y no en un horario fijo es a proposito: la PC de un
 * comercio se prende a la manana y se apaga a la noche, asi que "al abrir el
 * negocio" es el unico momento garantizado. Un cron a las 3 AM no correria nunca.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ServicioDeRespaldo {

    private final JdbcTemplate jdbcTemplate;

    @Value("${despensa.backups}")
    private final String carpetaBackups;

    @Value("${despensa.respaldos.a-conservar:14}")
    private final int aConservar;

    @Value("${despensa.respaldos.al-arrancar:true}")
    private final boolean alArrancar;

    @EventListener(ApplicationReadyEvent.class)
    public void respaldarAlArrancar() {
        if (!alArrancar) {
            return;
        }
        try {
            Path archivo = respaldar();
            log.info("Respaldo automatico: {}", archivo.getFileName());
        } catch (Exception e) {
            // Que falle el respaldo no puede impedir que el negocio venda.
            log.warn("No se pudo hacer el respaldo automatico: {}", e.getMessage());
        }
    }

    /**
     * Usa BACKUP TO de H2, que escribe un zip consistente con la base en uso.
     * Copiar el .mv.db a mano mientras la aplicacion corre puede dejar un
     * archivo a medio escribir, que es peor que no tener respaldo: parece uno.
     */
    public Path respaldar() throws IOException {
        Path carpeta = Paths.get(carpetaBackups);
        Files.createDirectories(carpeta);

        Path destino = carpeta.resolve(Escritorio.nombreDeRespaldo(LocalDateTime.now()));
        jdbcTemplate.execute("BACKUP TO '" + destino.toString().replace('\\', '/') + "'");

        eliminarLosViejos(carpeta);
        return destino;
    }

    private void eliminarLosViejos(Path carpeta) {
        try (Stream<Path> archivos = Files.list(carpeta)) {
            List<String> nombres = archivos
                    .map(ruta -> ruta.getFileName().toString())
                    .toList();

            for (String sobrante : Respaldos.aEliminar(nombres, aConservar)) {
                try {
                    Files.deleteIfExists(carpeta.resolve(sobrante));
                    log.info("Respaldo viejo eliminado: {}", sobrante);
                } catch (IOException e) {
                    log.warn("No se pudo eliminar el respaldo {}: {}", sobrante, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo revisar la carpeta de respaldos: {}", e.getMessage());
        }
    }
}
