package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.service.ServiceTranscaribe;
import org.springframework.mail.MailException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private ServiceTranscaribe service;

    @GetMapping("/login")
    public String login() {
        return "usuarios/auth/login"; 
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/mapa-rutas")
    public String mapaRutas(Authentication authentication, Model model) {
        String menuUrl = "/";
        String menuLabel = "Volver al inicio";
        boolean esAdmin = false;
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            esAdmin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            boolean esConductor = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_CONDUCTOR".equals(authority.getAuthority()));
            menuUrl = esAdmin ? "/admin/dashboard" : esConductor ? "/conductor/panel" : "/menu";
            menuLabel = esAdmin ? "Volver al panel" : "Volver al menú";
        }
        model.addAttribute("esAdmin", esAdmin);
        model.addAttribute("menuUrl", menuUrl);
        model.addAttribute("menuLabel", menuLabel);
        return "mapa-rutas";
    }

    @GetMapping("/registro")
    public String registro() {
        return "usuarios/auth/registro";
    }

    @PostMapping("/registro")
    public String registrar(@RequestParam("username") String nombre,
                            @RequestParam String correo,
                            @RequestParam("password") String contrasena,
                            @RequestParam String numeroTarjeta,
                            Model model) {
        try {
            boolean ok = service.registrar(nombre, correo, contrasena, numeroTarjeta);
            if (ok) {
                return "redirect:/verificar-otp?correo=" + correo;
            }
            model.addAttribute("error", "Error al registrar. El correo ya existe o la tarjeta es inválida.");
        } catch (MailException e) {
            LOGGER.error("No se pudo enviar el correo de verificación del registro.", e);
            model.addAttribute("correo", correo);
            model.addAttribute("correoNuevo", correo);
            model.addAttribute("error", "La cuenta se creó, pero no pudimos enviar el código. Intenta reenviarlo.");
            return "usuarios/auth/verificar-codigo";
        }
        return "usuarios/auth/registro";
    }

    @GetMapping("/verificar-otp")
    public String mostrarVerificacion(@RequestParam String correo, Model model) {
        model.addAttribute("correo", correo);
        model.addAttribute("correoNuevo", correo);
        return "usuarios/auth/verificar-codigo";
    }

    @PostMapping("/verificar-otp/reenviar")
    public String reenviarOtp(@RequestParam String correoActual,
                              @RequestParam String correoNuevo,
                              Model model) {
        model.addAttribute("correo", correoActual);
        model.addAttribute("correoNuevo", correoNuevo);
        try {
            ServiceTranscaribe.ResultadoReenvioCodigo resultado =
                    service.reenviarCodigoVerificacion(correoActual, correoNuevo);
            switch (resultado) {
                case ENVIADO -> {
                    model.addAttribute("correo", correoNuevo.trim());
                    model.addAttribute("correoNuevo", correoNuevo.trim());
                    model.addAttribute("mensaje", "Enviamos un nuevo código a " + correoNuevo.trim() + ".");
                }
                case CORREO_EN_USO ->
                        model.addAttribute("error", "Ese correo ya está asociado a otra cuenta. Prueba con otro.");
                case CORREO_INVALIDO ->
                        model.addAttribute("error", "Ingresa un correo electrónico válido.");
                case CUENTA_NO_PENDIENTE ->
                        model.addAttribute("error", "No encontramos una cuenta pendiente de verificación.");
            }
        } catch (MailException e) {
            LOGGER.error("No se pudo reenviar el código de verificación.", e);
            model.addAttribute("correo", correoNuevo.trim());
            model.addAttribute("correoNuevo", correoNuevo.trim());
            model.addAttribute("error", "No se pudo enviar el correo. Verifica la configuración SMTP e inténtalo de nuevo.");
        }
        return "usuarios/auth/verificar-codigo";
    }

    @PostMapping("/verificar-otp")
    public String verificarOtp(@RequestParam String correo, @RequestParam String codigo, Model model) {
        boolean validado = service.verificarCodigo(correo, codigo);
        if (validado) {
            model.addAttribute("mensaje", "¡Cuenta activada! Ya puedes iniciar sesión ✅");
            return "usuarios/auth/login";
        }
        model.addAttribute("error", "El código ingresado es incorrecto.");
        model.addAttribute("correo", correo);
        model.addAttribute("correoNuevo", correo);
        return "usuarios/auth/verificar-codigo";
    }
}