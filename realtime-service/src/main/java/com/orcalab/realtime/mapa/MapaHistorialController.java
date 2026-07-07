package com.orcalab.realtime.mapa;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MapaHistorialController {

    private final MarcadorRepository marcadorRepository;
    private final RutaRepository rutaRepository;

    public MapaHistorialController(MarcadorRepository marcadorRepository, RutaRepository rutaRepository) {
        this.marcadorRepository = marcadorRepository;
        this.rutaRepository = rutaRepository;
    }

    @GetMapping("/api/salas/{salaId}/marcadores")
    public List<Marcador> marcadores(@PathVariable Long salaId) {
        return marcadorRepository.findBySalaId(salaId);
    }

    @GetMapping("/api/salas/{salaId}/rutas")
    public List<Ruta> rutas(@PathVariable Long salaId) {
        return rutaRepository.findBySalaId(salaId);
    }
}