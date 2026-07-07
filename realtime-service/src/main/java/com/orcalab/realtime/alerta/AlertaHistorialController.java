package com.orcalab.realtime.alerta;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AlertaHistorialController {

    private final AlertaRepository alertaRepository;

    public AlertaHistorialController(AlertaRepository alertaRepository) {
        this.alertaRepository = alertaRepository;
    }

    @GetMapping("/api/salas/{salaId}/alertas")
    public List<Alerta> historial(@PathVariable Long salaId) {
        return alertaRepository.findBySalaIdOrderByTimestampDesc(salaId);
    }
}