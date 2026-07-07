package com.orcalab.realtime.alerta;

import com.orcalab.realtime.event.EventPublisher;
import com.orcalab.realtime.event.MapaEvento;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertaService {

    private final AlertaRepository alertaRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EventPublisher eventPublisher;

    public AlertaService(AlertaRepository alertaRepository, SimpMessagingTemplate messagingTemplate,
                          EventPublisher eventPublisher) {
        this.alertaRepository = alertaRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
    }

    public void generarAlertaPorMarcador(Long salaId, Long usuarioId, String marcadorId,
                                          double latitud, double longitud, String descripcion) {
        Alerta alerta = new Alerta(salaId, usuarioId, marcadorId, latitud, longitud, descripcion);
        alerta = alertaRepository.save(alerta);

        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/alertas", alerta);

        eventPublisher.publicar(MapaEvento.marcador("AlertaGenerada", salaId, usuarioId, alerta.getId()));
    }
}