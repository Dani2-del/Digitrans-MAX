package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.AvisoAdministrativo;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.AvisoAdministrativoRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AvisoAdministrativoService {

    private static final int MAX_ASUNTO = 120;
    private static final int MAX_MENSAJE = 3000;
    private static final int MAX_IMAGEN_BYTES = 5 * 1024 * 1024;
    private static final Pattern CORREO_VALIDO =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> ROLES_NOTIFICABLES = Set.of(
            Usuario.ROLE_USER, Usuario.ROLE_CONDUCTOR, Usuario.ROLE_ADMIN);

    private final AvisoAdministrativoRepository avisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    public AvisoAdministrativoService(AvisoAdministrativoRepository avisoRepository,
                                      UsuarioRepository usuarioRepository,
                                      EmailService emailService) {
        this.avisoRepository = avisoRepository;
        this.usuarioRepository = usuarioRepository;
        this.emailService = emailService;
    }

    public List<Usuario> obtenerDestinatariosElegibles() {
        return usuarioRepository.findAll().stream()
                .filter(this::esDestinatarioElegible)
                .sorted((primero, segundo) -> nombreParaMostrar(primero)
                        .compareToIgnoreCase(nombreParaMostrar(segundo)))
                .toList();
    }

    public ResultadoEnvio crearAviso(String asunto, String mensaje, boolean paraTodos,
                                     List<String> rolesSeleccionados, List<String> idsSeleccionados) {
        return crearAviso(asunto, mensaje, paraTodos, rolesSeleccionados, idsSeleccionados, null);
    }

    public ResultadoEnvio crearAviso(String asunto, String mensaje, boolean paraTodos,
                                     List<String> rolesSeleccionados, List<String> idsSeleccionados,
                                     MultipartFile imagenSubida) {
        String asuntoValidado = asunto == null ? "" : asunto.trim();
        String mensajeValidado = mensaje == null ? "" : mensaje.trim();
        if (!StringUtils.hasText(asuntoValidado) || asuntoValidado.length() > MAX_ASUNTO) {
            throw new IllegalArgumentException("El asunto es obligatorio y debe tener máximo 120 caracteres.");
        }
        if (!StringUtils.hasText(mensajeValidado) || mensajeValidado.length() > MAX_MENSAJE) {
            throw new IllegalArgumentException("El mensaje es obligatorio y debe tener máximo 3000 caracteres.");
        }
        ImagenAviso imagen = validarImagen(imagenSubida);

        Set<String> roles = validarRoles(rolesSeleccionados);
        List<Usuario> destinatarios = paraTodos
                ? obtenerDestinatariosElegibles().stream()
                        .filter(usuario -> roles.contains(usuario.getRole()))
                        .toList()
                : obtenerDestinatariosSeleccionados(idsSeleccionados, roles);
        if (destinatarios.isEmpty()) {
            throw new IllegalArgumentException("No hay cuentas activas y verificadas para los tipos seleccionados.");
        }

        List<String> idsDestinatarios = destinatarios.stream()
                .map(Usuario::getId)
                .distinct()
                .toList();
        AvisoAdministrativo nuevoAviso =
                new AvisoAdministrativo(asuntoValidado, mensajeValidado, idsDestinatarios);
        if (imagen != null) {
            nuevoAviso.setImagen(imagen.contenido());
            nuevoAviso.setImagenTipoContenido(imagen.tipoContenido());
        }
        AvisoAdministrativo aviso = avisoRepository.save(nuevoAviso);

        List<EmailService.DestinatarioAviso> destinatariosCorreo = destinatarios.stream()
                .map(destinatario -> new EmailService.DestinatarioAviso(
                        destinatario.getId(), destinatario.getCorreo(), nombreParaMostrar(destinatario)))
                .toList();
        emailService.enviarAvisosAdministrativosEnSegundoPlano(aviso.getId(), destinatariosCorreo,
                asuntoValidado, mensajeValidado,
                imagen == null ? null : imagen.contenido(),
                imagen == null ? null : imagen.tipoContenido());

        return new ResultadoEnvio(aviso.getId(), destinatarios.size(), destinatariosCorreo.size());
    }

    public ImagenAviso obtenerImagenParaUsuario(String avisoId, String usuarioId, boolean administrador) {
        AvisoAdministrativo aviso = avisoRepository.findById(avisoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el aviso solicitado."));
        if (!administrador && !aviso.getDestinatarios().contains(usuarioId)) {
            throw new SecurityException("El usuario no es destinatario de este aviso.");
        }
        if (aviso.getImagen() == null || aviso.getImagen().length == 0) {
            throw new IllegalArgumentException("El aviso no contiene una imagen.");
        }
        return new ImagenAviso(aviso.getImagen(), aviso.getImagenTipoContenido());
    }

    public List<AvisoAdministrativo> obtenerPendientes(String usuarioId) {
        if (!StringUtils.hasText(usuarioId)) {
            return List.of();
        }
        return avisoRepository.findPendientesParaUsuario(usuarioId);
    }

    public List<AvisoAdministrativo> obtenerAvisosRecientes() {
        return avisoRepository.findTop2ByOrderByFechaCreacionDesc();
    }

    public List<AvisoAdministrativo> obtenerHistorialAvisos() {
        return avisoRepository.findAllByOrderByFechaCreacionDesc();
    }

    public void descartarParaUsuario(String avisoId, String usuarioId) {
        AvisoAdministrativo aviso = avisoRepository.findById(avisoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el aviso solicitado."));
        if (!aviso.getDestinatarios().contains(usuarioId)) {
            throw new SecurityException("El usuario no es destinatario de este aviso.");
        }
        if (aviso.getDescartadaPor().add(usuarioId)) {
            avisoRepository.save(aviso);
        }
    }

    private List<Usuario> obtenerDestinatariosSeleccionados(List<String> idsSeleccionados,
                                                              Set<String> rolesSeleccionados) {
        Set<String> ids = idsSeleccionados == null
                ? Set.of()
                : idsSeleccionados.stream()
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un destinatario.");
        }

        List<Usuario> encontrados = usuarioRepository.findAllById(ids);
        if (encontrados.size() != ids.size()
                || encontrados.stream().anyMatch(u -> !esDestinatarioElegible(u)
                || !rolesSeleccionados.contains(u.getRole()))) {
            throw new IllegalArgumentException("La selección contiene cuentas inexistentes o no elegibles.");
        }
        return encontrados;
    }

    private Set<String> validarRoles(List<String> rolesSeleccionados) {
        Set<String> roles = rolesSeleccionados == null
                ? Set.of()
                : rolesSeleccionados.stream()
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roles.isEmpty() || !ROLES_NOTIFICABLES.containsAll(roles)) {
            throw new IllegalArgumentException("Selecciona al menos un tipo de cuenta válido.");
        }
        return roles;
    }

    private ImagenAviso validarImagen(MultipartFile imagenSubida) {
        if (imagenSubida == null || imagenSubida.isEmpty()) {
            return null;
        }
        if (imagenSubida.getSize() > MAX_IMAGEN_BYTES) {
            throw new IllegalArgumentException("La imagen no puede superar los 5 MB.");
        }

        String tipoContenido = imagenSubida.getContentType();
        if (!"image/png".equalsIgnoreCase(tipoContenido)
                && !"image/jpeg".equalsIgnoreCase(tipoContenido)) {
            throw new IllegalArgumentException("La imagen debe estar en formato PNG o JPEG.");
        }

        try {
            byte[] contenido = imagenSubida.getBytes();
            if (!esImagenValida(contenido, tipoContenido)) {
                throw new IllegalArgumentException("El archivo no contiene una imagen PNG o JPEG válida.");
            }
            return new ImagenAviso(contenido, tipoContenido.toLowerCase(Locale.ROOT));
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer la imagen seleccionada.", exception);
        }
    }

    private boolean esImagenValida(byte[] contenido, String tipoContenido) {
        if ("image/png".equalsIgnoreCase(tipoContenido)) {
            return contenido.length >= 8
                    && contenido[0] == (byte) 0x89 && contenido[1] == 0x50
                    && contenido[2] == 0x4E && contenido[3] == 0x47
                    && contenido[4] == 0x0D && contenido[5] == 0x0A
                    && contenido[6] == 0x1A && contenido[7] == 0x0A;
        }
        return contenido.length >= 3
                && contenido[0] == (byte) 0xFF
                && contenido[1] == (byte) 0xD8
                && contenido[2] == (byte) 0xFF;
    }

    private boolean esDestinatarioElegible(Usuario usuario) {
        return usuario != null
                && usuario.isActivo()
                && usuario.isVerificado()
                && StringUtils.hasText(usuario.getId())
                && StringUtils.hasText(usuario.getCorreo())
                && CORREO_VALIDO.matcher(usuario.getCorreo().trim()).matches();
    }

    private String nombreParaMostrar(Usuario usuario) {
        return StringUtils.hasText(usuario.getNombre()) ? usuario.getNombre().trim() : usuario.getCorreo();
    }

    public record ResultadoEnvio(String avisoId, int totalDestinatarios, int correosProgramados) {
    }

    public record ImagenAviso(byte[] contenido, String tipoContenido) {
    }
}
