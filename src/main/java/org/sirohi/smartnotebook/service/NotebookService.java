package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.NotebookRequest;
import org.sirohi.smartnotebook.dto.NotebookResponse;
import org.sirohi.smartnotebook.exception.ResourceNotFoundException;
import org.sirohi.smartnotebook.model.Notebook;
import org.sirohi.smartnotebook.repository.ChatRepository;
import org.sirohi.smartnotebook.repository.DocumentRepository;
import org.sirohi.smartnotebook.repository.NotebookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotebookService {

    private final NotebookRepository notebookRepo;
    private final DocumentRepository documentRepo;
    private final ChatRepository chatRepo;

    public NotebookService(NotebookRepository notebookRepo,
            DocumentRepository documentRepo,
            ChatRepository chatRepo) {
        this.notebookRepo = notebookRepo;
        this.documentRepo = documentRepo;
        this.chatRepo = chatRepo;
    }

    public NotebookResponse create(NotebookRequest request) {
        Notebook notebook = new Notebook();
        notebook.setName(request.name());
        notebook.setDescription(request.description());
        notebook = notebookRepo.save(notebook);
        return toResponse(notebook);
    }

    @Transactional(readOnly = true)
    public List<NotebookResponse> listAll() {
        return notebookRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotebookResponse getById(UUID id) {
        Notebook notebook = notebookRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notebook not found: " + id));
        return toResponse(notebook);
    }

    public NotebookResponse update(UUID id, NotebookRequest request) {
        Notebook notebook = notebookRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notebook not found: " + id));
        notebook.setName(request.name());
        notebook.setDescription(request.description());
        notebook = notebookRepo.save(notebook);
        return toResponse(notebook);
    }

    public void delete(UUID id) {
        if (!notebookRepo.existsById(id)) {
            throw new ResourceNotFoundException("Notebook not found: " + id);
        }
        // Cascade delete documents (and their chunks/tasks via DB constraints or app
        // logic)
        List<org.sirohi.smartnotebook.model.Document> docs = documentRepo.findAllByNotebookId(id);
        documentRepo.deleteAll(docs);

        notebookRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Notebook getEntityById(UUID id) {
        return notebookRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notebook not found: " + id));
    }

    private NotebookResponse toResponse(Notebook n) {
        long docCount = documentRepo.findIdsByNotebookId(n.getId()).size();
        long chatCount = chatRepo.findByNotebookId(n.getId(),
                org.springframework.data.domain.Sort.by("createdAt")).size();
        return new NotebookResponse(
                n.getId(),
                n.getName(),
                n.getDescription(),
                docCount,
                chatCount,
                n.getCreatedAt(),
                n.getUpdatedAt());
    }
}
