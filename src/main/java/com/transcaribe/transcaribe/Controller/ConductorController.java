package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Bus;
import com.transcaribe.transcaribe.Model.HorarioConductor;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.HorarioConductorRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.RutaNotificacionService;
import com.transcaribe.transcaribe.service.EmailService;
import com.transcaribe.transcaribe.service.AvisoAdministrativoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ConductorController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private RutaNotificacionService rutaNotificacionService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AvisoAdministrativoService avisoAdministrativoService;

    @Autowired
    private HorarioConductorRepository horarioRepository;

    private Usuario obtenerUsuarioLogueado() {
        String correo = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByCorreo(correo).orElse(null);
    }

    @GetMapping("/choose-view")
    public String chooseView(Model model) {
        Usuario usuario = obtenerUsuarioLogueado();

        if (usuario == null) {
            return "redirect:/login";
        }

        boolean esAdmin = Usuario.ROLE_ADMIN.equals(usuario.getRole());
        boolean esConductor = Usuario.ROLE_CONDUCTOR.equals(usuario.getRole());

        // Un usuario normal no tiene nada que elegir, va directo a su menú
        if (!esAdmin && !esConductor) {
            return "redirect:/menu";
        }

        model.addAttribute("nombre", usuario.getNombre());
        model.addAttribute("usuario", usuario);
        model.addAttribute("alertasAdmin", avisoAdministrativoService.obtenerPendientes(usuario.getId()));
        model.addAttribute("alertaReturnTo", "/choose-view");
        model.addAttribute("esAdmin", esAdmin);
        model.addAttribute("esConductor", esConductor);
        return "choose-view";
    }

    @GetMapping("/conductor/panel")
    public String panel(@RequestParam(required = false) String mensaje,
                         @RequestParam(required = false) String error,
                         @RequestParam(required = false) Integer notificados,
                         @RequestParam(defaultValue = "false") boolean historial,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        Usuario conductor = obtenerUsuarioLogueado();

        if (conductor == null) {
            return "redirect:/login";
        }

        model.addAttribute("usuario", conductor);
        model.addAttribute("alertasAdmin", avisoAdministrativoService.obtenerPendientes(conductor.getId()));
        model.addAttribute("alertaReturnTo", "/conductor/panel");

        Bus bus = null;
        if (conductor.getBusAsignado() != null) {
            bus = busRepository.findById(conductor.getBusAsignado()).orElse(null);
        }
        model.addAttribute("bus", bus);

        List<HorarioConductor> horariosConductor =
                horarioRepository.findByConductorIdOrderByFechaAscHoraInicioAsc(conductor.getId());
        model.addAttribute("horarios", horariosConductor.stream()
                .filter(h -> HorarioConductor.ESTADO_PENDIENTE.equals(h.getEstado())
                        || HorarioConductor.ESTADO_EN_CURSO.equals(h.getEstado()))
                .toList());
        List<HorarioConductor> historialCompleto = horariosConductor.stream()
                .filter(h -> HorarioConductor.ESTADO_FINALIZADA.equals(h.getEstado()))
                .toList();
        int totalPaginasHistorial = Math.max(1, (int) Math.ceil(historialCompleto.size() / 5.0));
        int paginaHistorial = Math.min(Math.max(page, 0), totalPaginasHistorial - 1);
        int inicioHistorial = paginaHistorial * 5;
        int finHistorial = Math.min(inicioHistorial + 5, historialCompleto.size());
        model.addAttribute("historialHorarios", historialCompleto.subList(inicioHistorial, finHistorial));
        model.addAttribute("paginaHistorial", paginaHistorial);
        model.addAttribute("totalPaginasHistorial", totalPaginasHistorial);
        model.addAttribute("verHistorial", historial);
        Map<String, String> busPorHorario = new HashMap<>();
        horariosConductor
                .forEach(horario -> {
                    String busId = obtenerBusId(horario, conductor);
                    if (busId != null) {
                        busRepository.findById(busId).ifPresent(b -> busPorHorario.put(horario.getId(), b.getPlaca()));
                    }
                });
        model.addAttribute("busPorHorario", busPorHorario);

        if ("ok".equals(mensaje)) {
            model.addAttribute("mensaje", "¡Ruta iniciada! Se notificó a " + notificados + " usuario(s).");
        }
        if ("vuelta".equals(mensaje)) {
            model.addAttribute("mensaje", "La ruta cambió a sentido Vuelta.");
        }
        if ("fin".equals(mensaje)) {
            model.addAttribute("mensaje", "Ruta finalizada correctamente.");
        }
        if ("sinbus".equals(error)) {
            model.addAttribute("error", "No tienes un bus asignado. Contacta al administrador.");
        }
        if ("horarioinvalido".equals(error)) {
            model.addAttribute("error", "Ese horario no existe o no te pertenece.");
        }
        if ("estadoinvalido".equals(error)) {
            model.addAttribute("error", "Esa ruta no está en un estado válido para esa acción.");
        }
        if ("sentidoinvalido".equals(error) || "secuencia".equals(error)) {
            model.addAttribute("error", "La ruta debe iniciar en Ida y luego cambiar a Vuelta antes de terminar.");
        }
        if ("busocupado".equals(error)) {
            model.addAttribute("error", "No puedes iniciar la ruta: el bus todavía está siendo utilizado en otra ruta.");
        }

        return "conductor/panel";
    }

    @PostMapping("/conductor/reportar")
    public String reportarIncidencia(@RequestParam String asunto,
                                     @RequestParam String detalle,
                                     org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Usuario conductor = obtenerUsuarioLogueado();
        if (conductor == null) {
            return "redirect:/login";
        }

        if (asunto.isBlank() || detalle.isBlank()) {
            redirectAttributes.addFlashAttribute("errorReporte",
                    "El asunto y el detalle del reporte son obligatorios.");
            return "redirect:/conductor/panel";
        }

        List<String> administradores = usuarioRepository
                .findByRoleAndActivoTrue(Usuario.ROLE_ADMIN)
                .stream()
                .map(Usuario::getCorreo)
                .filter(correo -> correo != null && !correo.isBlank())
                .collect(Collectors.toList());
        try {
            emailService.enviarReporteConductor(
                    administradores,
                    conductor.getNombre() == null || conductor.getNombre().isBlank()
                            ? conductor.getCorreo() : conductor.getNombre(),
                    conductor.getCorreo(),
                    asunto,
                    detalle
            );
            redirectAttributes.addFlashAttribute("mensajeReporte",
                    "Reporte enviado correctamente a los administradores.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorReporte", e.getMessage());
        }
        return "redirect:/conductor/panel";
    }

    @PostMapping("/conductor/iniciar-ruta")
    public synchronized String iniciarRuta(@RequestParam String horarioId,
                                           @RequestParam(required = false) String sentido) {
        Usuario conductor = obtenerUsuarioLogueado();

        if (conductor == null) {
            return "redirect:/login";
        }

        if (sentido == null) {
            return "redirect:/conductor/panel?error=sentidoinvalido";
        }
        String sentidoNormalizado = sentido.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"IDA".equals(sentidoNormalizado) && !"VUELTA".equals(sentidoNormalizado)) {
            return "redirect:/conductor/panel?error=sentidoinvalido";
        }

        HorarioConductor horario = horarioRepository.findByIdAndConductorId(horarioId, conductor.getId()).orElse(null);

        if (horario == null) {
            return "redirect:/conductor/panel?error=horarioinvalido";
        }

        if ("VUELTA".equals(sentidoNormalizado)
                && HorarioConductor.ESTADO_EN_CURSO.equals(horario.getEstado())
                && ("IDA".equals(horario.getSentido()) || horario.getSentido() == null)) {
            horario.setSentido("VUELTA");
            horarioRepository.save(horario);
            return "redirect:/conductor/panel?mensaje=vuelta";
        }

        if (!"IDA".equals(sentidoNormalizado)
                || !HorarioConductor.ESTADO_PENDIENTE.equals(horario.getEstado())) {
            return "redirect:/conductor/panel?error=estadoinvalido";
        }

        String busId = obtenerBusId(horario, conductor);
        if (busId == null || busId.isBlank()) {
            return "redirect:/conductor/panel?error=sinbus";
        }

        Bus bus = busRepository.findById(busId).orElse(null);
        if (bus == null) {
            return "redirect:/conductor/panel?error=sinbus";
        }

        boolean busOcupado = horarioRepository.findAll().stream()
                .anyMatch(otro -> !horario.getId().equals(otro.getId())
                        && busId.equals(obtenerBusId(otro, null))
                        && HorarioConductor.ESTADO_EN_CURSO.equals(otro.getEstado()));
        if (busOcupado) {
            return "redirect:/conductor/panel?error=busocupado";
        }

        horario.setSentido("IDA");
        horario.setEstado(HorarioConductor.ESTADO_EN_CURSO);
        horarioRepository.save(horario);

        int notificados = rutaNotificacionService.notificarInicioRuta(horario, bus.getPlaca());

        return "redirect:/conductor/panel?mensaje=ok&notificados=" + notificados;
    }

    @PostMapping("/conductor/terminar-ruta")
    public String terminarRuta(@RequestParam String horarioId) {
        Usuario conductor = obtenerUsuarioLogueado();

        if (conductor == null) {
            return "redirect:/login";
        }

        HorarioConductor horario = horarioRepository.findByIdAndConductorId(horarioId, conductor.getId()).orElse(null);

        if (horario == null) {
            return "redirect:/conductor/panel?error=horarioinvalido";
        }

        if (!HorarioConductor.ESTADO_EN_CURSO.equals(horario.getEstado())
                || !"VUELTA".equals(horario.getSentido())) {
            if (HorarioConductor.ESTADO_EN_CURSO.equals(horario.getEstado())
                    && !"VUELTA".equals(horario.getSentido())) {
                return "redirect:/conductor/panel?error=secuencia";
            }
            return "redirect:/conductor/panel?error=estadoinvalido";
        }

        horario.setEstado(HorarioConductor.ESTADO_FINALIZADA);
        horarioRepository.save(horario);

        return "redirect:/conductor/panel?mensaje=fin";
    }

    private String obtenerBusId(HorarioConductor horario, Usuario conductor) {
        if (horario.getBusId() != null && !horario.getBusId().isBlank()) {
            return horario.getBusId();
        }
        return conductor == null ? null : conductor.getBusAsignado();
    }
}