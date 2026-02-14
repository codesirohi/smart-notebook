package org.sirohi.smartnotebook.source;

import java.io.InputStream;

/**
 * Strategy interface for fetching document content from different sources.
 *
 * <p>
 * Implementations handle source-specific logic (local filesystem, S3, Google
 * Drive, etc.)
 * while {@link SourceService} orchestrates dedup, storage, and enqueueing.
 * </p>
 *
 * <h3>Extension Guide</h3>
 * <p>
 * To add a new file source (e.g., Google Drive):
 * </p>
 * <ol>
 * <li>Create {@code GoogleDriveSource implements FileSource}</li>
 * <li>Annotate with {@code @Service} and {@code @ConditionalOnProperty} for
 * config-driven activation</li>
 * <li>Spring auto-discovers it via {@code List<FileSource>} injection in
 * {@code SourceService}</li>
 * </ol>
 *
 * @see SourceReference
 * @see SourceService
 */
public interface FileSource {

    /**
     * Fetches the content of a document from this source.
     *
     * @param ref the source reference describing where to fetch from
     * @return an InputStream of the document content (caller must close)
     * @throws java.io.IOException if the content cannot be fetched
     */
    InputStream fetchContent(SourceReference ref) throws java.io.IOException;

    /**
     * Checks whether this source implementation handles the given source type.
     *
     * @param sourceType the source type identifier (e.g., "local", "s3", "gdrive")
     * @return true if this implementation can handle the source type
     */
    boolean supports(String sourceType);
}
