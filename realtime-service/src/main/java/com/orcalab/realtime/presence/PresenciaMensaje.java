package com.orcalab.realtime.presence;

import java.util.List;

public class PresenciaMensaje {

    // "ENTRADA"/"SALIDA": conexión o desconexión real de WebSocket.
    // "MIEMBRO_UNIDO"/"MIEMBRO_SALIO"/"ROL_CAMBIADO": cambios de membresía en room-service
    // (unirse, salir, ser expulsado, cambio de rol) reflejados vía Redis Streams — no implican
    // necesariamente una conexión/desconexión de socket en ese mismo instante.
    private String tipo;
    private Long usuarioId;
    private List<UsuarioPresente> presentes;

    public PresenciaMensaje() {}

    public static PresenciaMensaje entrada(Long usuarioId, List<UsuarioPresente> presentes) {
        return construir("ENTRADA", usuarioId, presentes);
    }

    public static PresenciaMensaje salida(Long usuarioId, List<UsuarioPresente> presentes) {
        return construir("SALIDA", usuarioId, presentes);
    }

    public static PresenciaMensaje miembroUnido(Long usuarioId, List<UsuarioPresente> presentes) {
        return construir("MIEMBRO_UNIDO", usuarioId, presentes);
    }

    public static PresenciaMensaje miembroSalio(Long usuarioId, List<UsuarioPresente> presentes) {
        return construir("MIEMBRO_SALIO", usuarioId, presentes);
    }

    public static PresenciaMensaje rolCambiado(Long usuarioId, List<UsuarioPresente> presentes) {
        return construir("ROL_CAMBIADO", usuarioId, presentes);
    }

    private static PresenciaMensaje construir(String tipo, Long usuarioId, List<UsuarioPresente> presentes) {
        PresenciaMensaje msg = new PresenciaMensaje();
        msg.tipo = tipo;
        msg.usuarioId = usuarioId;
        msg.presentes = presentes;
        return msg;
    }

    public String getTipo() { return tipo; }
    public Long getUsuarioId() { return usuarioId; }
    public List<UsuarioPresente> getPresentes() { return presentes; }

    public record UsuarioPresente(Long usuarioId, String rolEnSala) {}
}