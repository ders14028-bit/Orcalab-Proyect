package com.orcalab.room.service;

import com.orcalab.room.config.AuthContext;
import com.orcalab.room.dto.CrearSalaRequest;
import com.orcalab.room.dto.MiembroResponse;
import com.orcalab.room.dto.SalaResponse;
import com.orcalab.room.event.EventPublisher;
import com.orcalab.room.event.SalaEvento;
import com.orcalab.room.model.RolSala;
import com.orcalab.room.model.Sala;
import com.orcalab.room.model.SalaMiembro;
import com.orcalab.room.repository.SalaMiembroRepository;
import com.orcalab.room.repository.SalaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {

    private final SalaRepository salaRepository;
    private final SalaMiembroRepository miembroRepository;
    private final AuthContext authContext;
    private final EventPublisher eventPublisher;

    public RoomService(SalaRepository salaRepository, SalaMiembroRepository miembroRepository,
                        AuthContext authContext, EventPublisher eventPublisher) {
        this.salaRepository = salaRepository;
        this.miembroRepository = miembroRepository;
        this.authContext = authContext;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SalaResponse crearSala(CrearSalaRequest request) {
        Long usuarioId = authContext.usuarioIdActual();

        Sala sala = new Sala(request.getNombre(), request.getDescripcion(), usuarioId);
        sala = salaRepository.save(sala);

        SalaMiembro miembro = new SalaMiembro(sala, usuarioId, RolSala.LIDER);
        miembroRepository.save(miembro);

        eventPublisher.publicar(SalaEvento.salaCreada(sala.getId(), sala.getNombre(), usuarioId));

        return new SalaResponse(sala, 1);
    }

    @Transactional(readOnly = true)
    public List<SalaResponse> listarMisSalas() {
        Long usuarioId = authContext.usuarioIdActual();

        return miembroRepository.findByUsuarioId(usuarioId).stream()
                .map(m -> {
                    Sala sala = m.getSala();
                    int total = miembroRepository.findBySalaId(sala.getId()).size();
                    return new SalaResponse(sala, total);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public SalaResponse obtenerSala(Long salaId) {
        Long usuarioId = authContext.usuarioIdActual();
        verificarEsMiembro(salaId, usuarioId);

        Sala sala = salaRepository.findById(salaId)
                .orElseThrow(() -> new IllegalArgumentException("Sala no encontrada"));

        int total = miembroRepository.findBySalaId(salaId).size();
        return new SalaResponse(sala, total);
    }

    @Transactional(readOnly = true)
    public List<MiembroResponse> listarMiembros(Long salaId) {
        Long usuarioId = authContext.usuarioIdActual();
        verificarEsMiembro(salaId, usuarioId);

        return miembroRepository.findBySalaId(salaId).stream()
                .map(MiembroResponse::new)
                .toList();
    }

    @Transactional
    public void unirseASala(Long salaId) {
        Long usuarioId = authContext.usuarioIdActual();

        if (miembroRepository.existsBySalaIdAndUsuarioId(salaId, usuarioId)) {
            throw new IllegalArgumentException("Ya eres miembro de esta sala");
        }

        Sala sala = salaRepository.findById(salaId)
                .orElseThrow(() -> new IllegalArgumentException("Sala no encontrada"));

        SalaMiembro miembro = new SalaMiembro(sala, usuarioId, RolSala.MIEMBRO);
        miembroRepository.save(miembro);

        eventPublisher.publicar(SalaEvento.usuarioSeUnio(salaId, usuarioId, RolSala.MIEMBRO.name()));
    }

    @Transactional
    public void salirDeSala(Long salaId) {
        Long usuarioIdActual = authContext.usuarioIdActual();
        salirOExpulsar(salaId, usuarioIdActual, usuarioIdActual);
    }

    @Transactional
    public void expulsarMiembro(Long salaId, Long usuarioIdAExpulsar) {
        Long usuarioIdActual = authContext.usuarioIdActual();

        if (usuarioIdActual.equals(usuarioIdAExpulsar)) {
            throw new IllegalArgumentException("Usa el endpoint de salir de sala para abandonar tú mismo la sala");
        }

        verificarEsLider(salaId, usuarioIdActual);
        salirOExpulsar(salaId, usuarioIdActual, usuarioIdAExpulsar);
    }

    private void salirOExpulsar(Long salaId, Long usuarioIdActual, Long usuarioIdObjetivo) {
        SalaMiembro miembro = miembroRepository.findBySalaIdAndUsuarioId(salaId, usuarioIdObjetivo)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no es miembro de esta sala"));

        boolean esUltimoLider = miembro.getRolEnSala() == RolSala.LIDER
                && miembroRepository.findBySalaId(salaId).stream()
                        .filter(m -> m.getRolEnSala() == RolSala.LIDER)
                        .count() == 1;

        if (esUltimoLider) {
            throw new IllegalArgumentException("No puedes salir/ser removido: eres el único líder de la sala. Promueve a otro miembro primero.");
        }

        miembroRepository.delete(miembro);

        eventPublisher.publicar(SalaEvento.miembroSalio(salaId, usuarioIdObjetivo));
    }

    @Transactional
    public void cambiarRol(Long salaId, Long usuarioIdObjetivo, RolSala nuevoRol) {
        Long usuarioIdActual = authContext.usuarioIdActual();
        verificarEsLider(salaId, usuarioIdActual);

        SalaMiembro miembro = miembroRepository.findBySalaIdAndUsuarioId(salaId, usuarioIdObjetivo)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no es miembro de esta sala"));

        miembro.setRolEnSala(nuevoRol);
        miembroRepository.save(miembro);

        eventPublisher.publicar(SalaEvento.rolCambiado(salaId, usuarioIdObjetivo, nuevoRol.name()));
    }

    @Transactional
    public void eliminarSala(Long salaId) {
        Long usuarioId = authContext.usuarioIdActual();
        verificarEsLider(salaId, usuarioId);

        Sala sala = salaRepository.findById(salaId)
                .orElseThrow(() -> new IllegalArgumentException("Sala no encontrada"));

        miembroRepository.deleteBySalaId(salaId);
        salaRepository.delete(sala);

        eventPublisher.publicar(SalaEvento.salaEliminada(salaId, usuarioId));
    }

    private void verificarEsMiembro(Long salaId, Long usuarioId) {
        if (!miembroRepository.existsBySalaIdAndUsuarioId(salaId, usuarioId)) {
            throw new AccessDeniedException("No eres miembro de esta sala");
        }
    }

    private void verificarEsLider(Long salaId, Long usuarioId) {
        if (!miembroRepository.existsBySalaIdAndUsuarioIdAndRolEnSala(salaId, usuarioId, RolSala.LIDER)) {
            throw new AccessDeniedException("Solo el líder de la sala puede realizar esta acción");
        }
    }
}