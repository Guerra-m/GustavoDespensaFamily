package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.CredencialesInvalidasException;
import com.guerram.DespensaFamily.config.DemasiadosIntentosException;
import com.guerram.DespensaFamily.config.LimitadorDeIntentos;
import com.guerram.DespensaFamily.config.PoliticaDePassword;
import com.guerram.DespensaFamily.model.Roles;
import com.guerram.DespensaFamily.dto.CambiarPasswordDTO;
import com.guerram.DespensaFamily.dto.CrearUsuarioRequestDTO;
import com.guerram.DespensaFamily.dto.LoginResponseDTO;
import com.guerram.DespensaFamily.dto.PermisosUsuarioDTO;
import com.guerram.DespensaFamily.dto.UsuarioDTO;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService  {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LimitadorDeIntentos limitadorDeIntentos;
    private final EmpleadoService empleadoService;

    // Hash bcrypt de una contrasena cualquiera. Solo se usa para gastar el
    // mismo tiempo de comparacion cuando el usuario no existe.
    private static final String HASH_DE_DESCARTE =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";


    public UsuarioDTO obtenerPerfil() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return Mapper.toDTO(usuario);
    }

    // CAMBIAR CONTRASEÑA
    // IDOR (AUTZ-02): antes recibía el id del path y confiaba en él para
    // decidir SOBRE QUIÉN actuar — cualquier usuario autenticado podía
    // cambiar la contraseña de otro con solo cambiar el id en la URL
    // (probando que conociera la contraseña actual de esa otra cuenta). El
    // filtro de "dueño" ahora va en la misma resolución del usuario, no se
    // compara después: se ignora cualquier id que venga del cliente y se
    // resuelve siempre el usuario autenticado desde el token, igual que
    // obtenerPerfil().
    public Usuario cambiarPassword(String passwordActual, String nuevaPassword) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return cambiarPasswordDe(username, passwordActual, nuevaPassword);
    }

    // DF-SEC-004: antes esto no tenia ningun freno — 20 intentos seguidos
    // adivinando la contrasena actual pasaban sin demora, la misma ventana
    // que ya se habia cerrado en login() (ver frenarSiCorresponde). Mismo
    // criterio (AUTN-03): demora progresiva por clave, nunca bloqueo de
    // cuenta. La clave es "password:" + username, separada de "usuario:"
    // que usa el login, a proposito: machacar el cambio de contrasena no
    // tiene por que gastarle el margen a quien intenta loguearse, y
    // viceversa.
    //
    // DF-BUG-007 aplica igual de fuerte aca: DemasiadosIntentosException
    // mapea a 429 y el fallo de contrasena actual sigue siendo un
    // RuntimeException generico que el handler global manda a 400 — nunca
    // 401, porque el frontend borra la sesion ante cualquier 401 y esto es
    // "Mi Perfil", no el login.
    public Usuario cambiarPasswordDe(String username, String passwordActual, String nuevaPassword) {
        String clave = clavePasswordDe(username);

        limitadorDeIntentos.esperaPendiente(clave).ifPresent(espera -> {
            throw new DemasiadosIntentosException(espera);
        });

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            limitadorDeIntentos.registrarFallo(clave);
            throw new RuntimeException("Contraseña actual incorrecta");
        }

        limitadorDeIntentos.registrarExito(clave);

        PoliticaDePassword.validar(nuevaPassword);

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        // Elegir una contrasena propia es exactamente lo que se le estaba
        // pidiendo al que entra con la generada por el instalador.
        usuario.setDebeCambiarPassword(false);
        return usuarioRepository.save(usuario);
    }

    private static String clavePasswordDe(String username) {
        return "password:" + username;
    }
    // LOGIN
    // Dos cosas que antes no hacia, las dos medidas en la auditoria:
    //
    // 1. Un solo error para todos los fallos (CredencialesInvalidasException).
    //    Antes respondia 404 si el usuario no existia y 401 si existia con la
    //    contrasena mal, lo que deja averiguar que cuentas hay probando
    //    nombres.
    // 2. Freno progresivo por usuario Y por IP (AUTN-03). Las dos claves hacen
    //    falta: por usuario solo, el atacante prueba una contrasena contra
    //    cada cuenta y nunca llega al umbral; por IP sola, no frena al que
    //    machaca una cuenta desde varios dispositivos del local.
    public LoginResponseDTO login(String username, String password, String ip) {
        String claveUsuario = "usuario:" + username;
        String claveIp = "ip:" + ip;

        frenarSiCorresponde(claveUsuario, claveIp);

        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);

        // El chequeo se hace igual contra un hash de descarte cuando el usuario
        // no existe: si no, la respuesta vuelve mucho mas rapido en ese caso y
        // el tiempo delata que cuentas existen.
        boolean valida = usuario != null && passwordEncoder.matches(password, usuario.getPassword());
        if (usuario == null) {
            passwordEncoder.matches(password, HASH_DE_DESCARTE);
        }

        if (!valida) {
            limitadorDeIntentos.registrarFallo(claveUsuario);
            limitadorDeIntentos.registrarFallo(claveIp);
            throw new CredencialesInvalidasException();
        }

        limitadorDeIntentos.registrarExito(claveUsuario);
        limitadorDeIntentos.registrarExito(claveIp);

        String rol = usuario.getRol() != null ? usuario.getRol() : "EMPLEADO";
        String token = jwtService.generateToken(username);
        return new LoginResponseDTO("Login exitoso", token, rol, usuario.isDebeCambiarPassword(), usuario.isPuedeGestionarProductos(), usuario.isPuedeVerCaja());
    }

    private void frenarSiCorresponde(String claveUsuario, String claveIp) {
        limitadorDeIntentos.esperaPendiente(claveUsuario).ifPresent(espera -> {
            throw new DemasiadosIntentosException(espera);
        });
        limitadorDeIntentos.esperaPendiente(claveIp).ifPresent(espera -> {
            throw new DemasiadosIntentosException(espera);
        });
    }

    /** Solo para tests: deja las dos claves de login sin castigo acumulado. */
    public void olvidarIntentos(String username, String ip) {
        limitadorDeIntentos.registrarExito("usuario:" + username);
        limitadorDeIntentos.registrarExito("ip:" + ip);
    }

    /** Solo para tests: deja el freno de cambio de contraseña sin castigo acumulado. */
    public void olvidarIntentosDeCambioDePassword(String username) {
        limitadorDeIntentos.registrarExito(clavePasswordDe(username));
    }

    public List<UsuarioDTO> listarUsuarios() {
        return usuarioRepository.findAll()
                .stream()
                .map(Mapper::toDTO)
                .toList();
    }

    // Usuario + Empleado como una sola operacion: si vincularConUsuario
    // fallara, el usuario tampoco queda creado a medias (mismo criterio que
    // venta+caja, ver VentaService).
    @Transactional
    public UsuarioDTO crearUsuario(CrearUsuarioRequestDTO dto) {
        if (usuarioRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new RuntimeException("El nombre de usuario ya existe");
        }
        PoliticaDePassword.validar(dto.getPassword());
        String rol = Roles.validar(dto.getRol());
        Usuario usuario = Usuario.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nombre(dto.getNombre())
                .rol(rol)
                .puedeGestionarProductos(dto.isPuedeGestionarProductos())
                .puedeVerCaja(dto.isPuedeVerCaja())
                .build();
        Usuario guardado = usuarioRepository.save(usuario);

        // Quien va a trabajar en el local queda disponible para marcar sus
        // dias y pagarle sin cargarlo dos veces (ver EmpleadoService.vincularConUsuario).
        empleadoService.vincularConUsuario(guardado.getUsername(), guardado.getNombre());

        return Mapper.toDTO(guardado);
    }

    // Solo toca los permisos: no se puede reasignar rol, contraseña o
    // nombre desde acá (esas no fueron pedidas y ya tienen su propio camino
    // — cambiarPassword y, para rol/nombre, no hay edición hoy).
    @Transactional
    public UsuarioDTO actualizarPermisos(Long id, PermisosUsuarioDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.setPuedeGestionarProductos(dto.isPuedeGestionarProductos());
        usuario.setPuedeVerCaja(dto.isPuedeVerCaja());

        return Mapper.toDTO(usuarioRepository.save(usuario));
    }

    public void eliminarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String propio = SecurityContextHolder.getContext().getAuthentication().getName();
        if (usuario.getUsername().equals(propio)) {
            throw new RuntimeException("No podés eliminar tu propio usuario");
        }

        // Sin esto, borrar al ultimo administrador dejaba el sistema sin nadie
        // que pudiera entrar a Usuarios, Reportes ni Ajustes: la unica salida
        // era borrar la base y perder los datos del negocio.
        if (Roles.ADMIN.equals(usuario.getRol()) && contarAdministradores() <= 1) {
            throw new RuntimeException("No podés eliminar al último administrador del sistema");
        }

        usuarioRepository.delete(usuario);
    }

    private long contarAdministradores() {
        return usuarioRepository.findAll().stream()
                .filter(u -> Roles.ADMIN.equals(u.getRol()))
                .count();
    }

}
