package com.example.demo.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

class GoogleDriveServiceTests {
	@Test
	void configuredFolderIdTakesPrecedence() {
		assertEquals("configured-folder", GoogleDriveService.selectFolderId(
				" configured-folder ", List.of("existing-backup-folder")));
	}

	@Test
	void uniqueExistingBackupParentIsDetected() {
		assertEquals("existing-backup-folder", GoogleDriveService.selectFolderId(
				"", List.of("existing-backup-folder", "existing-backup-folder")));
	}

	@Test
	void missingFolderIdQueriesDriveForExistingBackupParent() throws Exception {
		Drive drive = mock(Drive.class);
		Drive.Files driveFiles = mock(Drive.Files.class);
		Drive.Files.List backupQuery = mock(Drive.Files.List.class);
		FileList result = new FileList().setFiles(List.of(
				new com.google.api.services.drive.model.File().setParents(List.of("existing-backup-folder"))));

		when(drive.files()).thenReturn(driveFiles);
		when(driveFiles.list()).thenReturn(backupQuery);
		when(backupQuery.setQ("name = 'backup_latest.sql' and trashed = false")).thenReturn(backupQuery);
		when(backupQuery.setPageSize(100)).thenReturn(backupQuery);
		when(backupQuery.setFields("files(parents)")).thenReturn(backupQuery);
		when(backupQuery.execute()).thenReturn(result);

		GoogleDriveService service = new GoogleDriveService("", "{}");

		assertEquals("existing-backup-folder", service.resolveFolderId(drive));
	}

	@Test
	void missingOrAmbiguousBackupParentFailsWithoutGuessing() {
		IllegalStateException missingFolder = assertThrows(IllegalStateException.class,
				() -> GoogleDriveService.selectFolderId("", List.of()));
		IllegalStateException ambiguousFolder = assertThrows(IllegalStateException.class,
				() -> GoogleDriveService.selectFolderId("", List.of("folder-a", "folder-b")));

		assertTrue(missingFolder.getMessage().contains("GDRIVE_FOLDER_ID"));
		assertTrue(ambiguousFolder.getMessage().contains("varias carpetas"));
	}
}