package com.orcalab.realtime.mapa;

import com.orcalab.realtime.config.AuthContext;
import com.orcalab.realtime.room.RoomServiceClient;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
public class MapaHistorialController {

    private final MarcadorRepository marcadorRepository;
    private final RutaRepository rutaRepository;
    private final RoomServiceClient roomServiceClient;
    private final AuthContext authContext;
    private final SimpMessagingTemplate messagingTemplate;

    public MapaHistorialController(MarcadorRepository marcadorRepository, RutaRepository rutaRepository,
                                    RoomServiceClient roomServiceClient, AuthContext authContext,
                                    SimpMessagingTemplate messagingTemplate) {
        this.marcadorRepository = marcadorRepository;
        this.rutaRepository = rutaRepository;
        this.roomServiceClient = roomServiceClient;
        this.authContext = authContext;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/api/salas/{salaId}/marcadores")
    public List<Marcador> marcadores(@PathVariable Long salaId) {
        return marcadorRepository.findBySalaId(salaId);
    }

    @GetMapping("/api/salas/{salaId}/rutas")
    public List<Ruta> rutas(@PathVariable Long salaId) {
        return rutaRepository.findBySalaId(salaId);
    }

    // REST (no WebSocket, a diferencia de crear/editar en MapaController): la verificación de
    // "es líder" necesita el JWT crudo de la petición (AuthContext.tokenActual()), que solo está
    // disponible en el hilo de una petición HTTP real — los handlers @MessageMapping de STOMP no
    // tienen ese contexto porque JwtHandshakeInterceptor solo guarda el usuarioId ya parseado en
    // el CONNECT, no el token crudo. Igual que CanalController/CanalService.eliminar.
    @DeleteMapping("/api/salas/{salaId}/marcadores/{marcadorId}")
    public ResponseEntity<Void> eliminarMarcador(@PathVariable Long salaId, @PathVariable String marcadorId) {
        Long usuarioId = authContext.usuarioIdActual();

        Marcador marcador = marcadorRepository.findById(marcadorId)
                .filter(m -> m.getSalaId().equals(salaId))
                .orElseThrow(() -> new IllegalArgumentException("Marcador no encontrado"));

        boolean esCreador = Objects.equals(marcador.getCreadorId(), usuarioId);
        boolean esLider = roomServiceClient.esLider(salaId, usuarioId, authContext.tokenActual());
        if (!esCreador && !esLider) {
            throw new AccessDeniedException("Solo quien creó el marcador o el líder de la sala pueden eliminarlo");
        }

        marcadorRepository.delete(marcador);

        // La alerta que este marcador haya generado (si era CRITICO) se conserva como registro
        // histórico — ya cumplió su propósito de notificar en su momento.
        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/marcadores/eliminado",
                new MarcadorEliminadoMensaje(marcadorId));

        return ResponseEntity.noContent().build();
    }
}