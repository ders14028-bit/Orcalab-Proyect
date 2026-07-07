package com.orcalab.room.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "salas")
public class Sala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private Long creadorId; // referencia al usuario de auth-service, NO es una FK real

    @Column(nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public Sala() {}

    public Sala(String nombre, String descripcion, Long creadorId) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.creadorId = creadorId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Long getCreadorId() { return creadorId; }
    public void setCreadorId(Long creadorId) { this.creadorId = creadorId; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}