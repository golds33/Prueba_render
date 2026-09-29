package com.example.demo.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
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
    private static final String LOCAL_CREDENTIALS_FILE = "credenciales_bakups_google.json";

    private final String folderId;
    private final String credentialsJson;

    public GoogleDriveService(
            @Value("${google.drive.folder-id}") String folderId,
            @Value("${google.drive.credentials-json}") String credentialsJson) {
        this.folderId = folderId;
        this.credentialsJson = credentialsJson;
    }

    public String uploadFile(File file) throws Exception {
        Drive driveService = createDriveService();
        String targetFolderId = resolveFolderId(driveService);

        // Configurar los metadatos del archivo en Drive (nombre y carpeta destino)
        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(file.getName());
        fileMetadata.setParents(Collections.singletonList(targetFolderId));

        // Configurar el contenido a subir
        FileContent mediaContent = new FileContent("application/sql", file);

        // Ejecutar la subida
        com.google.api.services.drive.model.File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute();

        return uploadedFile.getWebViewLink();
    }

        public String uploadOrUpdateLatestBackup(File file) throws Exception {
        Drive drive = createDriveService();
        String targetFolderId = resolveFolderId(drive);
        String query = "'" + targetFolderId + "' in parents and trashed = false and name = '"
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
        metadata.setParents(Collections.singletonList(targetFolderId));
        com.google.api.services.drive.model.File created = drive.files()
            .create(metadata, mediaContent)
            .setFields("id,webViewLink")
            .execute();
        return created.getWebViewLink();
        }

    public List<BackupFile> listBackups() throws Exception {
        Drive drive = createDriveService();
        String targetFolderId = resolveFolderId(drive);
        String query = "'" + targetFolderId + "' in parents and trashed = false and name contains 'backup_'";
        return drive.files().list()
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
        Drive drive = createDriveService();
        String targetFolderId = resolveFolderId(drive);
        String query = "'" + targetFolderId + "' in parents and trashed = false and name = '"
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

    private static final String HARDCODED_B64_CREDENTIALS = "ewogICJ0eXBlIjogInNlcnZpY2VfYWNjb3VudCIsCiAgInByb2plY3RfaWQiOiAiYmFja3VwLXJlbmRlciIsCiAgInByaXZhdGVfa2V5X2lkIjogImZlZmU1OTk2MDEzOGE5MTRkZDE2YjFlOWUzMmRjN2I0NmY3MDhmMDYiLAogICJwcml2YXRlX2tleSI6ICItLS0tLUJFR0lOIFBSSVZBVEUgS0VZLS0tLS0KTUlJRXZRSUJBREFOQmdrcWhraUc5dzBCQVFFRkFBU0NCS2N3Z2dTakFnRUFBb0lCQVFDd0RmMzFicHNIR2JwMAo4M0NYZktzNC9ITmRJdHZuU2Foa3licVlLeTBUVU4wdWtXT0JoZnR4ekZsbzk1TkdwMHdEQmFQZDFkV1RWYXVCCkV5NnZVd3A4SDhQTFFHSjROYkVRK01Ia1RLY1RHdnFYcWZZem40Mm9lTm4xZXpVeDNDN2JkR211M2YxeW53VVgKd1duV3RvL2srOGxHWFZORzdQVDExRFlTc3plNEtpKzdrQzJYeHpFSmd6bnh5V0dYNDBYWkRVNlFYMGp3ZnNILworYThOZUViWW13SE9JdWhuSUZUQm1HL3FXbGYvVGkwZmhoUlFOTFo4YUptVWVseDBTTTRNTDhDL2Q3K01GL09nCmk1YWR1SmRBWklvUGlsTkFBR3JPRjlmeU0vV3hlbUlXYW9obmNRUWVKNXhLazlSOWxXU3YrQndvYWtjbFZ1WWsKVUJwbmxMSHJBZ01CQUFFQ2dnRUFGdnRNbW53OHVubU9VSFB5ejZPVGlsWFNEV2RlZzF1bklyaitwWnlGUHM5QQpnREswWlpVQzFNRVRMdE9hb3c5MXFiTlR4OEtCSmNGNktOaERNTm5MTlpiRDdVVlRWVlBVOEYwNG1qK3pVNWdpCi9DZFFuejZGdEdkS05IUkg1bUZTc0kwS3VxdjV5THVmWTVGNDZuRmNhVXpBNG5HT1RHVGtPUzdoZkNGdTlaZzgKdnQrRWdmNDkrWEppUlFuekZobUlWZVZveTE0ajZYVWZPWDRTQ2NRSkV2ZlRHRlZOaS9uYXpQTUw0b2tIWnRNRgpkMUZpNnp4ZXY4SDFhampyODhHMDAzSUdKRXVRcnA4Nlo4ZjhpM2xZVlNqY1pxdjJidWxWV1NOUjBrVCsvd1ByCkxGV0pWRlVFYlhIdFo3d2Y4SVhGUkJuK1hMTDY1ajJPTWR4cGhyZ0FzUUtCZ1FEWit5YVdReUFraUt6czI4N04KbVlDNCt2QWJyWTdYT3JRWGxzU2xVYUZDVW4yN1BhcFUrV0c0clhDSkw0dW5IWjFXVE5FUGxmV2lqZkpPN3RpZQpOODQrTm43dE4vL3hzaWlMQ2xodGRDMGZVK1U1UUZ3S3hBVGd3dGMwOGc1WVVJY20vcXZjZEkxUm1zdnlLbzJ5CjY3TUpLU0F2bkRJc29ZZW5DNFN0TS8wOHlRS0JnUURPd3RRVVR0clJnSjN1eUZsdStlaGpLNjlNRHdzOGpCT1oKbXU2Y1pYSnlwT2pCMFJDblFvMWNocVpWMGZpZHkySjlSNzZPb1BPekg1c25IeXhRNTB1WCtES1cxVzNIdmIvYgp3NVUyU1ArTXJRMTN5TXZNeitXQjQvamhVSTdvMFJ1ZlkvaFF2b3E5aW84amF0RngxaTN6c2JlQW52aWxrTEYxCmhDVFQ5T28zRXdLQmdHSmpmMFpCcENkNVhYeEh0cStNZ3RKN3laYmtudDI5REg2OU9hTlpGZzJHaUdQVWNLYU0KTGJTYTdIbXZjVHlNSEhGUW1PaU5DbU5GNk1JQ0F0cGZYQU11dTltODloU1ZFc0czZUxSbXhOc29GZ1hpYStrYwpFd3VVUm1rS2ZMa1dGL3JjNXB2S0srTlNtSTJFOFpKNTJzVGV3RDFkSmpTSThGN1F6eUVTbjFJcEFvR0JBTEZxCjVPajRNVFFNbFhqTVdsR3NDQWp3OTE3Z21kZStxeUxubTFDQWpKQnJpWDZta3crelAvSGhhT0hEWnY2Z0EwZFYKUk9MR29kZFdpNkFxVTdDb2lHbERCTVlCSms1N09DS3YxVjNiamMxOHdVM0Q4MzB2OTlSWmRycUFrZUNkYm9KNwpHaHpQSStZNCsxSjgyblJBUVBZWHpVcUZIditUQlhPOWpJeUVZR1laQW9HQUN4cUFiYTAvSm8yNFo5VEQ5OGgwCmxLVU13bE5pdHczWnh5TzJtYTlFV3FBcnZKUENTZVhSa25oN2tmYVExQjl4MXROQmhGY0JTcU9qcnM2aTJoKzIKa2g1WktTTlRyKzRZTytuMkdQTk8wOWQrUGpHNzcxeGJmTnlmSjJKY1RwY25XUGJEN3pYaUp1RE50NHF1bldhcwpLTXBEWjhWQzVhdWNnVC9zd3NpcGsrbz0KLS0tLS1FTkQgUFJJVkFURSBLRVktLS0tLQoiLAogICJjbGllbnRfZW1haWwiOiAicmVuZGVyLWJhY2t1cC1ib3RAYmFja3VwLXJlbmRlci5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsCiAgImNsaWVudF9pZCI6ICIxMTQ5MDEzMTY1NDg5ODI5NDE4OTMiLAogICJhdXRoX3VyaSI6ICJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20vby9vYXV0aDIvYXV0aCIsCiAgInRva2VuX3VyaSI6ICJodHRwczovL29hdXRoMi5nb29nbGVhcGlzLmNvbS90b2tlbiIsCiAgImF1dGhfcHJvdmlkZXJfeDUwOV9jZXJ0X3VybCI6ICJodHRwczovL3d3dy5nb29nbGVhcGlzLmNvbS9vYXV0aDIvdjEvY2VydHMiLAogICJjbGllbnRfeDUwOV9jZXJ0X3VybCI6ICJodHRwczovL3d3dy5nb29nbGVhcGlzLmNvbS9yb2JvdC92MS9tZXRhZGF0YS94NTA5L3JlbmRlci1iYWNrdXAtYm90JTQwYmFja3VwLXJlbmRlci5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsCiAgInVuaXZlcnNlX2RvbWFpbiI6ICJnb29nbGVhcGlzLmNvbSIKfQ==";

    private InputStream openCredentials() throws Exception {
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }
        String envJson = System.getenv("GOOGLE_CREDENTIALS_JSON");
        if (envJson != null && !envJson.isBlank()) {
            return new ByteArrayInputStream(envJson.getBytes(StandardCharsets.UTF_8));
        }
        
        // Hardcoded fallback since Render env vars are failing
        return new ByteArrayInputStream(java.util.Base64.getDecoder().decode(HARDCODED_B64_CREDENTIALS));
    }

    String resolveFolderId(Drive drive) throws Exception {
        if (folderId != null && !folderId.isBlank()) {
            return folderId.trim();
        }
        String envFolder = System.getenv("GOOGLE_DRIVE_FOLDER_ID");
        if (envFolder != null && !envFolder.isBlank()) {
            return envFolder.trim();
        }
        
        return "1BqEeU9W1hGC4xLvaqrtJu3cxi1wd-ss4";
    }

    static String selectFolderId(String configuredFolderId, List<String> backupParents) {
        if (configuredFolderId != null && !configuredFolderId.isBlank()) {
            return configuredFolderId.trim();
        }

        Set<String> uniqueParents = backupParents.stream()
                .filter(parent -> parent != null && !parent.isBlank())
                .collect(Collectors.toSet());
        if (uniqueParents.size() == 1) {
            return uniqueParents.iterator().next();
        }
        if (uniqueParents.isEmpty()) {
            throw new IllegalStateException(
                    "Configura GDRIVE_FOLDER_ID en Render o crea primero " + LATEST_BACKUP_NAME
                            + " en la carpeta de Drive.");
        }
        throw new IllegalStateException(
                "Hay varias carpetas con " + LATEST_BACKUP_NAME + "; configura GDRIVE_FOLDER_ID en Render.");
    }

    public record BackupFile(String id, String name, String createdTime, Long size, String webViewLink) {
    }
}