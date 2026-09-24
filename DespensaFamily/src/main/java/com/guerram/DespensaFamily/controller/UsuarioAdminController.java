package com.guerram.DespensaFamily.controller;

import com.guerram.DespensaFamily.dto.CrearUsuarioRequestDTO;
import com.guerram.DespensaFamily.dto.PermisosUsuarioDTO;
import com.guerram.DespensaFamily.dto.UsuarioDTO;
import com.guerram.DespensaFamily.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/usuarios")
@RequiredArgsConstructor
public class UsuarioAdminController {

    private final UsuarioService usuarioService;

    @GetMapping
    public List<UsuarioDTO> listar() {
        return usuarioService.listarUsuarios();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDTO crear(@Valid @RequestBody CrearUsuarioRequestDTO dto) {
        return usuarioService.crearUsuario(dto);
    }

    @PatchMapping("/{id}/permisos")
    public UsuarioDTO actualizarPermisos(@PathVariable Long id, @RequestBody PermisosUsuarioDTO dto) {
        return usuarioService.actualizarPermisos(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
    }
}
