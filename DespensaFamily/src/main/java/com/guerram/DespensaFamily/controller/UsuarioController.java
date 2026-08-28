package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.CambiarPasswordDTO;
import com.guerram.DespensaFamily.dto.LoginRequestDTO;
import com.guerram.DespensaFamily.dto.LoginResponseDTO;
import com.guerram.DespensaFamily.dto.UsuarioDTO;
import com.guerram.DespensaFamily.service.UsuarioService;
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
        return new LoginResponseDTO("Contraseña cambiada correctamente", null, null);
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO request) {
        return usuarioService.login(request.getUsername(), request.getPassword());
    }


}
