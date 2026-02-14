package org.sirohi.smartnotebook.source;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * V1 implementation of {@link FileSource} for local file uploads (multipart
 * form data).
 *
 * <p>
 * Reads content from a temporary file path stored in
 * {@link SourceReference#location()}.
 * </p>
 */
@Service
public class LocalUploadSource implements FileSource {

    @Override
    public InputStream fetchContent(SourceReference ref) throws IOException {
        return Files.newInputStream(Path.of(ref.location()));
    }

    @Override
    public boolean supports(String sourceType) {
        return "local".equalsIgnoreCase(sourceType);
    }
}
