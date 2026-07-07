package com.orcalab.realtime.mapa;

import com.orcalab.realtime.alerta.AlertaService;
import com.orcalab.realtime.event.EventPublisher;
import com.orcalab.realtime.event.MapaEvento;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
public class MapaController {

    private final MarcadorRepository marcadorRepository;
    private final RutaRepository rutaRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EventPublisher eventPublisher;
    private final AlertaService alertaService;

    public MapaController(MarcadorRepository marcadorRepository, RutaRepository rutaRepository,
                           SimpMessagingTemplate messagingTemplate, EventPublisher eventPublisher,
                           AlertaService alertaService) {
        this.marcadorRepository = marcadorRepository;
        this.rutaRepository = rutaRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
        this.alertaService = alertaService;
    }

    @MessageMapping("/sala/{salaId}/marcador")
    public void agregarOEditarMarcador(@DestinationVariable Long salaId, @Payload MarcadorRequest request,
                                        SimpMessageHeaderAccessor headerAccessor) {
        Long usuarioId = extraerUsuarioId(headerAccessor.getUser());
        boolean esEdicion = request.getId() != null && !request.getId().isBlank();

        Marcador marcador;
        if (esEdicion) {
            marcador = marcadorRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Marcador no encontrado"));
            marcador.setLatitud(request.getLatitud());
            marcador.setLongitud(request.getLongitud());
            marcador.setTipo(request.getTipo());
            marcador.setDescripcion(request.getDescripcion());
            marcador.setUsuarioId(usuarioId);
            marcador.setFechaUltimaEdicion(LocalDateTime.now());
        } else {
            marcador = new Marcador(salaId, usuarioId, request.getLatitud(), request.getLongitud(),
                    request.getTipo(), request.getDescripcion());
        }

        marcador = marcadorRepository.save(marcador);

        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/marcadores", marcador);

        String tipoEvento = esEdicion ? "MarcadorEditado" : "MarcadorAgregado";
        eventPublisher.publicar(MapaEvento.marcador(tipoEvento, salaId, usuarioId, marcador.getId()));

        if ("CRITICO".equalsIgnoreCase(marcador.getTipo()) && !esEdicion) {
            alertaService.generarAlertaPorMarcador(salaId, usuarioId, marcador.getId(),
                    marcador.getLatitud(), marcador.getLongitud(), marcador.getDescripcion());
        }
    }

    @MessageMapping("/sala/{salaId}/ruta")
    public void trazarRuta(@DestinationVariable Long salaId, @Payload RutaRequest request,
                            SimpMessageHeaderAccessor headerAccessor) {
        Long usuarioId = extraerUsuarioId(headerAccessor.getUser());

        Ruta ruta = new Ruta(salaId, usuarioId, request.getPuntos(), request.getDescripcion());
        ruta = rutaRepository.save(ruta);

        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/rutas", ruta);

        eventPublisher.publicar(MapaEvento.ruta(salaId, usuarioId, ruta.getId()));
    }

    private Long extraerUsuarioId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token) {
            return (Long) token.getPrincipal();
        }
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }
}