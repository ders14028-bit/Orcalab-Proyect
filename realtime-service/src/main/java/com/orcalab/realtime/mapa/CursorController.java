package com.orcalab.realtime.mapa;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class CursorController {

    private final SimpMessagingTemplate messagingTemplate;

    public CursorController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Evento efímero: no se persiste en ninguna base de datos, solo se retransmite
    // en vivo a los demás usuarios conectados a la misma sala.
    @MessageMapping("/sala/{salaId}/cursor")
    public void moverCursor(@DestinationVariable Long salaId, @Payload CursorRequest request,
                             SimpMessageHeaderAccessor headerAccessor) {
        Long usuarioId = extraerUsuarioId(headerAccessor.getUser());

        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/cursores",
                Map.of("usuarioId", usuarioId, "x", request.getX(), "y", request.getY()));
    }

    private Long extraerUsuarioId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token) {
            return (Long) token.getPrincipal();
        }
        throw new IllegalStateException("No se pudo determinar el usuario autenticado");
    }

    public static class CursorRequest {
        private double x;
        private double y;

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
    }
}