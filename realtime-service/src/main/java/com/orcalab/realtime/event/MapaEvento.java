package com.orcalab.realtime.event;

import java.time.LocalDateTime;

public class MapaEvento {

    private String tipo;
    private Long salaId;
    private Long usuarioId;
    private String elementoId; // id del marcador o ruta
    private LocalDateTime timestamp = LocalDateTime.now();

    public MapaEvento() {}

    public static MapaEvento marcador(String tipo, Long salaId, Long usuarioId, String marcadorId) {
        MapaEvento evento = new MapaEvento();
        evento.tipo = tipo;
        evento.salaId = salaId;
        evento.usuarioId = usuarioId;
        evento.elementoId = marcadorId;
        return evento;
    }

    public static MapaEvento ruta(Long salaId, Long usuarioId, String rutaId) {
        MapaEvento evento = new MapaEvento();
        evento.tipo = "RutaTrazada";
        evento.salaId = salaId;
        evento.usuarioId = usuarioId;
        evento.elementoId = rutaId;
        return evento;
    }

    public String getTipo() { return tipo; }
    public Long getSalaId() { return salaId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getElementoId() { return elementoId; }
    public LocalDateTime getTimestamp() { return timestamp; }
}