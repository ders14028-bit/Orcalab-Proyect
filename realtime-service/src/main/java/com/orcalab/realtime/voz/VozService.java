package com.orcalab.realtime.voz;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VozService {

    // (salaId, canalId) -> usuarioId -> muteado
    private final Map<CanalVozKey, Map<Long, Boolean>> participantesPorCanal = new ConcurrentHashMap<>();

    // sessionId (de la conexión WebSocket) -> última llamada de voz activa para esa sesión,
    // para poder limpiar en desconexión o al cambiar de canal de voz sin pasar por "salir".
    private final Map<String, SesionVozInfo> sesiones = new ConcurrentHashMap<>();

    public void entrar(String sessionId, Long salaId, String canalId, Long usuarioId) {
        SesionVozInfo anterior = sesiones.get(sessionId);
        if (anterior != null && !anterior.mismoCanal(salaId, canalId)) {
            quitarDeCanal(anterior);
        }

        participantesPorCanal
                .computeIfAbsent(new CanalVozKey(salaId, canalId), k -> new ConcurrentHashMap<>())
                .put(usuarioId, Boolean.FALSE);
        sesiones.put(sessionId, new SesionVozInfo(salaId, canalId, usuarioId));
    }

    public SesionVozInfo salir(String sessionId) {
        SesionVozInfo info = sesiones.remove(sessionId);
        if (info != null) {
            quitarDeCanal(info);
        }
        return info;
    }

    public void silenciar(Long salaId, String canalId, Long usuarioId, boolean muteado) {
        Map<Long, Boolean> participantes = participantesPorCanal.get(new CanalVozKey(salaId, canalId));
        if (participantes != null) {
            participantes.computeIfPresent(usuarioId, (id, actual) -> muteado);
        }
    }

    public Map<Long, Boolean> obtenerParticipantes(Long salaId, String canalId) {
        return participantesPorCanal.getOrDefault(new CanalVozKey(salaId, canalId), Map.of());
    }

    /** Se invoca al eliminar un canal de voz, para no dejar entradas huérfanas en el mapa. */
    public void limpiarCanal(Long salaId, String canalId) {
        participantesPorCanal.remove(new CanalVozKey(salaId, canalId));
    }

    private void quitarDeCanal(SesionVozInfo info) {
        Map<Long, Boolean> participantes = participantesPorCanal.get(new CanalVozKey(info.salaId(), info.canalId()));
        if (participantes != null) {
            participantes.remove(info.usuarioId());
        }
    }

    public record SesionVozInfo(Long salaId, String canalId, Long usuarioId) {
        boolean mismoCanal(Long salaId, String canalId) {
            return this.salaId.equals(salaId) && this.canalId.equals(canalId);
        }
    }

    private record CanalVozKey(Long salaId, String canalId) {}
}
