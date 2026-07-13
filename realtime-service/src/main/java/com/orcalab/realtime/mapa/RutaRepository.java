package com.orcalab.realtime.mapa;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RutaRepository extends MongoRepository<Ruta, String> {
    List<Ruta> findBySalaId(Long salaId);
    void deleteBySalaId(Long salaId);
}