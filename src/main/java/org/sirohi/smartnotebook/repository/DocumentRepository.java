package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByChecksum(String checksum);

    Page<Document> findByNotebookId(UUID notebookId, Pageable pageable);

    @Query("SELECT d.id FROM Document d WHERE d.notebook.id = :notebookId")
    List<UUID> findIdsByNotebookId(UUID notebookId);
}
