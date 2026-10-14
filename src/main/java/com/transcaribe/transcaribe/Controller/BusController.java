package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Model.Tarjeta;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.service.BusService;
import com.transcaribe.transcaribe.service.ServiceTranscaribe;
import com.transcaribe.transcaribe.service.EmailService;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/api/buses")
public class BusController {

    @Autowired
    private BusService busService;

    @Autowired
    private ServiceTranscaribe service;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private EmailService emailService;

    @PostMapping("/pagar/{usuarioId}/{busId}")
    @ResponseBody
    public ResponseEntity<String> pagar(
            @PathVariable String usuarioId, 
            @PathVariable String busId,
            @RequestParam String numeroTarjeta) {
        try {
            String resultado = busService.pagarPasajeConTarjetaEspecifica(usuarioId, busId, numeroTarjeta);
            if (resultado.contains("Saldo insuficiente") || resultado.contains("no es válida")) {
                return ResponseEntity.badRequest().body(resultado);
            }
            try {
                Usuario usuarioDestino = service.buscarPorId(usuarioId);
                if (usuarioDestino != null) {
                    double costoPasaje = (double) TarifaTranscaribe.VALOR_PASAJE_COP;
                    String saldoRestante = resultado.split("\\$")[1]; 
                    emailService.enviarNotificacionGasto(
                        usuarioDestino.getCorreo(),
                        usuarioDestino.getNombre(),
                        costoPasaje,
                        saldoRestante
                    );

                    Tarjeta tarjetaUsada = usuarioDestino.getTarjetas().stream()
                        .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
                        .findFirst()
                        .orElse(null);
                    if (tarjetaUsada != null) {
                        service.verificarYNotificarLimiteSaldo(usuarioDestino, tarjetaUsada);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al enviar el correo de ticket: " + e.getMessage());
            }         
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al procesar el pago: " + e.getMessage());
        }
    }

    @PostMapping("/setup")
    @ResponseBody
    public ResponseEntity<String> setup() {
        busService.crearBusDePrueba();
        return ResponseEntity.ok("Bus de prueba creado exitosamente con sus rutas originales");
    }
    @PostMapping("/simular-viaje")
    public String simularViaje(@RequestParam String usuarioId, 
                               @RequestParam String numeroTarjeta, 
                               RedirectAttributes redirectAttributes) {
        
        try {
            var buses = busRepository.findAll();
            if (buses.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "No hay buses configurados. Ejecuta el setup primero.");
                return "redirect:/menu";
            }
            String busId = buses.get(0).getId(); 
            String resultado = busService.pagarPasajeConTarjetaEspecifica(usuarioId, busId, numeroTarjeta);

            if (resultado.contains("Cobro exitoso")) {
                try {
                    Usuario u = service.buscarPorId(usuarioId);
                    if (u != null) {
                        String saldoRestante = resultado.split("\\$")[1];
                        emailService.enviarNotificacionGasto(
                            u.getCorreo(), 
                            u.getNombre(), 
                            (double) TarifaTranscaribe.VALOR_PASAJE_COP,
                            saldoRestante
                        );

                        Tarjeta tarjetaUsada = u.getTarjetas().stream()
                            .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
                            .findFirst()
                            .orElse(null);
                        if (tarjetaUsada != null) {
                            service.verificarYNotificarLimiteSaldo(u, tarjetaUsada);
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Error correo simulación: " + ex.getMessage());
                }
                redirectAttributes.addFlashAttribute("mensaje", resultado + " ✅");
            } else {
                redirectAttributes.addFlashAttribute("error", resultado + " ❌");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error en la simulación: " + e.getMessage());
        }

        return "redirect:/menu";
    }
}