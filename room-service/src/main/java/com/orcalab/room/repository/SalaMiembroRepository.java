package com.orcalab.room.repository;

import com.orcalab.room.model.RolSala;
import com.orcalab.room.model.SalaMiembro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaMiembroRepository extends JpaRepository<SalaMiembro, Long> {

    Optional<SalaMiembro> findBySalaIdAndUsuarioId(Long salaId, Long usuarioId);

    List<SalaMiembro> findBySalaId(Long salaId);

    List<SalaMiembro> findByUsuarioId(Long usuarioId);

    boolean existsBySalaIdAndUsuarioId(Long salaId, Long usuarioId);

    boolean existsBySalaIdAndUsuarioIdAndRolEnSala(Long salaId, Long usuarioId, RolSala rolEnSala);

    void deleteBySalaId(Long salaId);
}