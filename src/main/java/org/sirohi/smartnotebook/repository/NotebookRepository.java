package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.Notebook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotebookRepository extends JpaRepository<Notebook, UUID> {
    java.util.Optional<Notebook> findFirstByOrderByCreatedAtAsc();
}
