package com.transcaribe.transcaribe.service;

import com.transcaribe.transcaribe.Repository.TransaccionRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.Repository.HorarioConductorRepository;
import com.transcaribe.transcaribe.Repository.AvisoAdministrativoRepository;
import com.transcaribe.transcaribe.Model.HorarioConductor;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class ExcelReportServiceTest {

    @Test
    void generaUnLibroValidoConAnchosDeColumnaEstables() throws IOException {
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        TransaccionRepository transaccionRepository = mock(TransaccionRepository.class);
        HorarioConductorRepository horarioRepository = mock(HorarioConductorRepository.class);
        AvisoAdministrativoRepository avisoRepository = mock(AvisoAdministrativoRepository.class);
        when(usuarioRepository.streamAll()).thenReturn(Stream.empty());
        when(transaccionRepository.streamByFechaEnRango(
                LocalDate.of(2026, 1, 1).atStartOfDay(),
                LocalDate.of(2027, 1, 1).atStartOfDay())).thenReturn(Stream.empty());

        ExcelReportService service = new ExcelReportService(
                usuarioRepository, transaccionRepository, horarioRepository, avisoRepository);
        ByteArrayOutputStream report = new ByteArrayOutputStream();
        service.generarReporte("anual", 2026, null, null, true, true, false, false, report);

        try (var workbook = WorkbookFactory.create(
                new ByteArrayInputStream(report.toByteArray()))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Resumen Usuarios", workbook.getSheetAt(0).getSheetName());
            assertEquals("Año 2026", workbook.getSheetAt(1).getSheetName());
            assertEquals("ID", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals(18 * 256, workbook.getSheetAt(0).getColumnWidth(0));
        }
        verify(transaccionRepository).streamByFechaEnRango(
                LocalDate.of(2026, 1, 1).atStartOfDay(),
                LocalDate.of(2027, 1, 1).atStartOfDay());
    }

    @Test
    void exportaSoloHistorialDeRutasYavisosParaElPeriodoSeleccionado() throws IOException {
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        TransaccionRepository transaccionRepository = mock(TransaccionRepository.class);
        HorarioConductorRepository horarioRepository = mock(HorarioConductorRepository.class);
        AvisoAdministrativoRepository avisoRepository = mock(AvisoAdministrativoRepository.class);
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2027, 1, 1);
        LocalDateTime inicioDateTime = inicio.atStartOfDay();
        LocalDateTime finDateTime = fin.atStartOfDay();
        when(horarioRepository.streamByFechaEnRango(inicio, fin))
                .thenReturn(Stream.of(new HorarioConductor(
                        "driver-1", "bus-1", "R1", LocalDate.of(2026, 3, 5),
                        LocalTime.of(8, 0), LocalTime.of(9, 0))));
        when(avisoRepository.streamByFechaCreacionEnRango(inicioDateTime, finDateTime))
                .thenReturn(Stream.empty());

        ExcelReportService service = new ExcelReportService(
                usuarioRepository, transaccionRepository, horarioRepository, avisoRepository);
        ByteArrayOutputStream report = new ByteArrayOutputStream();
        service.generarReporte("anual", 2026, null, null, false, false, true, true, report);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(report.toByteArray()))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Rutas conductores", workbook.getSheetAt(0).getSheetName());
            assertEquals("Historial avisos", workbook.getSheetAt(1).getSheetName());
            assertEquals("R1", workbook.getSheetAt(0).getRow(1).getCell(5).getStringCellValue());
            assertEquals("driver-1", workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
        }
        verify(usuarioRepository, never()).streamAll();
        verify(transaccionRepository, never()).streamByFechaEnRango(inicioDateTime, finDateTime);
    }
}
