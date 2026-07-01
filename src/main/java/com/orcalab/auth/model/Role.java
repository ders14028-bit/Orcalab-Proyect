package com.orcalab.auth.model;

/**
 * HU-03: Roles del sistema.
 * PUBLICO ve solo ubicacion aproximada de especies protegidas.
 * INVESTIGADOR, ORGANIZACION y ADMIN ven ubicacion exacta (KPI-04).
 */
public enum Role {
    PUBLICO,
    INVESTIGADOR,
    ORGANIZACION,
    ADMIN
}
