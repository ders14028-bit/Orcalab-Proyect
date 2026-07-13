package com.orcalab.realtime.canal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/salas/{salaId}/canales")
public class CanalController {

    private final CanalService canalService;

    public CanalController(CanalService canalService) {
        this.canalService = canalService;
    }

    @GetMapping
    public ResponseEntity<List<Canal>> listar(@PathVariable Long salaId) {
        return ResponseEntity.ok(canalService.listar(salaId));
    }

    @PostMapping
    public ResponseEntity<Canal> crear(@PathVariable Long salaId, @Valid @RequestBody CanalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canalService.crear(salaId, request));
    }

    @DeleteMapping("/{canalId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long salaId, @PathVariable String canalId) {
        canalService.eliminar(salaId, canalId);
        return ResponseEntity.noContent().build();
    }
}
