package com.orcalab.reporting.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Resuelve usuarioId -> nombre contra auth-service, para no reportar solo IDs crudos en el CSV. */
@Component
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(RestClient.Builder builder,
                              @Value("${app.services.auth-service-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Map<Long, String> obtenerNombresPorId(Collection<Long> ids, String token) {
        if (ids.isEmpty()) return Map.of();

        String idsParam = ids.stream().map(String::valueOf).collect(Collectors.joining(","));

        try {
            UsuarioResumenDto[] usuarios = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/auth/usuarios/resumen")
                            .queryParam("ids", idsParam)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(UsuarioResumenDto[].class);

            if (usuarios == null) return Map.of();
            return Arrays.stream(usuarios).collect(Collectors.toMap(UsuarioResumenDto::id, UsuarioResumenDto::nombre));
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudieron resolver los nombres de usuario desde auth-service", e);
        }
    }

    public record UsuarioResumenDto(Long id, String nombre) {}
}
