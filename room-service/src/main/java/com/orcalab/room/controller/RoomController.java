package com.orcalab.room.controller;

import com.orcalab.room.dto.CambiarRolRequest;
import com.orcalab.room.dto.CrearSalaRequest;
import com.orcalab.room.dto.MiembroResponse;
import com.orcalab.room.dto.SalaResponse;
import com.orcalab.room.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/salas")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<SalaResponse> crearSala(@Valid @RequestBody CrearSalaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.crearSala(request));
    }

    @GetMapping
    public ResponseEntity<List<SalaResponse>> listarMisSalas() {
        return ResponseEntity.ok(roomService.listarMisSalas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalaResponse> obtenerSala(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.obtenerSala(id));
    }

    @GetMapping("/{id}/miembros")
    public ResponseEntity<List<MiembroResponse>> listarMiembros(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.listarMiembros(id));
    }

    @PostMapping("/{id}/miembros")
    public ResponseEntity<Void> unirseASala(@PathVariable Long id) {
        roomService.unirseASala(id);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/miembros/me")
    public ResponseEntity<Void> salirDeSala(@PathVariable Long id) {
        roomService.salirDeSala(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/miembros/{usuarioId}")
    public ResponseEntity<Void> expulsarMiembro(@PathVariable Long id, @PathVariable Long usuarioId) {
        roomService.expulsarMiembro(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/miembros/{usuarioId}/rol")
    public ResponseEntity<Void> cambiarRol(@PathVariable Long id, @PathVariable Long usuarioId,
                                            @Valid @RequestBody CambiarRolRequest request) {
        roomService.cambiarRol(id, usuarioId, request.getNuevoRol());
        return ResponseEntity.noContent().build();
    }
}