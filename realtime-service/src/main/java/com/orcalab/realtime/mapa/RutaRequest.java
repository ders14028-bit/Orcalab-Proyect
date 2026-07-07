package com.orcalab.realtime.mapa;

import java.util.List;

public class RutaRequest {

    private List<Ruta.Punto> puntos;
    private String descripcion;

    public List<Ruta.Punto> getPuntos() { return puntos; }
    public void setPuntos(List<Ruta.Punto> puntos) { this.puntos = puntos; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}