package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.config.ReglaDeNegocioException;
import com.guerram.DespensaFamily.service.EscaneoService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/escaneo")
@RequiredArgsConstructor
public class EscaneoController {

    private final EscaneoService escaneoService;

    /**
     * Lo pide Ventas, ya autenticado: arranca una sesión nueva de escaneo
     * remoto y arma la URL con la IP de LA RED LOCAL, no con la del pedido
     * (que en la app de escritorio suele llegar como localhost — inservible
     * para un celular, que tiene su propio localhost).
     */
    @PostMapping("/sesiones")
    public Map<String, String> crearSesion(HttpServletRequest request) {
        String id = escaneoService.crearSesion();
        String url = "http://" + ipDeLaRedLocal() + ":" + request.getServerPort()
                + "/src/pages/scan-movil/scan-movil.html?sesion=" + id;
        return Map.of("sesionId", id, "url", url);
    }

    private String ipDeLaRedLocal() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                Enumeration<InetAddress> direcciones = iface.getInetAddresses();
                while (direcciones.hasMoreElements()) {
                    InetAddress direccion = direcciones.nextElement();
                    if (direccion.isSiteLocalAddress() && direccion.getHostAddress().indexOf(':') < 0) {
                        return direccion.getHostAddress();
                    }
                }
            }
        } catch (Exception ignorado) {
            // Sigue al fallback de abajo.
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    /** Se suscribe Ventas para recibir en vivo lo que escanea el celular. */
    @GetMapping("/sesiones/{id}/eventos")
    public SseEmitter eventos(@PathVariable String id) {
        return escaneoService.suscribirse(id);
    }

    /** Lo llama el celular, sin sesión propia — ver EscaneoService. */
    @PostMapping("/sesiones/{id}/codigos")
    public void publicarCodigo(@PathVariable String id, @RequestBody Map<String, String> body) {
        String codigo = body.get("codigo");
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaDeNegocioException("Falta el código escaneado");
        }
        escaneoService.publicarCodigo(id, codigo.trim());
    }
}
