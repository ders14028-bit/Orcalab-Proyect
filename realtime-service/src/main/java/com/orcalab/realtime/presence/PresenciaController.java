package com.orcalab.realtime.presence;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Controller
public class PresenciaController {

    private final PresenciaService presenciaService;
    private final PresenciaBroadcastService presenciaBroadcastService;

    public PresenciaController(PresenciaService presenciaService, PresenciaBroadcastService presenciaBroadcastService) {
        this.presenciaService = presenciaService;
        this.presenciaBroadcastService = presenciaBroadcastService;
    }

    @MessageMapping("/sala/{salaId}/entrar")
    public void entrarASala(@DestinationVariable Long salaId, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        Long usuarioId = extraerUsuarioId(principal);
        String sessionId = headerAccessor.getSessionId();

        presenciaService.registrarEntrada(sessionId, salaId, usuarioId);

        // Guardamos salaId en los atributos de la sesión, para poder recuperarlo en la desconexión
        headerAccessor.getSessionAttributes().put("salaId", salaId);

        presenciaBroadcastService.difundirEntrada(salaId, usuarioId);
    }

    @EventListener
    public void manejarDesconexion(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        PresenciaService.SesionInfo info = presenciaService.registrarSalida(sessionId);

        if (info != null) {
            presenciaBroadcastService.difundirSalida(info.salaId(), info.usuarioId());
        }
    }

    private Long extraerUsuarioId(Principal principal) {
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken token) {
            return (Long) token.getPrincipal();
        }
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }
}