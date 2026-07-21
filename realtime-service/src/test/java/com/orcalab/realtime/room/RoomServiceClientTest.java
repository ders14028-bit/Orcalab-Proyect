package com.orcalab.realtime.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Usa MockRestServiceServer (no mocks manuales de la cadena fluida de RestClient) porque
 * verifica de verdad que un 403/500 real de room-service se traduce en la excepcion esperada -
 * mockear la cadena a mano solo verificaria que el catch atrapa lo que uno mismo decidio lanzar.
 */
class RoomServiceClientTest {

    private static final String BASE_URL = "http://room-service";
    private static final Long SALA_ID = 1L;
    private static final String TOKEN = "token-de-prueba";

    private MockRestServiceServer server;
    private RoomServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RoomServiceClient(builder, BASE_URL);
    }

    @Test
    void esMiembro_devuelveTrue_cuandoRoomServiceConfirmaMembresia() {
        server.expect(requestTo(BASE_URL + "/api/salas/" + SALA_ID))
                .andRespond(withSuccess());

        assertThat(client.esMiembro(SALA_ID, TOKEN)).isTrue();
    }

    @Test
    void esMiembro_devuelveFalse_cuandoRoomServiceDiceQueNoEsMiembro() {
        server.expect(requestTo(BASE_URL + "/api/salas/" + SALA_ID))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client.esMiembro(SALA_ID, TOKEN)).isFalse();
    }

    @Test
    void esMiembro_devuelveFalse_cuandoLaSalaNoExiste() {
        server.expect(requestTo(BASE_URL + "/api/salas/" + SALA_ID))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.esMiembro(SALA_ID, TOKEN)).isFalse();
    }

    @Test
    void esMiembro_failClosed_cuandoRoomServiceNoResponde() {
        server.expect(requestTo(BASE_URL + "/api/salas/" + SALA_ID))
                .andRespond(withServerError());

        // Fail closed: un error real (no un 403/400 de negocio) debe denegar lanzando,
        // nunca devolver true por defecto ni tragarse el error silenciosamente.
        assertThatThrownBy(() -> client.esMiembro(SALA_ID, TOKEN))
                .isInstanceOf(IllegalStateException.class);
    }
}
