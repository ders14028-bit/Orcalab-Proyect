package com.orcalab.realtime.repository;

import com.orcalab.realtime.model.Observation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ObservationRepository extends MongoRepository<Observation, String> {
}
