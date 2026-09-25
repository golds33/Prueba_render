package com.example.demo.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.DatabaseBackupBot;
import com.example.demo.service.GoogleDriveService;

@RestController
@RequestMapping("/api/database")
public class DatabaseController {

    private final DatabaseBackupBot backupBot;
    private final GoogleDriveService googleDriveService;

    public DatabaseController(DatabaseBackupBot backupBot, GoogleDriveService googleDriveService) {
        this.backupBot = backupBot;
        this.googleDriveService = googleDriveService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/backups")
    public ResponseEntity<?> listBackups() {
        try {
            return ResponseEntity.ok(googleDriveService.listBackups());
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/restore-file")
    public ResponseEntity<?> restoreFile(@RequestBody RestoreFileRequest request) {
        if (request == null || request.fileId() == null || request.fileId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file_id es obligatorio"));
        }
        try {
            return ResponseEntity.ok(Map.of("message",
                    backupBot.restoreFromGoogleDrive(request.fileId(), googleDriveService)));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    Map.of("error", exception.getMessage()));
        }
    }

   @PostMapping("/backup")
    public ResponseEntity<?> triggerManualBackup() {
        try {
            backupBot.backupLatest();
            return ResponseEntity.ok(Map.of(
                "status", "success", 
                "message", "Respaldo manual creado y enviado a tu Drive exitosamente."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("error", e.getMessage()));
        }
    }

    public record RestoreFileRequest(String file_id) {
        public String fileId() {
            return file_id;
        }
    }
}