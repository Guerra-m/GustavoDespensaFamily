package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        if (usuarioRepository.count() == 0) {
            Usuario usuario = Usuario.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .nombre("Administrador")
                    .rol("ADMIN")
                    .build();
            usuarioRepository.save(usuario);
        } else {
            // Migración: usuarios existentes sin rol quedan como ADMIN
            usuarioRepository.findAll().forEach(u -> {
                if (u.getRol() == null) {
                    u.setRol("ADMIN");
                    usuarioRepository.save(u);
                }
            });
        }
    }
}
