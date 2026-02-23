package org.sirohi.smartnotebook.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
@Profile({ "prod", "gdrive" })
public class GoogleDriveConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveConfig.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${app.gdrive.credentials-path:}")
    private String credentialsPath;

    @Bean
    public Drive googleDriveService() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials;

        if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
            log.info("Loading Google Drive credentials from path: {}", credentialsPath);
            credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                    .createScoped(Collections.singleton(DriveScopes.DRIVE_FILE));
        } else {
            log.info("Loading Default Google Drive credentials from GOOGLE_APPLICATION_CREDENTIALS");
            credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(Collections.singleton(DriveScopes.DRIVE_FILE));
        }

        log.info("Google Drive Service initialized successfully.");

        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName("Smart Notebook")
                .build();
    }
}
