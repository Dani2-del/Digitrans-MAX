package com.transcaribe.transcaribe.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.bson.Document;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminControllerTest {

    @Test
    void combinaLasFechasDesdeYHastaEnUnSoloCriterioMongo() {
        var criterio = AdminController.criterioRangoFechaRegistro(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        Query consulta = new Query();

        consulta.addCriteria(criterio);
        Document rango = consulta.getQueryObject().get("fechaRegistro", Document.class);

        assertEquals(2, rango.size());
        assertTrue(rango.containsKey("$gte"));
        assertTrue(rango.containsKey("$lt"));
    }
}
