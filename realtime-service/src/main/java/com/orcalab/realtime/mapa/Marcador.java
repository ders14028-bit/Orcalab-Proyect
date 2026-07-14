package com.orcalab.realtime.mapa;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "marcadores")
public class Marcador {

    @Id
    private String id;

    private Long salaId;
    private Long usuarioId; // quién lo creó o editó por última vez
    private Long creadorId; // quién lo creó originalmente; nunca cambia (a diferencia de usuarioId), usado para permisos de borrado
    private double latitud;
    private double longitud;
    private String tipo; // ej: "AVISTAMIENTO", "ZONA_INTERES"
    private String descripcion;
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaUltimaEdicion = LocalDateTime.now();

    public Marcador() {}

    public Marcador(Long salaId, Long usuarioId, double latitud, double longitud, String tipo, String descripcion) {
        this.salaId = salaId;
        this.usuarioId = usuarioId;
        this.creadorId = usuarioId;
        this.latitud = latitud;
        this.longitud = longitud;
        this.tipo = tipo;
        this.descripcion = descripcion;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getSalaId() { return salaId; }
    public void setSalaId(Long salaId) { this.salaId = salaId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Long getCreadorId() { return creadorId; }
    public void setCreadorId(Long creadorId) { this.creadorId = creadorId; }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaUltimaEdicion() { return fechaUltimaEdicion; }
    public void setFechaUltimaEdicion(LocalDateTime fechaUltimaEdicion) { this.fechaUltimaEdicion = fechaUltimaEdicion; }
}