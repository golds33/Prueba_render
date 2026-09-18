package com.example.demo.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.NeonRestoreService;
import com.example.demo.service.NeonRestoreService.NeonRestoreException;

@RestController
@RequestMapping("/api/database")
public class DatabaseController {

    private final NeonRestoreService neonRestoreService;

    public DatabaseController(NeonRestoreService neonRestoreService) {
        this.neonRestoreService = neonRestoreService;
    }

    @PostMapping("/restore")
    public ResponseEntity<?> restore(@RequestBody RestoreRequest request) {
        if (request == null || request.restoreToPointInTime() == null
                || request.restoreToPointInTime().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "restore_to_point_in_time es obligatorio"));
        }

        try {
            return neonRestoreService.restore(request.restoreToPointInTime());
        } catch (NeonRestoreException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    Map.of("error", exception.getMessage()));
        }
    }

    public record RestoreRequest(String restore_to_point_in_time) {

        public String restoreToPointInTime() {
            return restore_to_point_in_time;
        }
    }
}
