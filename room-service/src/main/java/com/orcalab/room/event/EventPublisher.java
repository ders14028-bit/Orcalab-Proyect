package com.orcalab.room.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.events.topic}")
    private String topic;

    public EventPublisher(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publicar(SalaEvento evento) {
        try {
            String json = objectMapper.writeValueAsString(evento);
            MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of("data", json))
                    .withStreamKey(topic);
            redisTemplate.opsForStream().add(record);
        } catch (Exception e) {
            // No queremos que un fallo al publicar el evento tumbe la operación principal
            // (ej. crear la sala debe funcionar aunque Redis esté caído momentáneamente)
            log.error("Error al publicar evento {} en Redis Streams", evento.getTipo(), e);
        }
    }
}