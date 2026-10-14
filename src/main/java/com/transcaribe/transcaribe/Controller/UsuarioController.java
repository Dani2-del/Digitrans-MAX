package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.PreferenciaRutaNotificacion;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.ReciboPdfService;
import com.transcaribe.transcaribe.service.ServiceTranscaribe;
import com.transcaribe.transcaribe.service.TransaccionService;
import com.transcaribe.transcaribe.service.BusService;
import com.transcaribe.transcaribe.service.EmailService;
import com.transcaribe.transcaribe.service.AvisoAdministrativoService;
import java.io.Serializable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Controller
public class UsuarioController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsuarioController.class);
    private static final SecureRandom GENERADOR_CODIGOS = new SecureRandom();
    private static final String DESAFIO_CAMBIO_PERFIL = "desafioCambioPerfil";
    private static final Duration VIGENCIA_CODIGO_PERFIL = Duration.ofMinutes(10);
    private static final int MAX_INTENTOS_CODIGO_PERFIL = 5;

    @Autowired
    private ServiceTranscaribe service;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserDetailsService userDetailsService;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private TransaccionService transaccionService;

    @Autowired
    private BusService busService;

    @Autowired
    private ReciboPdfService reciboPdfService;

    @Autowired
    private AvisoAdministrativoService avisoAdministrativoService;

    private Usuario obtenerUsuarioLogueado() {
        String correo = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByCorreo(correo).orElse(null);
    }

    @GetMapping("/menu")
    public String menu(Model model) {
        Usuario usuario = obtenerUsuarioLogueado();
        
        if (usuario == null) {
            return "redirect:/login";
        }

        if (!usuario.isVerificado()) {
            return "redirect:/verificar-otp?correo=" + usuario.getCorreo();
        }

        service.procesarNotificacionLogin(usuario.getCorreo());
        
        model.addAttribute("usuario", usuario);
        model.addAttribute("alertasAdmin", avisoAdministrativoService.obtenerPendientes(usuario.getId()));
        model.addAttribute("alertaReturnTo", "/menu");
        return "usuarios/cuenta/menu";
    }

    @GetMapping("/perfil")
    public String perfil(Model model, HttpSession session) {
        Usuario usuario = obtenerUsuarioLogueado();
        
        if (usuario == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("usuario", usuario);
        DesafioCambioPerfil desafio = (DesafioCambioPerfil) session.getAttribute(DESAFIO_CAMBIO_PERFIL);
        if (desafio != null && Instant.now().toEpochMilli() >= desafio.expiraEn()) {
            session.removeAttribute(DESAFIO_CAMBIO_PERFIL);
            desafio = null;
            model.addAttribute("error", "El código venció. Solicita uno nuevo para continuar.");
        }
        model.addAttribute("codigoPendiente", desafio != null);
        if (desafio != null) {
            model.addAttribute("correoVerificacion", desafio.correoVerificacion());
        }
        return "usuarios/cuenta/perfil";
    }

    @PostMapping("/perfil")
    public String actualizarPerfil(@RequestParam String nombre, 
                                   @RequestParam String correo, 
                                   @RequestParam(required = false) String password, 
                                   Model model,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        Usuario usuarioActual = obtenerUsuarioLogueado();
        
        if (usuarioActual == null) {
            return "redirect:/login";
        }

        if (Objects.equals(nombre.trim(), usuarioActual.getNombre())
                && Objects.equals(correo.trim(), usuarioActual.getCorreo())
                && (password == null || password.isBlank())) {
            redirectAttributes.addFlashAttribute("mensaje", "No hay cambios para confirmar.");
            return "redirect:/perfil";
        }

        ServiceTranscaribe.CambioCredencialesPendiente cambio;
        try {
            cambio = service.prepararCambioCredenciales(
                    usuarioActual.getId(), nombre, correo, password);
        } catch (IllegalArgumentException e) {
            model.addAttribute("usuario", usuarioActual);
            model.addAttribute("perfilNombre", nombre);
            model.addAttribute("perfilCorreo", correo);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("codigoPendiente", false);
            return "usuarios/cuenta/perfil";
        }

        String codigo = generarCodigoPerfil();
        try {
            emailService.enviarCodigoVerificacionCambioCredenciales(
                    usuarioActual.getCorreo(), usuarioActual.getNombre(), codigo);
        } catch (MailSendException e) {
            LOGGER.error("No se pudo enviar el código de confirmación de cambios de perfil.", e);
            model.addAttribute("usuario", usuarioActual);
            model.addAttribute("perfilNombre", nombre);
            model.addAttribute("perfilCorreo", correo);
            model.addAttribute("error", "No se pudo enviar el código al correo actual. Inténtalo nuevamente.");
            model.addAttribute("codigoPendiente", false);
            return "usuarios/cuenta/perfil";
        }

        session.setAttribute(DESAFIO_CAMBIO_PERFIL, new DesafioCambioPerfil(
                cambio, codigo, Instant.now().plus(VIGENCIA_CODIGO_PERFIL).toEpochMilli(),
                0, usuarioActual.getCorreo()));
        redirectAttributes.addFlashAttribute("mensaje", "Enviamos un código de confirmación a tu correo actual.");
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/verificar")
    public String verificarCambioPerfil(@RequestParam String codigo,
                                        HttpSession session,
                                        HttpServletRequest request,
                                        HttpServletResponse response,
                                        RedirectAttributes redirectAttributes) {
        Usuario usuarioActual = obtenerUsuarioLogueado();
        if (usuarioActual == null) {
            return "redirect:/login";
        }

        DesafioCambioPerfil desafio = (DesafioCambioPerfil) session.getAttribute(DESAFIO_CAMBIO_PERFIL);
        if (desafio == null) {
            redirectAttributes.addFlashAttribute("error", "Solicita un código antes de confirmar los cambios.");
            return "redirect:/perfil";
        }
        if (Instant.now().toEpochMilli() >= desafio.expiraEn()) {
            session.removeAttribute(DESAFIO_CAMBIO_PERFIL);
            redirectAttributes.addFlashAttribute("error", "El código venció. Solicita uno nuevo.");
            return "redirect:/perfil";
        }
        if (!usuarioActual.getId().equals(desafio.cambio().idUsuario())) {
            session.removeAttribute(DESAFIO_CAMBIO_PERFIL);
            redirectAttributes.addFlashAttribute("error", "La solicitud ya no es válida. Inténtalo nuevamente.");
            return "redirect:/perfil";
        }
        if (!MessageDigest.isEqual(
                desafio.codigo().getBytes(StandardCharsets.UTF_8),
                (codigo == null ? "" : codigo.trim()).getBytes(StandardCharsets.UTF_8))) {
            int intentos = desafio.intentos() + 1;
            if (intentos >= MAX_INTENTOS_CODIGO_PERFIL) {
                session.removeAttribute(DESAFIO_CAMBIO_PERFIL);
                redirectAttributes.addFlashAttribute("error", "Se agotaron los intentos. Solicita un nuevo código.");
            } else {
                session.setAttribute(DESAFIO_CAMBIO_PERFIL, desafio.conIntentos(intentos));
                redirectAttributes.addFlashAttribute("error",
                        "El código no es correcto. Te quedan " + (MAX_INTENTOS_CODIGO_PERFIL - intentos) + " intentos.");
            }
            return "redirect:/perfil";
        }

        if (!service.aplicarCambioCredenciales(desafio.cambio())) {
            session.removeAttribute(DESAFIO_CAMBIO_PERFIL);
            redirectAttributes.addFlashAttribute("error",
                    "No se pudieron aplicar los cambios. Verifica que el correo siga disponible.");
            return "redirect:/perfil";
        }

        session.removeAttribute(DESAFIO_CAMBIO_PERFIL);
        Usuario usuarioActualizado = usuarioRepository.findById(usuarioActual.getId()).orElseThrow();
        UserDetails detallesActualizados = userDetailsService.loadUserByUsername(usuarioActualizado.getCorreo());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                detallesActualizados, null, detallesActualizados.getAuthorities()));
        new HttpSessionSecurityContextRepository().saveContext(
                SecurityContextHolder.getContext(), request, response);
        redirectAttributes.addFlashAttribute("mensaje", "¡Cambios confirmados y guardados correctamente!");
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/reenviar-codigo")
    public String reenviarCodigoCambioPerfil(HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario usuarioActual = obtenerUsuarioLogueado();
        if (usuarioActual == null) {
            return "redirect:/login";
        }
        DesafioCambioPerfil desafio = (DesafioCambioPerfil) session.getAttribute(DESAFIO_CAMBIO_PERFIL);
        if (desafio == null
                || Instant.now().toEpochMilli() >= desafio.expiraEn()
                || !usuarioActual.getId().equals(desafio.cambio().idUsuario())) {
            session.removeAttribute(DESAFIO_CAMBIO_PERFIL);
            redirectAttributes.addFlashAttribute("error", "Solicita nuevamente los cambios para recibir un código.");
            return "redirect:/perfil";
        }

        String codigo = generarCodigoPerfil();
        try {
            emailService.enviarCodigoVerificacionCambioCredenciales(
                    desafio.correoVerificacion(), usuarioActual.getNombre(), codigo);
        } catch (MailSendException e) {
            LOGGER.error("No se pudo reenviar el código de confirmación de cambios de perfil.", e);
            redirectAttributes.addFlashAttribute("error", "No se pudo reenviar el código. Inténtalo nuevamente.");
            return "redirect:/perfil";
        }
        session.setAttribute(DESAFIO_CAMBIO_PERFIL, new DesafioCambioPerfil(
                desafio.cambio(), codigo, Instant.now().plus(VIGENCIA_CODIGO_PERFIL).toEpochMilli(),
                0, desafio.correoVerificacion()));
        redirectAttributes.addFlashAttribute("mensaje", "Enviamos un nuevo código a tu correo actual.");
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/cancelar-cambio")
    public String cancelarCambioPerfil(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute(DESAFIO_CAMBIO_PERFIL);
        redirectAttributes.addFlashAttribute("mensaje", "Se canceló la solicitud de cambios.");
        return "redirect:/perfil";
    }

    private String generarCodigoPerfil() {
        return String.format(Locale.ROOT, "%06d", GENERADOR_CODIGOS.nextInt(1_000_000));
    }

    private record DesafioCambioPerfil(
            ServiceTranscaribe.CambioCredencialesPendiente cambio,
            String codigo,
            long expiraEn,
            int intentos,
            String correoVerificacion) implements Serializable {
        private DesafioCambioPerfil conIntentos(int nuevosIntentos) {
            return new DesafioCambioPerfil(cambio, codigo, expiraEn, nuevosIntentos, correoVerificacion);
        }
    }

    @GetMapping("/historial")
    public String historial(@RequestParam(defaultValue = "") String buscar,
                            @RequestParam(defaultValue = "") String tipo,
                            @RequestParam(defaultValue = "false") boolean buscarPorTarjeta,
                            @RequestParam(required = false) Integer tarjetaSeleccionada,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        Usuario usuario = obtenerUsuarioLogueado();
        
        if (usuario == null) {
            return "redirect:/login";
        }

        boolean filtrarPorTarjeta = buscarPorTarjeta || tarjetaSeleccionada != null;
        model.addAttribute("usuario", usuario);
        List<com.transcaribe.transcaribe.Model.Transaccion> filtradas =
                transaccionService.obtenerTransaccionesPorUsuario(usuario).stream()
                        .filter(t -> buscar.isBlank()
                                || (t.getTipo() != null && t.getTipo().toLowerCase(Locale.ROOT)
                                        .contains(buscar.trim().toLowerCase(Locale.ROOT))))
                        .filter(t -> !filtrarPorTarjeta
                                || (tarjetaSeleccionada != null
                                        && tarjetaSeleccionada >= 0
                                        && tarjetaSeleccionada < usuario.getTarjetas().size()
                                        && coincideTarjeta(
                                                t, usuario.getTarjetas().get(tarjetaSeleccionada).getNumeroTarjeta())))
                        .filter(t -> tipo.isBlank() || coincideTipo(t.getTipo(), tipo))
                        .toList();
        int totalPages = Math.max(1, (int) Math.ceil(filtradas.size() / 10.0));
        int paginaSegura = Math.min(Math.max(page, 0), totalPages - 1);
        int desde = paginaSegura * 10;
        int hasta = Math.min(desde + 10, filtradas.size());
        model.addAttribute("transacciones", filtradas.subList(desde, hasta));
        model.addAttribute("buscar", buscar);
        model.addAttribute("tipoSeleccionado", tipo);
        model.addAttribute("buscarPorTarjeta", filtrarPorTarjeta);
        model.addAttribute("tarjetaSeleccionada", tarjetaSeleccionada);
        model.addAttribute("currentPage", paginaSegura);
        model.addAttribute("totalPages", totalPages);
        return "usuarios/cuenta/historial";
    }

    private boolean coincideTarjeta(Transaccion transaccion, String numeroTarjeta) {
        if (coincideNumeroTarjeta(transaccion.getTarjetaTranscaribe(), numeroTarjeta)) {
            return true;
        }

        String tipoTransaccion = transaccion.getTipo();
        if (tipoTransaccion == null) {
            return false;
        }

        int inicioNumero = tipoTransaccion.indexOf('[');
        int finNumero = tipoTransaccion.indexOf(']', inicioNumero + 1);
        if (inicioNumero >= 0 && finNumero > inicioNumero) {
            return coincideNumeroTarjeta(
                    tipoTransaccion.substring(inicioNumero + 1, finNumero), numeroTarjeta);
        }

        int inicioTarjeta = tipoTransaccion.toLowerCase(Locale.ROOT).indexOf("tarjeta:");
        if (inicioTarjeta >= 0) {
            return coincideNumeroTarjeta(
                    tipoTransaccion.substring(inicioTarjeta + "tarjeta:".length()), numeroTarjeta);
        }

        int inicioPasaje = tipoTransaccion.toLowerCase(Locale.ROOT).indexOf("pasaje:");
        return inicioPasaje >= 0 && coincideNumeroTarjeta(
                tipoTransaccion.substring(inicioPasaje + "pasaje:".length()), numeroTarjeta);
    }

    private boolean coincideNumeroTarjeta(String numeroGuardado, String numeroTarjeta) {
        if (numeroGuardado == null || numeroTarjeta == null) {
            return false;
        }

        String numeroGuardadoNormalizado = numeroGuardado.replaceAll("\\D", "");
        String numeroTarjetaNormalizado = numeroTarjeta.replaceAll("\\D", "");
        if (numeroGuardadoNormalizado.isEmpty() || numeroTarjetaNormalizado.isEmpty()) {
            return false;
        }

        return numeroGuardado.contains("*")
                ? numeroTarjetaNormalizado.endsWith(numeroGuardadoNormalizado)
                : numeroGuardadoNormalizado.equals(numeroTarjetaNormalizado);
    }

    @GetMapping("/historial/{id}/recibo")
    public ResponseEntity<byte[]> descargarRecibo(@PathVariable String id) throws IOException {
        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Transaccion transaccion = transaccionService.obtenerTransaccionDeUsuario(id, usuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] pdf = reciboPdfService.generarRecibo(transaccion);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("recibo-" + transaccion.getId() + ".pdf", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(pdf.length);
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    private boolean coincideTipo(String tipoTransaccion, String filtro) {
        if (tipoTransaccion == null) {
            return false;
        }
        String tipoNormalizado = tipoTransaccion.toLowerCase(Locale.ROOT);
        return switch (filtro.toLowerCase(Locale.ROOT)) {
            case "recarga" -> tipoNormalizado.contains("recarga");
            case "pasaje" -> tipoNormalizado.contains("pasaje");
            default -> true;
        };
    }

    @GetMapping("/rutas")
    public String rutas(@RequestParam(defaultValue = "") String buscar,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        Usuario usuario = obtenerUsuarioLogueado();

        if (usuario == null) {
            return "redirect:/login";
        }

        model.addAttribute("usuario", usuario);
        List<PreferenciaRutaNotificacion> rutasFavoritasConPreferencia = usuario.getRutasFavoritas().stream()
                .map(usuario::obtenerPreferenciaNotificacionRuta)
                .toList();
        model.addAttribute("rutasFavoritasConPreferencia", rutasFavoritasConPreferencia);
        List<String> rutas = busService.obtenerTodasLasRutas().stream()
                .filter(r -> buscar.isBlank() || r.toLowerCase(Locale.ROOT)
                        .contains(buscar.trim().toLowerCase(Locale.ROOT)))
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil(rutas.size() / 20.0));
        int paginaSegura = Math.min(Math.max(page, 0), totalPages - 1);
        int desde = paginaSegura * 20;
        int hasta = Math.min(desde + 20, rutas.size());
        model.addAttribute("rutasDisponibles", rutas.subList(desde, hasta));
        model.addAttribute("buscar", buscar);
        model.addAttribute("currentPage", paginaSegura);
        model.addAttribute("totalPages", totalPages);
        return "usuarios/cuenta/rutas";
    }

    @PostMapping("/rutas/favorito/agregar")
    public String agregarFavorito(@RequestParam String ruta, Model model) {
        Usuario usuario = obtenerUsuarioLogueado();

        if (usuario == null) {
            return "redirect:/login";
        }

        usuario.agregarRutaFavorita(ruta);
        usuarioRepository.save(usuario);

        return "redirect:/rutas";
    }

    @PostMapping("/rutas/favorito/eliminar")
    public String eliminarFavorito(@RequestParam String ruta, Model model) {
        Usuario usuario = obtenerUsuarioLogueado();

        if (usuario == null) {
            return "redirect:/login";
        }

        usuario.eliminarRutaFavorita(ruta);
        usuarioRepository.save(usuario);

        return "redirect:/rutas";
    }

    @PostMapping("/rutas/favorito/notificaciones")
    public String guardarPreferenciaNotificacion(
            @RequestParam String ruta,
            @RequestParam(defaultValue = "false") boolean notificacionesActivas,
            @RequestParam String horaDesde,
            @RequestParam String horaHasta,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Usuario usuario = obtenerUsuarioLogueado();
        if (usuario == null) {
            return "redirect:/login";
        }
        if (!usuario.getRutasFavoritas().contains(ruta)) {
            redirectAttributes.addFlashAttribute("error", "Solo puedes configurar notificaciones para rutas favoritas.");
            return "redirect:/rutas";
        }

        try {
            LocalTime desde = LocalTime.parse(horaDesde);
            LocalTime hasta = LocalTime.parse(horaHasta);
            if (!desde.isBefore(hasta)) {
                redirectAttributes.addFlashAttribute("error",
                        "La hora inicial debe ser anterior a la hora final.");
                return "redirect:/rutas";
            }

            usuario.guardarPreferenciaNotificacionRuta(
                    new PreferenciaRutaNotificacion(ruta, notificacionesActivas, desde, hasta));
            usuarioRepository.save(usuario);
            redirectAttributes.addFlashAttribute("mensaje",
                    notificacionesActivas
                            ? "Horario de notificaciones guardado para " + ruta + "."
                            : "Notificaciones desactivadas para " + ruta + ".");
        } catch (DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("error", "Selecciona un rango horario válido.");
        }
        return "redirect:/rutas";
    }
}