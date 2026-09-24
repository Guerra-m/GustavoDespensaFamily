package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.CambiarPasswordDTO;
import com.guerram.DespensaFamily.dto.LoginRequestDTO;
import com.guerram.DespensaFamily.dto.LoginResponseDTO;
import com.guerram.DespensaFamily.dto.UsuarioDTO;
import com.guerram.DespensaFamily.service.UsuarioService;
import com.guerram.DespensaFamily.config.CookieDeSesion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/perfil")
    public UsuarioDTO perfil() {
        return usuarioService.obtenerPerfil();
    }

    // CAMBIAR PASSWORD
    // {id} se ignora a propósito para decidir sobre quién actuar (ver
    // UsuarioService.cambiarPassword) — el usuario objetivo siempre se
    // resuelve del token, nunca del path. Se mantiene en la ruta sin
    // cambios para no tocar el contrato que ya usa el frontend.
    @PatchMapping("/{id}/password")
    public LoginResponseDTO cambiarPassword(@PathVariable Long id, @Valid @RequestBody CambiarPasswordDTO request) {
        usuarioService.cambiarPassword(request.getPasswordActual(), request.getPasswordNueva());
        return new LoginResponseDTO("Contraseña cambiada correctamente", null, null, false, false, false);
    }

    // La IP entra al freno de intentos junto con el nombre de usuario (ver
    // UsuarioService.login). Se usa la de la conexion y no X-Forwarded-For:
    // acá no hay proxy adelante, y ese header lo pone el cliente, así que
    // confiar en él permitiría esquivar el límite cambiándolo en cada intento.
    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO request,
                                  HttpServletRequest peticion,
                                  HttpServletResponse respuesta) {

        LoginResponseDTO login = usuarioService.login(
                request.getUsername(), request.getPassword(), peticion.getRemoteAddr());

        // La sesion sale en una cookie HttpOnly y no en el cuerpo. Ver
        // CookieDeSesion: es lo que hace que el navegador la mande sola en cada
        // pedido —fotos de producto incluidas, que era lo que estaba roto— y lo
        // que saca el token del alcance de cualquier script de la pagina.
        respuesta.addCookie(CookieDeSesion.crear(login.getToken(), peticion));
        return login;
    }

    /**
     * Cierra la sesion del lado del servidor.
     *
     * <p>Antes cerrar sesion era solo borrar el localStorage, o sea una
     * decision del cliente: quien no corriera ese codigo se quedaba con el
     * token. Ahora la cookie la apaga el servidor.
     *
     * <p>Es publico a proposito: si la cookie ya vencio o vino manoseada, el
     * pedido igual tiene que poder limpiarla en vez de rebotar con un 403 y
     * dejar al usuario con una cookie rota que no puede sacarse de encima.
     */
    @PostMapping("/logout")
    public void logout(HttpServletRequest peticion, HttpServletResponse respuesta) {
        respuesta.addCookie(CookieDeSesion.borrar(peticion));
    }


}
