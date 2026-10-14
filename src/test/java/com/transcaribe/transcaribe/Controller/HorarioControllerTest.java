package com.transcaribe.transcaribe.Controller;

import com.transcaribe.transcaribe.Model.Bus;
import com.transcaribe.transcaribe.Model.HorarioConductor;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.HorarioConductorRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HorarioControllerTest {

    @Test
    void eliminarBusLiberaConductoresDesactivaHorariosYConservaLaPlaca() {
        BusRepository busRepository = mock(BusRepository.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        HorarioConductorRepository horarioRepository = mock(HorarioConductorRepository.class);
        HorarioController controller = new HorarioController();
        ReflectionTestUtils.setField(controller, "busRepository", busRepository);
        ReflectionTestUtils.setField(controller, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(controller, "horarioRepository", horarioRepository);
        ReflectionTestUtils.setField(controller, "emailService", mock(EmailService.class));

        Bus bus = new Bus("B-210", List.of("T100E"));
        bus.setId("bus-1");
        Usuario conductor = new Usuario();
        conductor.setId("conductor-1");
        conductor.setRole(Usuario.ROLE_CONDUCTOR);
        conductor.setBusAsignado("bus-1");
        HorarioConductor horario = new HorarioConductor(
                "conductor-1", "bus-1", "T100E",
                LocalDate.of(2026, 10, 3), LocalTime.of(8, 0), LocalTime.of(10, 0));

        when(busRepository.findById("bus-1")).thenReturn(Optional.of(bus));
        when(usuarioRepository.findAll()).thenReturn(List.of(conductor));
        when(horarioRepository.findAll()).thenReturn(List.of(horario));

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String result = controller.eliminarBus("bus-1", redirectAttributes);

        assertEquals("redirect:/admin/horarios/buses", result);
        assertNull(conductor.getBusAsignado());
        assertEquals(HorarioConductor.ESTADO_DESACTIVADA, horario.getEstado());
        assertEquals("B-210", horario.getBusPlaca());
        verify(usuarioRepository).saveAll(List.of(conductor));
        verify(horarioRepository).saveAll(List.of(horario));
        verify(busRepository).delete(bus);
    }
}
