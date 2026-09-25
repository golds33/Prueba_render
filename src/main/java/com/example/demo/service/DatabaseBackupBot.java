package com.example.demo.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DatabaseBackupBot {

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.datasource.username:}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    private final GoogleDriveService googleDriveService;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseBackupBot(GoogleDriveService googleDriveService, JdbcTemplate jdbcTemplate) {
        this.googleDriveService = googleDriveService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void executeAutomatedBackup() {
        try {
            backupLatest();
        } catch (Exception e) {
            System.err.println("🚨 [BOT] Error crítico durante el respaldo: " + e.getMessage());
        }
    }

    public void backupLatest() throws Exception {
        File backupFile = new File(GoogleDriveService.LATEST_BACKUP_NAME);
        try {
            runDatabaseCommand("pg_dump", backupFile.toPath());
            googleDriveService.uploadOrUpdateLatestBackup(backupFile);
        } finally {
            Files.deleteIfExists(backupFile.toPath());
        }
    }

    public void deleteDatabase() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS public CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA public");
    }

    public void restoreLatest() throws Exception {
        Path backupFile = googleDriveService.downloadLatestBackup();
        try {
            deleteDatabase();
            runDatabaseCommand("psql", backupFile);
        } finally {
            Files.deleteIfExists(backupFile);
        }
    }

    public String restoreFromGoogleDrive(String fileId, GoogleDriveService driveService) throws Exception {
        File backupFile = driveService.downloadBackup(fileId).toFile();
        try {
            runDatabaseCommand("psql", backupFile.toPath());
            return "Copia restaurada correctamente.";
        } finally {
            if (backupFile.exists()) {
                backupFile.delete();
            }
        }
    }

    private void runDatabaseCommand(String command, Path sqlFile) throws Exception {
        String cleanUrl = dbUrl.replaceFirst("^jdbc:", "");
        ProcessBuilder processBuilder = new ProcessBuilder(command, "--dbname=" + cleanUrl,
                "--file=" + sqlFile.toAbsolutePath());
        if (!dbUser.isBlank()) {
            processBuilder.environment().put("PGUSER", dbUser);
        }
        if (!dbPassword.isBlank()) {
            processBuilder.environment().put("PGPASSWORD", dbPassword);
        }
        Process process = processBuilder.redirectErrorStream(true).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(command + " no pudo completar la operación sobre PostgreSQL.");
        }
    }
}