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
    @PatchMapping("/{id}/password")
    public LoginResponseDTO cambiarPassword(@PathVariable Long id, @RequestBody CambiarPasswordDTO request) {
        usuarioService.cambiarPassword(id, request.getPasswordActual(), request.getPasswordNueva());
        return new LoginResponseDTO("Contraseña cambiada correctamente", null, null);
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO request) {
        return usuarioService.login(request.getUsername(), request.getPassword());
    }


}
