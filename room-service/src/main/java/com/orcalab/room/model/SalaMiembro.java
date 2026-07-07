package com.orcalab.room.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sala_miembros", uniqueConstraints = @UniqueConstraint(columnNames = {"sala_id", "usuario_id"}))
public class SalaMiembro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sala_id", nullable = false)
    private Sala sala;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolSala rolEnSala;

    @Column(nullable = false)
    private LocalDateTime fechaUnion = LocalDateTime.now();

    public SalaMiembro() {}

    public SalaMiembro(Sala sala, Long usuarioId, RolSala rolEnSala) {
        this.sala = sala;
        this.usuarioId = usuarioId;
        this.rolEnSala = rolEnSala;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Sala getSala() { return sala; }
    public void setSala(Sala sala) { this.sala = sala; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public RolSala getRolEnSala() { return rolEnSala; }
    public void setRolEnSala(RolSala rolEnSala) { this.rolEnSala = rolEnSala; }

    public LocalDateTime getFechaUnion() { return fechaUnion; }
    public void setFechaUnion(LocalDateTime fechaUnion) { this.fechaUnion = fechaUnion; }
}