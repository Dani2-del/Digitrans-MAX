package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Optional;

@Controller
public class PasswordRecoveryController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/olvido-password")
    public String mostrarOlvidoPassword() {
        return "usuarios/password-recovery/olvido-password";
    }

    @PostMapping("/olvido-password")
    public String procesarOlvidoPassword(@RequestParam("correo") String correo, RedirectAttributes redirectAttributes) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(correo);
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            String codigo = String.format("%06d", (int)(Math.random() * 1000000));
            usuario.setResetToken(codigo);
            usuarioRepository.save(usuario);
            emailService.enviarCodigoRecuperacion(usuario.getCorreo(), usuario.getNombre(), codigo);
            redirectAttributes.addFlashAttribute("mensaje", "Código enviado a tu correo ✅");
            return "redirect:/verificar-codigo-recuperar-password?correo=" + correo;
        }
        redirectAttributes.addFlashAttribute("error", "El correo no está registrado ❌");
        return "redirect:/olvido-password";
    }

    @GetMapping("/verificar-codigo-recuperar-password")
    public String mostrarVistaCodigo(@RequestParam("correo") String correo, Model model) {
        model.addAttribute("correo", correo);
        return "usuarios/password-recovery/verificar-codigo-recuperar-password"; 
    }

    @PostMapping("/validar-codigo-restablecimiento")
    public String validarCodigo(@RequestParam("correo") String correo, @RequestParam("codigo") String codigo, RedirectAttributes redirectAttributes) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElse(null);
        if (usuario != null && codigo.equals(usuario.getResetToken())) {
            redirectAttributes.addFlashAttribute("correo", correo);
            redirectAttributes.addFlashAttribute("codigo", codigo);
            return "redirect:/restablecer-password";
        }
        redirectAttributes.addFlashAttribute("error", "Código incorrecto o expirado ❌");
        return "redirect:/verificar-codigo-recuperar-password?correo=" + correo;
    }

    @GetMapping("/restablecer-password")
    public String mostrarVistaRestablecer(Model model) {
        if (!model.containsAttribute("correo")) return "redirect:/olvido-password"; 
        return "usuarios/password-recovery/restablecer-password"; 
    }

    @PostMapping("/confirmar-restablecimiento")
    public String confirmarRestablecimiento(@RequestParam("correo") String correo, @RequestParam("codigo") String codigo, @RequestParam("nuevaPassword") String nuevaPassword, RedirectAttributes redirectAttributes) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElse(null);
        if (usuario != null && codigo.equals(usuario.getResetToken())) {
            usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
            usuario.setResetToken(null);
            usuarioRepository.save(usuario);
            redirectAttributes.addFlashAttribute("mensaje", "Contraseña actualizada correctamente ✅");
            return "redirect:/login";
        }
        redirectAttributes.addFlashAttribute("error", "Hubo un problema. Intenta de nuevo.");
        return "redirect:/olvido-password";
    }
}