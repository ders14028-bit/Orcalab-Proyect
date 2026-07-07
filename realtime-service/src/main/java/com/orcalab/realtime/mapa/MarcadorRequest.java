package com.orcalab.realtime.mapa;

public class MarcadorRequest {

    private String id; // null al crear, presente al editar
    private double latitud;
    private double longitud;
    private String tipo;
    private String descripcion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}