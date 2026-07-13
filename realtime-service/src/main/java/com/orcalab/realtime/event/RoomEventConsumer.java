package com.orcalab.realtime.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orcalab.realtime.canal.CanalService;
import com.orcalab.realtime.model.RolSala;
import com.orcalab.realtime.presence.PresenciaBroadcastService;
import com.orcalab.realtime.sala.SalaLimpiezaService;
import com.orcalab.realtime.state.SalaEstadoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RoomEventConsumer.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SalaEstadoService salaEstadoService;
    private final CanalService canalService;
    private final SalaLimpiezaService salaLimpiezaService;
    private final PresenciaBroadcastService presenciaBroadcastService;

    @Value("${app.events.topic}")
    private String topic;

    private String ultimoIdLeido = "0";

    public RoomEventConsumer(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper,
                              SalaEstadoService salaEstadoService, CanalService canalService,
                              SalaLimpiezaService salaLimpiezaService, PresenciaBroadcastService presenciaBroadcastService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.salaEstadoService = salaEstadoService;
        this.canalService = canalService;
        this.salaLimpiezaService = salaLimpiezaService;
        this.presenciaBroadcastService = presenciaBroadcastService;
    }

    @Scheduled(fixedDelay = 1000)
    public void consumirEventos() {
        try {
            List<MapRecord<String, Object, Object>> registros = redisTemplate.opsForStream()
                    .read(StreamOffset.create(topic, ReadOffset.from(ultimoIdLeido)));

            if (registros == null || registros.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> registro : registros) {
                procesarRegistro(registro);
                ultimoIdLeido = registro.getId().getValue();
            }
        } catch (Exception e) {
            log.error("Error al consumir eventos de {}", topic, e);
        }
    }

    private void procesarRegistro(MapRecord<String, Object, Object> registro) {
        try {
            String json = (String) registro.getValue().get("data");
            SalaEventoRecibido evento = objectMapper.readValue(json, SalaEventoRecibido.class);
            aplicarEvento(evento);
        } catch (Exception e) {
            log.error("Error al procesar registro {}", registro.getId(), e);
        }
    }

    private void aplicarEvento(SalaEventoRecibido evento) {
        switch (evento.getTipo()) {
            case "SalaCreada" -> {
                salaEstadoService.registrarSalaCreada(evento.getSalaId(), evento.getUsuarioId());
                canalService.crearCanalPorDefectoSiNoExiste(evento.getSalaId(), evento.getUsuarioId());
            }
            case "UsuarioSeUnioASala" -> {
                salaEstadoService.registrarUsuarioUnido(
                        evento.getSalaId(), evento.getUsuarioId(), RolSala.valueOf(evento.getRolEnSala()));
                presenciaBroadcastService.difundirMiembroUnido(evento.getSalaId(), evento.getUsuarioId());
            }
            case "MiembroSalioDeSala" -> {
                salaEstadoService.registrarMiembroSalio(evento.getSalaId(), evento.getUsuarioId());
                presenciaBroadcastService.difundirMiembroSalio(evento.getSalaId(), evento.getUsuarioId());
            }
            case "RolDeMiembroCambiado" -> {
                salaEstadoService.registrarCambioRol(
                        evento.getSalaId(), evento.getUsuarioId(), RolSala.valueOf(evento.getRolEnSala()));
                presenciaBroadcastService.difundirRolCambiado(evento.getSalaId(), evento.getUsuarioId());
            }
            case "SalaEliminada" -> salaLimpiezaService.limpiarDatosDeSala(evento.getSalaId());
            default -> log.warn("Tipo de evento desconocido: {}", evento.getTipo());
        }
        log.info("Evento aplicado: {} para sala {}", evento.getTipo(), evento.getSalaId());
    }
}