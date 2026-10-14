package com.transcaribe.transcaribe.Controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.ExcelReportService;
import com.transcaribe.transcaribe.service.ServiceTranscaribe;
import com.transcaribe.transcaribe.service.AvisoAdministrativoService;
import org.springframework.http.ContentDisposition;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UsuarioRepository usuarioRepository;
    private final ServiceTranscaribe serviceTranscaribe;
    private final ExcelReportService excelService;
    private final BusRepository busRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired
    private AvisoAdministrativoService avisoAdministrativoService;

    public AdminController(UsuarioRepository usuarioRepository,
                           ServiceTranscaribe serviceTranscaribe,
                           ExcelReportService excelService,
                           BusRepository busRepository,
                           MongoTemplate mongoTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.serviceTranscaribe = serviceTranscaribe;
        this.excelService = excelService;
        this.busRepository = busRepository;
        this.mongoTemplate = mongoTemplate;
    }

    private static final int TAMANO_PAGINA = 20;

    @GetMapping("/mapa-rutas/editar")
    public String editarGeometriasRuta() {
        return "admin/editar-rutas-mapa";
    }

    @GetMapping("/dashboard")
    public String mostrarDashboard(Model model,
                                   org.springframework.security.core.Authentication authentication) {
        model.addAttribute("totalUsuarios", usuarioRepository.count());
        usuarioRepository.findByCorreo(authentication.getName()).ifPresent(usuario ->
                model.addAttribute("alertasAdmin", avisoAdministrativoService.obtenerPendientes(usuario.getId())));
        model.addAttribute("alertaReturnTo", "/admin/dashboard");
        return "admin/inicio";
    }

    @GetMapping("/usuarios")
    public String mostrarUsuarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String fechaDesde,
            @RequestParam(defaultValue = "") String fechaHasta,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String verificacion,
            Model model) {
        String busqueda = search == null ? "" : search.trim();
        LocalDate desde = parseFechaUsuario(fechaDesde);
        LocalDate hasta = parseFechaUsuario(fechaHasta);
        boolean filtrosValidos = true;

        if (!fechaDesde.isBlank() && desde == null) {
            model.addAttribute("errorFiltro", "La fecha inicial no tiene un formato válido.");
            filtrosValidos = false;
        }
        if (!fechaHasta.isBlank() && hasta == null) {
            model.addAttribute("errorFiltro", "La fecha final no tiene un formato válido.");
            filtrosValidos = false;
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            model.addAttribute("errorFiltro", "La fecha inicial no puede ser posterior a la fecha final.");
            filtrosValidos = false;
        }

        Set<String> rolesValidos = Set.of("", Usuario.ROLE_USER, Usuario.ROLE_CONDUCTOR, Usuario.ROLE_ADMIN);
        if (!rolesValidos.contains(role)) {
            model.addAttribute("errorFiltro", "El rol seleccionado no es válido.");
            filtrosValidos = false;
        }
        if (!Set.of("", "activos", "inactivos").contains(estado)) {
            model.addAttribute("errorFiltro", "El estado de cuenta seleccionado no es válido.");
            filtrosValidos = false;
        }
        if (!Set.of("", "verificados", "pendientes").contains(verificacion)) {
            model.addAttribute("errorFiltro", "El estado de verificación seleccionado no es válido.");
            filtrosValidos = false;
        }

        Query filtro = new Query();
        if (!busqueda.isBlank()) {
            String busquedaLiteral = Pattern.quote(busqueda);
            filtro.addCriteria(new Criteria().orOperator(
                    Criteria.where("nombre").regex(busquedaLiteral, "i"),
                    Criteria.where("correo").regex(busquedaLiteral, "i")));
        }
        if (!role.isBlank()) {
            filtro.addCriteria(Criteria.where("role").is(role));
        }
        if ("activos".equals(estado)) {
            filtro.addCriteria(Criteria.where("activo").is(true));
        } else if ("inactivos".equals(estado)) {
            filtro.addCriteria(Criteria.where("activo").is(false));
        }
        if ("verificados".equals(verificacion)) {
            filtro.addCriteria(Criteria.where("verificado").is(true));
        } else if ("pendientes".equals(verificacion)) {
            filtro.addCriteria(Criteria.where("verificado").is(false));
        }
        if (desde != null || hasta != null) {
            filtro.addCriteria(criterioRangoFechaRegistro(desde, hasta));
        }

        long totalFiltrado = filtrosValidos ? mongoTemplate.count(filtro, Usuario.class) : 0;
        int totalPaginas = Math.max(1, (int) Math.ceil((double) totalFiltrado / TAMANO_PAGINA));
        int paginaSegura = Math.min(Math.max(page, 0), totalPaginas - 1);
        PageRequest pageable = PageRequest.of(paginaSegura, TAMANO_PAGINA, Sort.by("nombre").ascending());
        List<Usuario> usuarios = filtrosValidos
                ? mongoTemplate.find(filtro.with(pageable), Usuario.class)
                : List.of();

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("currentPage", paginaSegura);
        model.addAttribute("totalPages", totalPaginas);
        model.addAttribute("totalUsuarios", usuarioRepository.count());
        model.addAttribute("totalFiltrado", totalFiltrado);
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("roleSeleccionado", role);
        model.addAttribute("fechaDesde", fechaDesde);
        model.addAttribute("fechaHasta", fechaHasta);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("verificacionSeleccionada", verificacion);
        model.addAttribute("buses", busRepository.findAll());
        return "admin/dashboard";
    }

    private LocalDate parseFechaUsuario(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(fecha);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    static Criteria criterioRangoFechaRegistro(LocalDate desde, LocalDate hasta) {
        Criteria rangoFecha = Criteria.where("fechaRegistro");
        if (desde != null) {
            rangoFecha.gte(desde.atStartOfDay());
        }
        if (hasta != null) {
            LocalDateTime limiteExclusivo = hasta.plusDays(1).atStartOfDay();
            rangoFecha.lt(limiteExclusivo);
        }
        return rangoFecha;
    }

    @GetMapping("/transacciones")
    public String mostrarTransacciones(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String usuario,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(required = false) Double montoMin,
            @RequestParam(required = false) Double montoMax,
            Model model) {
        int paginaSolicitada = Math.max(page, 0);
        Sort orden = Sort.by(Sort.Direction.DESC, "fecha");
        PageRequest pageable = PageRequest.of(paginaSolicitada, TAMANO_PAGINA, orden);

        String usuarioBuscado = usuario.trim();
        String tipoBuscado = tipo.trim().toLowerCase(Locale.ROOT);
        boolean rangoInvalido = (montoMin != null && montoMin < 0)
                || (montoMax != null && montoMax < 0)
                || (montoMin != null && montoMax != null && montoMin > montoMax);
        boolean tipoInvalido = !List.of("", "recarga", "pasaje", "otros").contains(tipoBuscado);
        List<Transaccion> contenido = List.of();
        long total = 0;

        if (!rangoInvalido && !tipoInvalido) {
            Query filtro = construirFiltroTransacciones(usuarioBuscado, tipoBuscado, montoMin, montoMax);
            total = mongoTemplate.count(filtro, Transaccion.class);
            int totalPaginas = (int) Math.ceil((double) total / TAMANO_PAGINA);
            if (totalPaginas > 0 && paginaSolicitada >= totalPaginas) {
                paginaSolicitada = totalPaginas - 1;
            }
            pageable = PageRequest.of(paginaSolicitada, TAMANO_PAGINA, orden);
            contenido = mongoTemplate.find(filtro.with(pageable), Transaccion.class);
        }

        Page<Transaccion> resultado = new PageImpl<>(contenido, pageable, total);
        model.addAttribute("transacciones", resultado.getContent());
        model.addAttribute("currentPage", resultado.getNumber());
        model.addAttribute("totalPages", resultado.getTotalPages());
        model.addAttribute("totalTransacciones", resultado.getTotalElements());
        model.addAttribute("usuarioBuscado", usuario);
        model.addAttribute("tipoBuscado", tipoBuscado);
        model.addAttribute("montoMin", montoMin);
        model.addAttribute("montoMax", montoMax);
        model.addAttribute("errorFiltro", rangoInvalido
                ? "Verifica los montos: deben ser positivos y el mínimo no puede superar el máximo."
                : tipoInvalido
                        ? "El tipo de transacción seleccionado no es válido."
                        : null);
        return "admin/transacciones";
    }

    private Query construirFiltroTransacciones(
            String usuario, String tipo, Double montoMin, Double montoMax) {
        Query filtro = new Query();
        if (!usuario.isBlank()) {
            List<Usuario> usuarios = usuarioRepository
                    .findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(usuario, usuario)
                    .stream()
                    .filter(resultado -> resultado.getId() != null)
                    .toList();
            filtro.addCriteria(Criteria.where("usuario").in(usuarios));
        }
        switch (tipo) {
            case "recarga" -> filtro.addCriteria(Criteria.where("tipo").regex("recarga", "i"));
            case "pasaje" -> filtro.addCriteria(Criteria.where("tipo").regex("pasaje", "i"));
            case "otros" -> filtro.addCriteria(new Criteria().andOperator(
                    Criteria.where("tipo").not().regex("recarga", "i"),
                    Criteria.where("tipo").not().regex("pasaje", "i")));
            default -> { }
        }
        if (montoMin != null || montoMax != null) {
            Criteria monto = Criteria.where("monto");
            if (montoMin != null) monto.gte(montoMin);
            if (montoMax != null) monto.lte(montoMax);
            filtro.addCriteria(monto);
        }
        return filtro;
    }

    @GetMapping("/reporte/excel")
    public ResponseEntity<StreamingResponseBody> descargarExcel(
            @RequestParam(defaultValue = "todo") String tipo,
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer dia,
            @RequestParam(required = false) String nombreReporte,
            @RequestParam(defaultValue = "true") boolean incluirUsuarios,
            @RequestParam(defaultValue = "true") boolean incluirTransacciones,
            @RequestParam(defaultValue = "false") boolean incluirRutas,
            @RequestParam(defaultValue = "false") boolean incluirAvisos) {

        if (!incluirUsuarios && !incluirTransacciones && !incluirRutas && !incluirAvisos) {
            return ResponseEntity.badRequest().build();
        }

        String nombrePredeterminado = switch (tipo) {
            case "anual"   -> "Reporte_" + anio + ".xlsx";
            case "mensual" -> "Reporte_" + anio + "-" + String.format("%02d", mes) + ".xlsx";
            case "diario"  -> "Reporte_" + anio + "-" + String.format("%02d", mes) + "-" + String.format("%02d", dia) + ".xlsx";
            default        -> "Reporte_Transcaribe.xlsx";
        };
        String nombreArchivo = nombreReporte == null || nombreReporte.isBlank()
                ? nombrePredeterminado
                : nombreReporte.trim()
                        .replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_")
                        .replaceAll("[. ]+$", "");
        if (nombreArchivo.length() > 100) {
            nombreArchivo = nombreArchivo.substring(0, 100).replaceAll("[. ]+$", "");
        }
        if (nombreArchivo.isBlank()) {
            nombreArchivo = "Reporte_Transcaribe";
        }
        if (!nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            nombreArchivo += ".xlsx";
        }

        StreamingResponseBody file = out -> excelService.generarReporte(
                tipo, anio, mes, dia, incluirUsuarios, incluirTransacciones,
                incluirRutas, incluirAvisos, out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(nombreArchivo, StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @PostMapping("/charge")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> chargePassenger(
            @RequestParam String userId, @RequestParam double amount) {
        Map<String, Object> response = new HashMap<>();
        boolean ok = serviceTranscaribe.cobrarPasajePorAdmin(userId, amount);
        if (!ok) {
            response.put("status", "error");
            response.put("message", "Saldo insuficiente o usuario no encontrado");
            return ResponseEntity.badRequest().body(response);
        }
        Usuario actualizado = usuarioRepository.findById(userId).get();
        response.put("status", "ok");
        response.put("usuarioSaldo", actualizado.getSaldoTotal());
        if (!actualizado.getTarjetas().isEmpty())
            response.put("tarjetaSaldo", actualizado.getTarjetas().get(0).getSaldo());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/editUser")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editUser(
            @RequestParam String userId,
            @RequestParam String nombre,
            @RequestParam String correo,
            @RequestParam String role,
            @RequestParam(required = false) String password) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<Usuario> opt = usuarioRepository.findById(userId);
            if (opt.isPresent()) {
                Usuario u = opt.get();
                u.setNombre(nombre);
                u.setCorreo(correo);
                u.setRole(role);
                if (password != null && !password.trim().isEmpty()) u.setPasswordHash(password);

                usuarioRepository.save(u);
                response.put("status", "ok");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "Usuario no encontrado");
                return ResponseEntity.status(404).body(response);
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/deleteUser")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteUser(@RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            usuarioRepository.deleteById(userId);
            response.put("status", "ok");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            return ResponseEntity.status(500).body(response);
        }
    }
}