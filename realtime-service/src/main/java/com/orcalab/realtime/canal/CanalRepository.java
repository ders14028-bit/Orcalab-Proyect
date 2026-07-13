package com.orcalab.realtime.canal;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CanalRepository extends MongoRepository<Canal, String> {
    List<Canal> findBySalaIdOrderByFechaCreacionAsc(Long salaId);
    boolean existsBySalaId(Long salaId);
    void deleteBySalaId(Long salaId);
}
