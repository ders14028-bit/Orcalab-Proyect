package com.orcalab.realtime.alerta;

import com.orcalab.realtime.config.AuthContext;
import com.orcalab.realtime.config.JwtUtil;
import com.orcalab.realtime.room.RoomServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// addFilters = false: se testea la logica de autorizacion propia del controller (esMiembro),
// no la cadena de filtros de Spring Security/JWT - eso es una capa distinta ya cubierta por
// JwtAuthFilter en produccion. AuthContext se mockea para no depender de un JWT real.
@WebMvcTest(AlertaHistorialController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertaHistorialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertaRepository alertaRepository;

    @MockitoBean
    private RoomServiceClient roomServiceClient;

    @MockitoBean
    private AuthContext authContext;

    // JwtAuthFilter (un Filter, por eso @WebMvcTest lo instancia igual con addFilters=false)
    // necesita JwtUtil en su constructor - nunca se invoca porque los filtros estan deshabilitados
    // en el dispatch, solo hace falta que el bean exista para poder construir el contexto.
    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void historial_devuelve403_cuandoElUsuarioNoEsMiembro() throws Exception {
        given(authContext.tokenActual()).willReturn("token-de-prueba");
        given(roomServiceClient.esMiembro(anyLong(), anyString())).willReturn(false);

        mockMvc.perform(get("/api/salas/1/alertas"))
                .andExpect(status().isForbidden());
    }

    @Test
    void historial_devuelve200ConLasAlertas_cuandoElUsuarioEsMiembro() throws Exception {
        given(authContext.tokenActual()).willReturn("token-de-prueba");
        given(roomServiceClient.esMiembro(anyLong(), anyString())).willReturn(true);

        Alerta alerta = new Alerta(1L, 99L, "marcador-1", -23.65, -70.4, "Alerta de prueba");
        given(alertaRepository.findBySalaIdOrderByTimestampDesc(1L)).willReturn(List.of(alerta));

        mockMvc.perform(get("/api/salas/1/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].descripcion").value("Alerta de prueba"));
    }
}
