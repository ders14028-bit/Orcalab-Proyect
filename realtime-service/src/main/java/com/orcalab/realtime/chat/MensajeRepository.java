package com.orcalab.realtime.chat;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MensajeRepository extends MongoRepository<Mensaje, String> {

    List<Mensaje> findBySalaIdOrderByTimestampAsc(Long salaId);
    List<Mensaje> findByCanalIdOrderByTimestampAsc(String canalId);
    void deleteBySalaId(Long salaId);
    void deleteByCanalId(String canalId);
}