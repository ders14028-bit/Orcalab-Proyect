package com.orcalab.reporting.room;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

/**
 * Igual que su equivalente en realtime-service: consulta room-service (fuente de verdad) en vez
 * de confiar en las colecciones locales de reporting-service, que solo se alimentan de forma
 * asíncrona vía eventos y no guardan membresía (Sala aquí solo tiene creadorId).
 */
@Component
public class RoomServiceClient {

    private final RestClient restClient;

    public RoomServiceClient(RestClient.Builder builder,
                              @Value("${app.services.room-service-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * Para gating de reportes solo-líder. A diferencia de su equivalente en realtime-service
     * (que no distingue "no soy miembro" de un error real), acá sí se captura Forbidden/BadRequest
     * como "no es líder" - GET /miembros en room-service devuelve 403 si quien pregunta no es
     * miembro de la sala, y sin este catch eso se propagaba como 500 en vez de 403.
     */
    public boolean esLider(Long salaId, Long usuarioId, String token) {
        try {
            MiembroDto[] miembros = restClient.get()
                    .uri("/api/salas/{salaId}/miembros", salaId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(MiembroDto[].class);

            if (miembros == null) return false;

            return Arrays.stream(miembros)
                    .anyMatch(m -> usuarioId.equals(m.usuarioId()) && "LIDER".equals(m.rolEnSala()));
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.BadRequest e) {
            // Forbidden = no es miembro (por ende no es líder); BadRequest = la sala no existe.
            return false;
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudo verificar el rol del usuario en room-service", e);
        }
    }

    public boolean esMiembro(Long salaId, String token) {
        try {
            restClient.get()
                    .uri("/api/salas/{salaId}", salaId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.BadRequest e) {
            return false;
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudo verificar la membresía en room-service", e);
        }
    }

    /** Salas de las que el dueño del token es miembro, para acotar el feed del panel a lo suyo. */
    public List<Long> misSalaIds(String token) {
        try {
            SalaDto[] salas = restClient.get()
                    .uri("/api/salas")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(SalaDto[].class);

            if (salas == null) return List.of();
            return Arrays.stream(salas).map(SalaDto::id).toList();
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudieron obtener las salas del usuario desde room-service", e);
        }
    }

    public record SalaDto(Long id) {}

    public record MiembroDto(Long usuarioId, String rolEnSala) {}
}
