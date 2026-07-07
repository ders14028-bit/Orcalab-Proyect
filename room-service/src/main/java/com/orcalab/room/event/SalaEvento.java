package com.orcalab.room.event;

import java.time.LocalDateTime;

public class SalaEvento {

    private String tipo;
    private Long salaId;
    private String nombreSala; // solo se usa en SalaCreada, null en los demás
    private Long usuarioId;
    private String rolEnSala; // solo se usa en eventos que involucran rol
    private LocalDateTime timestamp = LocalDateTime.now();

    public SalaEvento() {}

    public static SalaEvento salaCreada(Long salaId, String nombreSala, Long usuarioId) {
        SalaEvento evento = new SalaEvento();
        evento.tipo = "SalaCreada";
        evento.salaId = salaId;
        evento.nombreSala = nombreSala;
        evento.usuarioId = usuarioId;
        return evento;
    }

    public static SalaEvento usuarioSeUnio(Long salaId, Long usuarioId, String rolEnSala) {
        SalaEvento evento = new SalaEvento();
        evento.tipo = "UsuarioSeUnioASala";
        evento.salaId = salaId;
        evento.usuarioId = usuarioId;
        evento.rolEnSala = rolEnSala;
        return evento;
    }

    public static SalaEvento miembroSalio(Long salaId, Long usuarioId) {
        SalaEvento evento = new SalaEvento();
        evento.tipo = "MiembroSalioDeSala";
        evento.salaId = salaId;
        evento.usuarioId = usuarioId;
        return evento;
    }

    public static SalaEvento rolCambiado(Long salaId, Long usuarioId, String nuevoRol) {
        SalaEvento evento = new SalaEvento();
        evento.tipo = "RolDeMiembroCambiado";
        evento.salaId = salaId;
        evento.usuarioId = usuarioId;
        evento.rolEnSala = nuevoRol;
        return evento;
    }

    // Getters (necesarios para que Jackson pueda serializar a JSON)
    public String getTipo() { return tipo; }
    public Long getSalaId() { return salaId; }
    public String getNombreSala() { return nombreSala; }
    public Long getUsuarioId() { return usuarioId; }
    public String getRolEnSala() { return rolEnSala; }
    public LocalDateTime getTimestamp() { return timestamp; }
}