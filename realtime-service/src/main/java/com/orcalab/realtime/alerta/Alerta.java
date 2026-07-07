package com.orcalab.realtime.alerta;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "alertas")
public class Alerta {

    @Id
    private String id;

    private Long salaId;
    private Long usuarioId; // quién generó la alerta
    private String marcadorId; // referencia al marcador que la originó
    private double latitud;
    private double longitud;
    private String descripcion;
    private LocalDateTime timestamp = LocalDateTime.now();

    public Alerta() {}

    public Alerta(Long salaId, Long usuarioId, String marcadorId, double latitud, double longitud, String descripcion) {
        this.salaId = salaId;
        this.usuarioId = usuarioId;
        this.marcadorId = marcadorId;
        this.latitud = latitud;
        this.longitud = longitud;
        this.descripcion = descripcion;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getSalaId() { return salaId; }
    public void setSalaId(Long salaId) { this.salaId = salaId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getMarcadorId() { return marcadorId; }
    public void setMarcadorId(String marcadorId) { this.marcadorId = marcadorId; }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}