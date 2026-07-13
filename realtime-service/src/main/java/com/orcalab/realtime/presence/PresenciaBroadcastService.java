package com.orcalab.realtime.presence;

import com.orcalab.realtime.state.SalaEstadoService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Construye y difunde el listado de presencia de una sala hacia /topic/sala/{salaId}/presencia.
 * Lo usan tanto las conexiones/desconexiones reales de WebSocket (PresenciaController) como los
 * cambios de membresía que llegan de forma asíncrona vía Redis Streams (RoomEventConsumer), para
 * que ambos caminos mantengan a los clientes conectados sincronizados sin necesidad de recargar.
 */
@Service
public class PresenciaBroadcastService {

    private final PresenciaService presenciaService;
    private final SalaEstadoService salaEstadoService;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenciaBroadcastService(PresenciaService presenciaService, SalaEstadoService salaEstadoService,
                                      SimpMessagingTemplate messagingTemplate) {
        this.presenciaService = presenciaService;
        this.salaEstadoService = salaEstadoService;
        this.messagingTemplate = messagingTemplate;
    }

    public List<PresenciaMensaje.UsuarioPresente> construirListaPresentes(Long salaId) {
        return presenciaService.obtenerPresentes(salaId).stream()
                .map(uid -> {
                    var rol = salaEstadoService.obtenerRol(salaId, uid);
                    return new PresenciaMensaje.UsuarioPresente(uid, rol != null ? rol.name() : "DESCONOCIDO");
                })
                .toList();
    }

    private void difundir(Long salaId, Long usuarioId,
                           BiFunction<Long, List<PresenciaMensaje.UsuarioPresente>, PresenciaMensaje> constructor) {
        var presentes = construirListaPresentes(salaId);
        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/presencia", constructor.apply(usuarioId, presentes));
    }

    public void difundirEntrada(Long salaId, Long usuarioId) {
        difundir(salaId, usuarioId, PresenciaMensaje::entrada);
    }

    public void difundirSalida(Long salaId, Long usuarioId) {
        difundir(salaId, usuarioId, PresenciaMensaje::salida);
    }

    public void difundirMiembroUnido(Long salaId, Long usuarioId) {
        difundir(salaId, usuarioId, PresenciaMensaje::miembroUnido);
    }

    /** Además de avisar a los demás, se asegura de que el expulsado ya no figure como conectado. */
    public void difundirMiembroSalio(Long salaId, Long usuarioId) {
        presenciaService.forzarSalida(salaId, usuarioId);
        difundir(salaId, usuarioId, PresenciaMensaje::miembroSalio);
    }

    public void difundirRolCambiado(Long salaId, Long usuarioId) {
        difundir(salaId, usuarioId, PresenciaMensaje::rolCambiado);
    }
}
