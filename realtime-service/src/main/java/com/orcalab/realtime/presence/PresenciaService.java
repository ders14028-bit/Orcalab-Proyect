package com.orcalab.realtime.presence;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenciaService {

    // salaId -> conjunto de usuarioIds presentes
    private final Map<Long, Set<Long>> presentesPorSala = new ConcurrentHashMap<>();

    // sessionId (de la conexión WebSocket) -> (salaId, usuarioId), para saber qué limpiar al desconectar
    private final Map<String, SesionInfo> sesiones = new ConcurrentHashMap<>();

    public void registrarEntrada(String sessionId, Long salaId, Long usuarioId) {
        presentesPorSala.computeIfAbsent(salaId, k -> ConcurrentHashMap.newKeySet()).add(usuarioId);
        sesiones.put(sessionId, new SesionInfo(salaId, usuarioId));
    }

    public SesionInfo registrarSalida(String sessionId) {
        SesionInfo info = sesiones.remove(sessionId);
        if (info != null) {
            Set<Long> presentes = presentesPorSala.get(info.salaId());
            if (presentes != null) {
                presentes.remove(info.usuarioId());
            }
        }
        return info;
    }

    public Set<Long> obtenerPresentes(Long salaId) {
        return presentesPorSala.getOrDefault(salaId, Set.of());
    }

    /**
     * Quita a un usuario de la lista de presentes sin pasar por una desconexión real de
     * WebSocket — se usa cuando lo expulsan de la sala mientras su conexión sigue abierta,
     * para que los demás dejen de verlo como conectado de inmediato. Si más tarde su socket
     * se desconecta de verdad, registrarSalida() simplemente no encontrará nada que quitar.
     */
    public void forzarSalida(Long salaId, Long usuarioId) {
        Set<Long> presentes = presentesPorSala.get(salaId);
        if (presentes != null) {
            presentes.remove(usuarioId);
        }
    }

    public record SesionInfo(Long salaId, Long usuarioId) {}
}