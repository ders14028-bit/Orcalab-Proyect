package com.orcalab.realtime.state;

import com.orcalab.realtime.model.RolSala;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SalaEstadoService {

    // salaId -> (usuarioId -> rolEnSala)
    private final Map<Long, Map<Long, RolSala>> miembrosPorSala = new ConcurrentHashMap<>();

    public void registrarSalaCreada(Long salaId, Long usuarioId) {
        Map<Long, RolSala> miembros = miembrosPorSala.computeIfAbsent(salaId, k -> new ConcurrentHashMap<>());
        miembros.put(usuarioId, RolSala.LIDER);
    }

    public void registrarUsuarioUnido(Long salaId, Long usuarioId, RolSala rol) {
        Map<Long, RolSala> miembros = miembrosPorSala.computeIfAbsent(salaId, k -> new ConcurrentHashMap<>());
        miembros.put(usuarioId, rol);
    }

    public void registrarMiembroSalio(Long salaId, Long usuarioId) {
        Map<Long, RolSala> miembros = miembrosPorSala.get(salaId);
        if (miembros != null) {
            miembros.remove(usuarioId);
        }
    }

    public void registrarCambioRol(Long salaId, Long usuarioId, RolSala nuevoRol) {
        Map<Long, RolSala> miembros = miembrosPorSala.computeIfAbsent(salaId, k -> new ConcurrentHashMap<>());
        miembros.put(usuarioId, nuevoRol);
    }

    public RolSala obtenerRol(Long salaId, Long usuarioId) {
        Map<Long, RolSala> miembros = miembrosPorSala.get(salaId);
        return miembros != null ? miembros.get(usuarioId) : null;
    }

    public Set<Long> obtenerMiembros(Long salaId) {
        Map<Long, RolSala> miembros = miembrosPorSala.get(salaId);
        return miembros != null ? miembros.keySet() : Set.of();
    }

    public boolean esLider(Long salaId, Long usuarioId) {
        return obtenerRol(salaId, usuarioId) == RolSala.LIDER;
    }

    public void eliminarSala(Long salaId) {
        miembrosPorSala.remove(salaId);
    }
}