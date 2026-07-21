package com.orcalab.reporting.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Consulta realtime-service (fuente de verdad de los marcadores) para el reporte CSV -
 * reporting-service no guarda los marcadores en sí, solo eventos derivados de ellos vía Redis
 * Streams, que no alcanzan a capturar el estado actual completo (descripción tras ediciones, etc.).
 */
@Component
public class RealtimeServiceClient {

    private final RestClient restClient;

    public RealtimeServiceClient(RestClient.Builder builder,
                                  @Value("${app.services.realtime-service-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * GET /api/salas/{salaId}/marcadores ya verifica membresía internamente contra el token
     * reenviado; como quien llama aquí ya fue confirmado como líder (por ende, miembro), esa
     * verificación siempre pasa - no se duplica lógica de permisos, solo se reusa la fuente real.
     */
    public List<MarcadorDto> obtenerMarcadores(Long salaId, String token) {
        try {
            MarcadorDto[] marcadores = restClient.get()
                    .uri("/api/salas/{salaId}/marcadores", salaId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(MarcadorDto[].class);

            return marcadores != null ? Arrays.asList(marcadores) : List.of();
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudieron obtener los marcadores desde realtime-service", e);
        }
    }

    public record MarcadorDto(
            String id,
            Long salaId,
            Long usuarioId,
            Long creadorId,
            double latitud,
            double longitud,
            String tipo,
            String descripcion,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaUltimaEdicion) {
    }
}
