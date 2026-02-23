package org.sirohi.smartnotebook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Stores uploaded files to AWS S3.
 */
@Service
@Profile("s3")
public class S3StorageProvider implements FileStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(S3StorageProvider.class);

    @Override
    public String store(MultipartFile file) {
        // TODO: Implement actual S3 upload logic using AWS SDK
        log.info("Mock uploading file to S3: {}", file.getOriginalFilename());

        // Return a mock S3 URI or object key
        return "s3://mock-bucket/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
    }
}
