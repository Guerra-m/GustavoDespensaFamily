package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        if (usuarioRepository.count() == 0) {
            usuarioRepository.save(Usuario.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .nombre("Administrador")
                    .rol("ADMIN")
                    .build());
        } else {
            // Migración: usuarios sin rol quedan como ADMIN
            usuarioRepository.findAll().forEach(u -> {
                if (u.getRol() == null) {
                    u.setRol("ADMIN");
                    usuarioRepository.save(u);
                }
            });
        }

        // Crear empleados de ejemplo si no existen
        crearEmpleadoSiNoExiste("juan", "Juan García", "empleado123");
        crearEmpleadoSiNoExiste("maria", "María López", "empleado123");
        crearEmpleadoSiNoExiste("carlos", "Carlos Rodríguez", "empleado123");
    }

    private void crearEmpleadoSiNoExiste(String username, String nombre, String password) {
        if (usuarioRepository.findByUsername(username).isEmpty()) {
            usuarioRepository.save(Usuario.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .nombre(nombre)
                    .rol("EMPLEADO")
                    .build());
        }
    }
}
