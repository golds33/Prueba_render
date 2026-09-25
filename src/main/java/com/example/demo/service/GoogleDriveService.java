package com.example.demo.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

@Service
public class GoogleDriveService {

    public static final String LATEST_BACKUP_NAME = "backup_latest.sql";

    private final String folderId;
    private final String credentialsPath;
    private final String credentialsJson;

    public GoogleDriveService(
            @Value("${google.drive.folder-id}") String folderId,
            @Value("${google.drive.credentials-path}") String credentialsPath,
            @Value("${google.drive.credentials-json}") String credentialsJson) {
        this.folderId = folderId;
        this.credentialsPath = credentialsPath;
        this.credentialsJson = credentialsJson;
    }

    public String uploadFile(File file) throws Exception {
        validateConfiguration();
        Drive driveService = createDriveService();

        // Configurar los metadatos del archivo en Drive (nombre y carpeta destino)
        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(file.getName());
        fileMetadata.setParents(Collections.singletonList(folderId));

        // Configurar el contenido a subir
        FileContent mediaContent = new FileContent("application/sql", file);

        // Ejecutar la subida
        com.google.api.services.drive.model.File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute();

        return uploadedFile.getWebViewLink();
    }

        public String uploadOrUpdateLatestBackup(File file) throws Exception {
        validateConfiguration();
        Drive drive = createDriveService();
        String query = "'" + folderId + "' in parents and trashed = false and name = '"
            + LATEST_BACKUP_NAME + "'";
        List<com.google.api.services.drive.model.File> matches = drive.files().list()
            .setQ(query)
            .setPageSize(10)
            .setFields("files(id,webViewLink)")
            .execute()
            .getFiles();
        FileContent mediaContent = new FileContent("application/sql", file);

        if (!matches.isEmpty()) {
            com.google.api.services.drive.model.File updated = drive.files()
                .update(matches.get(0).getId(), null, mediaContent)
                .setFields("id,webViewLink")
                .execute();
            return updated.getWebViewLink();
        }

        com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File();
        metadata.setName(LATEST_BACKUP_NAME);
        metadata.setParents(Collections.singletonList(folderId));
        com.google.api.services.drive.model.File created = drive.files()
            .create(metadata, mediaContent)
            .setFields("id,webViewLink")
            .execute();
        return created.getWebViewLink();
        }

    public List<BackupFile> listBackups() throws Exception {
        validateConfiguration();
        String query = "'" + folderId + "' in parents and trashed = false and name contains 'backup_'";
        return createDriveService().files().list()
                .setQ(query)
                .setOrderBy("createdTime desc")
                .setPageSize(100)
                .setFields("files(id,name,createdTime,size,webViewLink)")
                .execute()
                .getFiles()
                .stream()
                .map(file -> new BackupFile(file.getId(), file.getName(),
                        file.getCreatedTime() == null ? null : file.getCreatedTime().toStringRfc3339(),
                        file.getSize(), file.getWebViewLink()))
                .collect(Collectors.toList());
    }

    public Path downloadBackup(String fileId) throws Exception {
        BackupFile backup = listBackups().stream()
                .filter(file -> Objects.equals(file.id(), fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La copia no existe en la carpeta configurada."));

        Path destination = Files.createTempFile("restore-", "-" + backup.name());
        try (var output = Files.newOutputStream(destination)) {
            createDriveService().files().get(fileId).executeMediaAndDownloadTo(output);
        }
        return destination;
    }

    public Path downloadLatestBackup() throws Exception {
        validateConfiguration();
        Drive drive = createDriveService();
        String query = "'" + folderId + "' in parents and trashed = false and name = '"
                + LATEST_BACKUP_NAME + "'";
        List<com.google.api.services.drive.model.File> matches = drive.files().list()
                .setQ(query)
                .setPageSize(1)
                .setFields("files(id,name)")
                .execute()
                .getFiles();
        if (matches.isEmpty()) {
            throw new IllegalStateException("No existe backup_latest.sql en Google Drive.");
        }

        Path destination = Files.createTempFile("restore-", ".sql");
        try (var output = Files.newOutputStream(destination)) {
            drive.files().get(matches.get(0).getId()).executeMediaAndDownloadTo(output);
        }
        return destination;
    }

    private Drive createDriveService() throws Exception {
        try (InputStream credentialsStream = openCredentials()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));
            return new Drive.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("PostgreSQL Backup Bot")
                    .build();
        }
    }

    private InputStream openCredentials() throws Exception {
        if (!credentialsJson.isBlank()) {
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }
        if (!credentialsPath.isBlank()) {
            return new FileInputStream(credentialsPath);
        }
        throw new IllegalStateException(
                "Configura GOOGLE_DRIVE_CREDENTIALS_JSON o GOOGLE_DRIVE_CREDENTIALS_PATH.");
    }

    private void validateConfiguration() {
        if (folderId.isBlank()) {
            throw new IllegalStateException("Falta GOOGLE_DRIVE_FOLDER_ID.");
        }
    }

    public record BackupFile(String id, String name, String createdTime, Long size, String webViewLink) {
    }
}