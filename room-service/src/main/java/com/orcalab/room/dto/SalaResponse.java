package com.orcalab.room.dto;

import com.orcalab.room.model.Sala;
import java.time.LocalDateTime;

public class SalaResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private Long creadorId;
    private LocalDateTime fechaCreacion;
    private int totalMiembros;

    public SalaResponse(Sala sala, int totalMiembros) {
        this.id = sala.getId();
        this.nombre = sala.getNombre();
        this.descripcion = sala.getDescripcion();
        this.creadorId = sala.getCreadorId();
        this.fechaCreacion = sala.getFechaCreacion();
        this.totalMiembros = totalMiembros;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public Long getCreadorId() { return creadorId; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public int getTotalMiembros() { return totalMiembros; }

}
