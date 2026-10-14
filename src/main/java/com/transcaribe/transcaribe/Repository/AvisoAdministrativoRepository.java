package com.transcaribe.transcaribe.Repository;

import com.transcaribe.transcaribe.Model.AvisoAdministrativo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

public interface AvisoAdministrativoRepository extends MongoRepository<AvisoAdministrativo, String> {

    @Query(value = "{ 'destinatarios': ?0, 'descartada_por': { $ne: ?0 } }",
            sort = "{ 'fecha_creacion': -1 }")
    List<AvisoAdministrativo> findPendientesParaUsuario(String usuarioId);

    List<AvisoAdministrativo> findTop2ByOrderByFechaCreacionDesc();

    List<AvisoAdministrativo> findAllByOrderByFechaCreacionDesc();

    @Query(value = "{}", sort = "{ 'fecha_creacion': -1 }")
    Stream<AvisoAdministrativo> streamAll();

    @Query(value = "{ 'fecha_creacion': { '$gte': ?0, '$lt': ?1 } }",
            sort = "{ 'fecha_creacion': -1 }")
    Stream<AvisoAdministrativo> streamByFechaCreacionEnRango(LocalDateTime inicio, LocalDateTime fin);
}
