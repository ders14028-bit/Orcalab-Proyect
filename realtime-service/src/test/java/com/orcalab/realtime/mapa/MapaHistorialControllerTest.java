package com.orcalab.realtime.mapa;

import com.orcalab.realtime.broadcast.RealtimeBroadcaster;
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

// addFilters = false: mismo criterio que AlertaHistorialControllerTest - se aisla la logica de
// autorizacion propia del controller (esMiembro), no la cadena de Spring Security/JWT.
@WebMvcTest(MapaHistorialController.class)
@AutoConfigureMockMvc(addFilters = false)
class MapaHistorialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarcadorRepository marcadorRepository;

    @MockitoBean
    private RutaRepository rutaRepository;

    @MockitoBean
    private RoomServiceClient roomServiceClient;

    @MockitoBean
    private AuthContext authContext;

    @MockitoBean
    private RealtimeBroadcaster broadcaster;

    // JwtAuthFilter (un Filter, por eso @WebMvcTest lo instancia igual con addFilters=false)
    // necesita JwtUtil en su constructor - nunca se invoca porque los filtros estan deshabilitados
    // en el dispatch, solo hace falta que el bean exista para poder construir el contexto.
    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void marcadores_devuelve403_cuandoElUsuarioNoEsMiembro() throws Exception {
        given(authContext.tokenActual()).willReturn("token-de-prueba");
        given(roomServiceClient.esMiembro(anyLong(), anyString())).willReturn(false);

        mockMvc.perform(get("/api/salas/1/marcadores"))
                .andExpect(status().isForbidden());
    }

    @Test
    void marcadores_devuelve200ConLosMarcadores_cuandoElUsuarioEsMiembro() throws Exception {
        given(authContext.tokenActual()).willReturn("token-de-prueba");
        given(roomServiceClient.esMiembro(anyLong(), anyString())).willReturn(true);

        Marcador marcador = new Marcador(1L, 99L, -23.65, -70.4, "AVISTAMIENTO", "Marcador de prueba");
        given(marcadorRepository.findBySalaId(1L)).willReturn(List.of(marcador));

        mockMvc.perform(get("/api/salas/1/marcadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].descripcion").value("Marcador de prueba"));
    }
}
