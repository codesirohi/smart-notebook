package org.sirohi.smartnotebook.service;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;

/**
 * Stores uploaded files to Google Drive.
 */
@Service
@Profile({ "prod", "gdrive" })
public class GoogleDriveStorageProvider implements FileStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveStorageProvider.class);

    private final Drive driveService;
    private final String folderId;

    public GoogleDriveStorageProvider(Drive driveService,
            @Value("${app.gdrive.folder-id:}") String folderId) {
        this.driveService = driveService;
        this.folderId = folderId;
        if (this.folderId == null || this.folderId.trim().isEmpty()) {
            log.warn("Google Drive folder-id is not set. Files will be uploaded to root directory.");
        } else {
            log.info("Google Drive Storage Provider initialized with folder ID: {}", this.folderId);
        }
    }

    @Override
    public String store(MultipartFile file) {
        try {
            log.info("Uploading file to Google Drive: {}", file.getOriginalFilename());

            File fileMetadata = new File();
            fileMetadata.setName(file.getOriginalFilename());
            if (folderId != null && !folderId.trim().isEmpty()) {
                fileMetadata.setParents(Collections.singletonList(folderId));
            }

            InputStreamContent mediaContent = new InputStreamContent(
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                    file.getInputStream());

            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();

            log.info("Successfully uploaded to Google Drive. File ID: {}", uploadedFile.getId());
            return "gdrive://" + uploadedFile.getId();
        } catch (Exception e) {
            log.error("Failed to upload file to Google Drive", e);
            throw new RuntimeException("Failed to upload file to Google Drive", e);
        }
    }
}
