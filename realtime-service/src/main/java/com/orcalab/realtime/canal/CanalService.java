package com.orcalab.realtime.canal;

import com.orcalab.realtime.chat.MensajeRepository;
import com.orcalab.realtime.config.AuthContext;
import com.orcalab.realtime.room.RoomServiceClient;
import com.orcalab.realtime.voz.VozService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CanalService {

    public static final String NOMBRE_CANAL_POR_DEFECTO = "general";

    private final CanalRepository canalRepository;
    private final MensajeRepository mensajeRepository;
    private final RoomServiceClient roomServiceClient;
    private final AuthContext authContext;
    private final SimpMessagingTemplate messagingTemplate;
    private final VozService vozService;

    public CanalService(CanalRepository canalRepository, MensajeRepository mensajeRepository,
                         RoomServiceClient roomServiceClient, AuthContext authContext,
                         SimpMessagingTemplate messagingTemplate, VozService vozService) {
        this.canalRepository = canalRepository;
        this.mensajeRepository = mensajeRepository;
        this.roomServiceClient = roomServiceClient;
        this.authContext = authContext;
        this.messagingTemplate = messagingTemplate;
        this.vozService = vozService;
    }

    public List<Canal> listar(Long salaId) {
        return canalRepository.findBySalaIdOrderByFechaCreacionAsc(salaId);
    }

    public Canal crear(Long salaId, CanalRequest request) {
        Long usuarioId = authContext.usuarioIdActual();

        if (!roomServiceClient.esLider(salaId, usuarioId, authContext.tokenActual())) {
            throw new AccessDeniedException("Solo el líder de la sala puede crear canales");
        }

        Canal canal = new Canal(salaId, request.getNombre().trim(), request.getTipo(), usuarioId);
        canal = canalRepository.save(canal);

        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/canales", canal);

        return canal;
    }

    public void eliminar(Long salaId, String canalId) {
        Long usuarioId = authContext.usuarioIdActual();

        if (!roomServiceClient.esLider(salaId, usuarioId, authContext.tokenActual())) {
            throw new AccessDeniedException("Solo el líder de la sala puede eliminar canales");
        }

        Canal canal = canalRepository.findById(canalId)
                .filter(c -> c.getSalaId().equals(salaId))
                .orElseThrow(() -> new IllegalArgumentException("Canal no encontrado"));

        // Nunca se permite dejar la sala sin ningún canal (sin importar el nombre o tipo del
        // último que quede) para que el chat nunca quede completamente huérfano.
        if (canalRepository.findBySalaIdOrderByFechaCreacionAsc(salaId).size() <= 1) {
            throw new IllegalArgumentException("No puedes eliminar el último canal de la sala");
        }

        canalRepository.delete(canal);
        mensajeRepository.deleteByCanalId(canalId);
        if (canal.getTipo() == TipoCanal.VOZ) {
            // La desconexión real del cliente que está en la llamada la hace el front al recibir
            // el broadcast de abajo (ver quitarCanalDelEstado en RoomSocketContext); esto solo
            // limpia el estado de presencia en memoria para no dejar entradas huérfanas.
            vozService.limpiarCanal(salaId, canalId);
        }

        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/canales/eliminado", new CanalEliminadoMensaje(canalId));
    }

    /** Se invoca cuando se crea una sala, para que el chat tenga un canal de texto disponible desde el inicio. */
    public void crearCanalPorDefectoSiNoExiste(Long salaId, Long creadorId) {
        if (canalRepository.existsBySalaId(salaId)) {
            return;
        }
        Canal canal = new Canal(salaId, NOMBRE_CANAL_POR_DEFECTO, TipoCanal.TEXTO, creadorId);
        canal = canalRepository.save(canal);

        // Difundir igual que los canales creados por REST: el creador de la sala ya está
        // dentro (fue redirigido de inmediato) y su fetch inicial de canales casi siempre
        // llega antes de que este consumidor procese SalaCreada — sin el broadcast, el
        // líder no ve el canal "general" hasta recargar la página.
        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/canales", canal);
    }
}
