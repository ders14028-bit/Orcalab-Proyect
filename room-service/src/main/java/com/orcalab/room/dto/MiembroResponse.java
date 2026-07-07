package com.orcalab.room.dto;

import com.orcalab.room.model.RolSala;
import com.orcalab.room.model.SalaMiembro;
import java.time.LocalDateTime;

public class MiembroResponse {

    private Long usuarioId;
    private RolSala rolEnSala;
    private LocalDateTime fechaUnion;

    public MiembroResponse(SalaMiembro miembro) {
        this.usuarioId = miembro.getUsuarioId();
        this.rolEnSala = miembro.getRolEnSala();
        this.fechaUnion = miembro.getFechaUnion();
    }

    public Long getUsuarioId() { return usuarioId; }
    public RolSala getRolEnSala() { return rolEnSala; }
    public LocalDateTime getFechaUnion() { return fechaUnion; }
}