package com.example.demo.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.DatabaseBackupBot;

@RestController
public class DemoController {

    private final DatabaseBackupBot backupBot;

    public DemoController(DatabaseBackupBot backupBot) {
        this.backupBot = backupBot;
    }

    @PostMapping("/api/delete-db")
    public ResponseEntity<?> deleteDatabase() {
        try {
            backupBot.deleteDatabase();
            return ResponseEntity.ok(Map.of("message", "Base de datos eliminada completamente. Tu sistema sigue activo."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/restore-db")
    public ResponseEntity<?> restoreDatabase() {
        try {
            backupBot.restoreLatest();
            return ResponseEntity.ok(Map.of("message", "Base de datos restaurada con éxito desde Google Drive."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
