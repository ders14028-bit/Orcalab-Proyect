package com.orcalab.realtime.mapa;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "rutas")
public class Ruta {

    @Id
    private String id;

    private Long salaId;
    private Long usuarioId;
    private List<Punto> puntos;
    private String descripcion;
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public Ruta() {}

    public Ruta(Long salaId, Long usuarioId, List<Punto> puntos, String descripcion) {
        this.salaId = salaId;
        this.usuarioId = usuarioId;
        this.puntos = puntos;
        this.descripcion = descripcion;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getSalaId() { return salaId; }
    public void setSalaId(Long salaId) { this.salaId = salaId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public List<Punto> getPuntos() { return puntos; }
    public void setPuntos(List<Punto> puntos) { this.puntos = puntos; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public record Punto(double latitud, double longitud) {}
}