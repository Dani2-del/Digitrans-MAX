package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Model.Bus;
import com.transcaribe.transcaribe.Model.Tarjeta;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.TransaccionRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;
import java.math.BigDecimal; 
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service 
public class BusService {

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    public void crearBusDePrueba() {
        List<String> rutasCartagena = List.of("T100E", "T101", "T102", "T103", 
                                            "X101", "X102", "X103", "X104", "X105", "X106",
                                            "A101", "A102", "A103", "A104", "A105", "A107",
                                            "A108", "A111", "A114", "A117", "A118", "C001", 
                                            "C016", "C017", "C018");

        Bus nuevoBus = new Bus("B-210", rutasCartagena);
        busRepository.save(nuevoBus);
    }

    public String pagarPasajeConTarjetaEspecifica(String usuarioId, String busId, String numeroTarjeta) {
    Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Bus bus = busRepository.findById(busId)
            .orElseThrow(() -> new RuntimeException("Bus no encontrado"));

    if (bus.getRutas() == null || bus.getRutas().isEmpty()) {
        return "Este bus no tiene rutas asignadas.";
    }

    Random rand = new Random();
    String rutaSeleccionada = bus.getRutas().get(rand.nextInt(bus.getRutas().size()));

    BigDecimal costo = BigDecimal.valueOf(TarifaTranscaribe.VALOR_PASAJE_COP);

    Tarjeta tarjetaSeleccionada = usuario.getTarjetas().stream()
            .filter(t -> t.getNumeroTarjeta().equals(numeroTarjeta))
            .findFirst()
            .orElse(null);

    if (tarjetaSeleccionada == null) {
        return "La tarjeta seleccionada no es válida.";
    }

    if (tarjetaSeleccionada.getSaldo() != null && tarjetaSeleccionada.getSaldo().compareTo(costo) >= 0) {
        
        BigDecimal nuevoSaldo = tarjetaSeleccionada.getSaldo().subtract(costo);
        tarjetaSeleccionada.setSaldo(nuevoSaldo);
        
        usuarioRepository.save(usuario);

        Transaccion t = new Transaccion(
            costo.doubleValue(), 
            LocalDateTime.now(), 
            "PASAJE [" + numeroTarjeta + "] - Ruta: " + rutaSeleccionada, 
            usuario
        );
        transaccionRepository.save(t);

        return "Cobro exitoso. Ruta: " + rutaSeleccionada + 
               ". Tarjeta: " + numeroTarjeta + 
               ". Saldo restante: $" + nuevoSaldo;
    } else {
        return "Saldo insuficiente en la tarjeta " + numeroTarjeta;
    }
}
    public List<String> obtenerTodasLasRutas() {
        return busRepository.findAll().stream()
                .filter(Bus::isActivo)
                .filter(b -> b.getRutas() != null)
                .flatMap(b -> b.getRutas().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}

