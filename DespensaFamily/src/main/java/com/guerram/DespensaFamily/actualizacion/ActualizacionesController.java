package com.guerram.DespensaFamily.actualizacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/actualizaciones")
public class ActualizacionesController {

    private static final Logger log = LoggerFactory.getLogger(ActualizacionesController.class);

    /**
     * Margen para que la respuesta HTTP llegue al navegador antes de que el
     * servidor empiece a bajar. Sin esto, el usuario toca "actualizar" y ve un
     * error de red: el proceso se apago mientras contestaba.
     */
    private static final long MARGEN_ANTES_DE_CERRAR_MS = 800;

    private final ServicioDeActualizaciones servicio;
    private final ApplicationContext contexto;

    public ActualizacionesController(ServicioDeActualizaciones servicio, ApplicationContext contexto) {
        this.servicio = servicio;
        this.contexto = contexto;
    }

    @GetMapping
    public EstadoDeActualizacion estado() {
        return servicio.estado();
    }

    /**
     * Aplica la actualizacion que el launcher ya descargo y verifico.
     *
     * <p>Este endpoint apaga el servidor, que es justo lo que en la version de
     * escritorio se habia evitado a proposito: un endpoint de apagado es un
     * boton de "cerrar el negocio" al alcance de cualquier cosa que llegue al
     * puerto. Lo que lo hace aceptable aca son tres condiciones juntas, y si
     * falta una no apaga nada:
     *
     * <ul>
     *   <li>pide rol ADMIN (ver SecurityConfig): un empleado no puede;
     *   <li>solo funciona si hay un jar descargado y con el SHA-256 verificado
     *       esperando, asi que no sirve como apagado generico;
     *   <li>el launcher lo vuelve a levantar enseguida: no deja la caja abajo.
     * </ul>
     */
    @PostMapping("/aplicar")
    public ResponseEntity<Void> aplicar() {
        try {
            servicio.pedirQueSeAplique();
        } catch (IllegalStateException e) {
            log.warn("Se pidio aplicar una actualizacion que no esta lista: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        log.info("Actualizacion aprobada: el servidor va a cerrar para que el launcher la aplique");
        cerrarDespuesDeResponder();
        return ResponseEntity.accepted().build();
    }

    private void cerrarDespuesDeResponder() {
        Thread cierre = new Thread(() -> {
            try {
                Thread.sleep(MARGEN_ANTES_DE_CERRAR_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // close() y no exit(): cierra el contexto con orden —la base se
            // cierra bien— y deja que el proceso termine solo. El launcher esta
            // esperando justamente ese final para cambiar el jar.
            ((org.springframework.context.ConfigurableApplicationContext) contexto).close();
        }, "cierre-por-actualizacion");
        cierre.setDaemon(false);
        cierre.start();
    }
}
