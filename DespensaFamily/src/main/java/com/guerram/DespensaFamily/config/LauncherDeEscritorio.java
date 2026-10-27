package com.guerram.DespensaFamily.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import com.guerram.DespensaFamily.service.ServicioDeRespaldo;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Convierte la aplicacion web en algo que se siente un programa de escritorio:
 * abre el navegador cuando el servidor esta realmente listo y deja un icono en
 * la bandeja con Abrir, Respaldar y Salir.
 *
 * <p>No hace falta un launcher aparte ni Electron: el ejecutable lo genera
 * jpackage y esto es lo unico que le agrega comportamiento de escritorio.
 *
 * <p>Se apaga con {@code despensa.escritorio=false} (los tests lo usan, y sirve
 * si algun dia la app corre en un servidor sin pantalla).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "despensa.escritorio", havingValue = "true", matchIfMissing = true)
public class LauncherDeEscritorio {

    // El boton de la bandeja y el respaldo automatico usan el mismo codigo:
    // un solo lugar donde puede estar mal.
    private final ServicioDeRespaldo servicioDeRespaldo;

    @Value("${server.port:8080}")
    private final int puerto;

    // Quien pone la ventana. En "ventana" la pone el programa de escritorio y
    // aca solo queda la bandeja; en "navegador" (el de siempre) la abre Java.
    @Value("${despensa.escritorio.modo:navegador}")
    private final String modoConfigurado;

    /** Margen para que el apagado ordenado termine antes de cortar por lo sano. */
    private static final int SEGUNDOS_PARA_CERRAR = 8;

    private TrayIcon iconoBandeja;

    /**
     * ApplicationReadyEvent es el sondeo real que reemplaza al "timeout /t 10"
     * de iniciar.bat: Spring lo dispara cuando el servidor ya acepta pedidos.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void alEstarLista() {
        ModoEscritorio modo = ModoEscritorio.desde(modoConfigurado);

        // Va antes del chequeo de pantalla a proposito: aunque no haya bandeja,
        // el programa de escritorio tiene que poder cerrar este servidor.
        if (!modo.abreElNavegador()) {
            escucharElCierreDeLaVentana();
        }

        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        if (modo.abreElNavegador()) {
            Escritorio.abrirNavegador(puerto);
        }
        instalarIconoEnLaBandeja(modo);
    }

    /**
     * En modo ventana, el pedido de cierre llega por la entrada estandar: el
     * programa de escritorio la cierra y aca se ve el fin de la entrada.
     *
     * <p>Se eligio ese cable y no un endpoint HTTP de apagado porque un
     * endpoint asi seria un boton de "cerrar el negocio" abierto a cualquier
     * cosa que corra en la misma maquina. El pipe, en cambio, solo lo tiene
     * quien arranco el proceso.
     *
     * <p>Ademas cubre el caso feo: si el programa de escritorio se muere de
     * golpe, el sistema cierra el pipe igual y este servidor no queda huerfano
     * ocupando el puerto y el archivo de la base.
     */
    private void escucharElCierreDeLaVentana() {
        Thread vigia = new Thread(() -> {
            try {
                // No se esperan datos: lo unico que importa es el fin de la entrada.
                while (System.in.read() != -1) {
                    continue;
                }
            } catch (IOException e) {
                // El pipe se rompio: misma senal que el fin de la entrada.
            }
            cerrar();
        }, "cierre-desde-la-ventana");
        vigia.setDaemon(true);
        vigia.start();
    }

    private void instalarIconoEnLaBandeja(ModoEscritorio modo) {
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();

            // "Abrir" solo tiene sentido cuando la ventana es el navegador.
            // Con el programa de escritorio la ventana ya esta, y traerla al
            // frente es cosa de el, no de la bandeja.
            if (modo.abreElNavegador()) {
                MenuItem abrir = new MenuItem("Abrir DespensaFamily");
                abrir.addActionListener(e -> Escritorio.abrirNavegador(puerto));
                menu.add(abrir);
            }

            MenuItem respaldar = new MenuItem("Respaldar ahora");
            respaldar.addActionListener(e -> respaldar());

            MenuItem salir = new MenuItem("Salir");
            salir.addActionListener(e -> cerrar());

            menu.add(respaldar);
            menu.addSeparator();
            menu.add(salir);

            iconoBandeja = new TrayIcon(dibujarIcono(),
                    modo.abreElNavegador()
                            ? "DespensaFamily esta funcionando\nClic derecho: abrir, respaldar o salir"
                            : "DespensaFamily esta funcionando\nClic derecho: respaldar o salir",
                    menu);
            iconoBandeja.setImageAutoSize(true);
            if (modo.abreElNavegador()) {
                iconoBandeja.addActionListener(e -> Escritorio.abrirNavegador(puerto));
            }
            SystemTray.getSystemTray().add(iconoBandeja);

            // Windows 11 esconde los iconos nuevos detras de la flecha de la
            // bandeja, asi que nadie lo encuentra solo. El aviso aparece una vez
            // al arrancar y senala donde vive el programa: sin esto la unica
            // forma de cerrarlo es el Administrador de tareas.
            //
            // Con el programa de escritorio no hace falta: cerrar la ventana
            // cierra todo, que es lo que la gente espera. Avisar ahi solo
            // sembraria la duda de si quedo algo abierto.
            if (modo.abreElNavegador()) {
                avisar("DespensaFamily esta funcionando",
                        "Cerrar el navegador no cierra el programa. "
                                + "Para cerrarlo, clic derecho en este icono y elegi Salir. "
                                + "Si no lo ves, esta detras de la flecha de la barra de tareas.");
            }

        } catch (Exception e) {
            // Sin bandeja la app igual funciona: no vale la pena tirarla abajo.
            System.out.println("No se pudo crear el icono de la bandeja: " + e.getMessage());
        }
    }

    private void respaldar() {
        try {
            Path destino = servicioDeRespaldo.respaldar();
            avisar("Respaldo guardado", destino.getFileName().toString());
        } catch (Exception e) {
            avisar("No se pudo respaldar", e.getMessage() == null ? "Error desconocido" : e.getMessage());
        }
    }

    private void cerrar() {
        if (iconoBandeja != null) {
            SystemTray.getSystemTray().remove(iconoBandeja);
        }

        // Red de seguridad: si el apagado ordenado se cuelga (Tomcat esperando
        // pedidos en curso, el pool de conexiones, un hilo no-daemon de AWT),
        // el usuario ve que el icono desaparecio pero el programa sigue vivo.
        // Pasados unos segundos se corta por lo sano.
        Thread garantia = new Thread(() -> {
            try {
                Thread.sleep(SEGUNDOS_PARA_CERRAR * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Runtime.getRuntime().halt(0);
        }, "cierre-forzado");
        garantia.setDaemon(true);
        garantia.start();

        System.exit(0);
    }

    private void avisar(String titulo, String detalle) {
        if (iconoBandeja != null) {
            iconoBandeja.displayMessage(titulo, detalle, TrayIcon.MessageType.NONE);
        }
    }

    /** Se dibuja en memoria para no depender de un archivo de icono todavia. */
    private Image dibujarIcono() {
        int lado = 16;
        BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x02, 0x88, 0xD1)); // el celeste de la navbar
        g.fillRoundRect(0, 0, lado, lado, 5, 5);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("D", 4, 12);
        g.dispose();
        return imagen;
    }
}
