package com.orcalab.room.dto;

import com.orcalab.room.model.RolSala;
import jakarta.validation.constraints.NotNull;

public class CambiarRolRequest {

    @NotNull
    private RolSala nuevoRol;

    public RolSala getNuevoRol() { return nuevoRol; }
    public void setNuevoRol(RolSala nuevoRol) { this.nuevoRol = nuevoRol; }
}