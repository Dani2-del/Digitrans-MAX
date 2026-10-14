package com.transcaribe.transcaribe.Repository;

import com.transcaribe.transcaribe.Model.HorarioConductor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.util.stream.Stream;

public interface HorarioConductorRepository extends MongoRepository<HorarioConductor, String> {

    List<HorarioConductor> findByConductorIdOrderByFechaAscHoraInicioAsc(String conductorId);

    List<HorarioConductor> findAllByOrderByFechaAscHoraInicioAsc();

    List<HorarioConductor> findAllByOrderByFechaDescHoraInicioDesc();

    @Query(value = "{}", sort = "{ 'fecha': -1, 'hora_inicio': -1 }")
    Stream<HorarioConductor> streamAll();

    @Query(value = "{ 'fecha': { '$gte': ?0, '$lt': ?1 } }",
            sort = "{ 'fecha': -1, 'hora_inicio': -1 }")
    Stream<HorarioConductor> streamByFechaEnRango(LocalDate inicio, LocalDate fin);

    Optional<HorarioConductor> findByIdAndConductorId(String id, String conductorId);

    List<HorarioConductor> findByBusIdAndFecha(String busId, java.time.LocalDate fecha);
}
