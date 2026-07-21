package com.orcalab.reporting.controller;

import com.orcalab.reporting.auth.AuthServiceClient;
import com.orcalab.reporting.config.AuthContext;
import com.orcalab.reporting.realtime.RealtimeServiceClient;
import com.orcalab.reporting.room.RoomServiceClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HU-13: reporte CSV de marcadores de una sala, exclusivo para el líder. */
@RestController
public class MarcadorReporteController {

    // Coincide EXACTO con el texto que antepone el frontend al aceptar una sugerencia de IA
    // (MarkerFormModal.tsx: "Especie sugerida: {especie} (NN% confianza)"). Es un parseo de texto
    // libre sobre un campo que el usuario puede seguir editando despues - si rompe el patron
    // (lo borra, lo reescribe), el reporte simplemente no detecta que hubo IA. No hay ningun
    // campo estructurado que lo garantice (ver hallazgo: la foto en si nunca se persiste en
    // ningun lado, ni el resultado de la clasificacion - solo esta linea de texto si el usuario
    // decidio conservarla).
    private static final Pattern PATRON_SUGERENCIA_IA =
            Pattern.compile("^Especie sugerida: (.+) \\((\\d{1,3})% confianza\\)$", Pattern.MULTILINE);

    private final RoomServiceClient roomServiceClient;
    private final RealtimeServiceClient realtimeServiceClient;
    private final AuthServiceClient authServiceClient;
    private final AuthContext authContext;

    public MarcadorReporteController(RoomServiceClient roomServiceClient, RealtimeServiceClient realtimeServiceClient,
                                      AuthServiceClient authServiceClient, AuthContext authContext) {
        this.roomServiceClient = roomServiceClient;
        this.realtimeServiceClient = realtimeServiceClient;
        this.authServiceClient = authServiceClient;
        this.authContext = authContext;
    }

    @GetMapping("/api/reportes/salas/{salaId}/marcadores/csv")
    public ResponseEntity<byte[]> descargarCsv(@PathVariable Long salaId) {
        Long usuarioId = authContext.usuarioIdActual();
        String token = authContext.tokenActual();

        if (!roomServiceClient.esLider(salaId, usuarioId, token)) {
            throw new AccessDeniedException("Solo el líder de la sala puede descargar este reporte");
        }

        List<RealtimeServiceClient.MarcadorDto> marcadores = realtimeServiceClient.obtenerMarcadores(salaId, token);

        Set<Long> idsUsuarios = new LinkedHashSet<>();
        for (var m : marcadores) {
            idsUsuarios.add(m.creadorId());
            idsUsuarios.add(m.usuarioId());
        }
        Map<Long, String> nombresPorId = authServiceClient.obtenerNombresPorId(idsUsuarios, token);

        String csv = construirCsv(marcadores, nombresPorId);
        byte[] cuerpo = agregarBom(csv.getBytes(StandardCharsets.UTF_8));

        String nombreArchivo = "marcadores-sala-" + salaId + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(cuerpo);
    }

    private String construirCsv(List<RealtimeServiceClient.MarcadorDto> marcadores, Map<Long, String> nombresPorId) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,tipo,lat,lng,creado_por,editado_por,fecha_creacion,fecha_ultima_edicion,")
          .append("descripcion,especie_sugerida_ia,confianza_ia\n");

        for (var m : marcadores) {
            Matcher match = PATRON_SUGERENCIA_IA.matcher(m.descripcion() != null ? m.descripcion() : "");
            String especieIa = "";
            String confianzaIa = "";
            if (match.find()) {
                especieIa = match.group(1).trim();
                confianzaIa = match.group(2);
            }

            String creadoPor = nombresPorId.getOrDefault(m.creadorId(), "");
            // usuarioId = ultimo editor (se sobreescribe en cada edicion); si coincide con
            // creadorId, nadie mas lo edito (o el propio creador lo volvio a guardar, caso que
            // este reporte no puede distinguir sin un historial de ediciones, que hoy no existe).
            boolean editadoPorOtro = !Objects.equals(m.usuarioId(), m.creadorId());
            String editadoPor = editadoPorOtro ? nombresPorId.getOrDefault(m.usuarioId(), "") : "";

            sb.append(csvEscape(m.id())).append(',')
              .append(csvEscape(m.tipo())).append(',')
              .append(m.latitud()).append(',')
              .append(m.longitud()).append(',')
              .append(csvEscape(creadoPor)).append(',')
              .append(csvEscape(editadoPor)).append(',')
              .append(csvEscape(String.valueOf(m.fechaCreacion()))).append(',')
              .append(csvEscape(String.valueOf(m.fechaUltimaEdicion()))).append(',')
              .append(csvEscape(m.descripcion())).append(',')
              .append(csvEscape(especieIa)).append(',')
              .append(csvEscape(confianzaIa))
              .append('\n');
        }

        return sb.toString();
    }

    private static String csvEscape(String valor) {
        if (valor == null) return "";
        boolean necesitaComillas = valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r");
        String escapado = valor.replace("\"", "\"\"");
        return necesitaComillas ? "\"" + escapado + "\"" : escapado;
    }

    // Excel abre CSV UTF-8 sin BOM asumiendo el codepage local y rompe acentos ("Delfín" ->
    // "DelfÃ­n"); el BOM fuerza a que lo detecte como UTF-8 de verdad.
    private static byte[] agregarBom(byte[] contenidoUtf8) {
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] conBom = new byte[bom.length + contenidoUtf8.length];
        System.arraycopy(bom, 0, conBom, 0, bom.length);
        System.arraycopy(contenidoUtf8, 0, conBom, bom.length, contenidoUtf8.length);
        return conBom;
    }
}
