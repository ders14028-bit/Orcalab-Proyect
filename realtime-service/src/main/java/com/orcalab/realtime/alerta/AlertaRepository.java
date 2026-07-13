package com.orcalab.realtime.alerta;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlertaRepository extends MongoRepository<Alerta, String> {
    List<Alerta> findBySalaIdOrderByTimestampDesc(Long salaId);
    void deleteBySalaId(Long salaId);
}