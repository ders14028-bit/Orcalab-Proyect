package com.orcalab.realtime.mapa;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MarcadorRepository extends MongoRepository<Marcador, String> {
    List<Marcador> findBySalaId(Long salaId);
    void deleteBySalaId(Long salaId);
}