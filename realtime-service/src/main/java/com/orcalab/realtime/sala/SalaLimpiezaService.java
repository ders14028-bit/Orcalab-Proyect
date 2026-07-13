package com.orcalab.realtime.sala;

import com.orcalab.realtime.alerta.AlertaRepository;
import com.orcalab.realtime.canal.CanalRepository;
import com.orcalab.realtime.chat.MensajeRepository;
import com.orcalab.realtime.mapa.MarcadorRepository;
import com.orcalab.realtime.mapa.RutaRepository;
import com.orcalab.realtime.state.SalaEstadoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reacciona al evento "SalaEliminada" de room-service: los datos operativos de esa sala
 * (canales, mensajes, marcadores, rutas, alertas) ya no tienen sentido sin la sala, así que
 * se eliminan de realtime-service. reporting-service, en cambio, conserva su histórico como
 * registro de auditoría aunque la sala ya no exista (decisión de producto, no de este servicio).
 */
@Service
public class SalaLimpiezaService {

    private static final Logger log = LoggerFactory.getLogger(SalaLimpiezaService.class);

    private final CanalRepository canalRepository;
    private final MensajeRepository mensajeRepository;
    private final MarcadorRepository marcadorRepository;
    private final RutaRepository rutaRepository;
    private final AlertaRepository alertaRepository;
    private final SalaEstadoService salaEstadoService;

    public SalaLimpiezaService(CanalRepository canalRepository, MensajeRepository mensajeRepository,
                                MarcadorRepository marcadorRepository, RutaRepository rutaRepository,
                                AlertaRepository alertaRepository, SalaEstadoService salaEstadoService) {
        this.canalRepository = canalRepository;
        this.mensajeRepository = mensajeRepository;
        this.marcadorRepository = marcadorRepository;
        this.rutaRepository = rutaRepository;
        this.alertaRepository = alertaRepository;
        this.salaEstadoService = salaEstadoService;
    }

    public void limpiarDatosDeSala(Long salaId) {
        canalRepository.deleteBySalaId(salaId);
        mensajeRepository.deleteBySalaId(salaId);
        marcadorRepository.deleteBySalaId(salaId);
        rutaRepository.deleteBySalaId(salaId);
        alertaRepository.deleteBySalaId(salaId);
        salaEstadoService.eliminarSala(salaId);

        log.info("Datos operativos de la sala {} eliminados tras SalaEliminada", salaId);
    }
}
