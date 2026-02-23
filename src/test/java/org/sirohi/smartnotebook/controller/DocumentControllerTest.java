package org.sirohi.smartnotebook.controller;

import org.junit.jupiter.api.Test;
import org.sirohi.smartnotebook.model.Document;
import org.sirohi.smartnotebook.dto.UploadResponse;
import org.sirohi.smartnotebook.dto.UploadResult;
import org.sirohi.smartnotebook.service.DocumentService;
import org.sirohi.smartnotebook.service.IngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@ActiveProfiles("test")
public class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private IngestionService ingestionService;

    @Test
    void listDocuments_ReturnsPage() throws Exception {
        Document doc = new Document();
        UUID docId = UUID.randomUUID();
        doc.setId(docId);
        doc.setTitle("Title");

        Page<Document> page = new PageImpl<>(List.of(doc));
        when(documentService.listDocuments(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/documents")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(docId.toString()));

        verify(documentService, times(1)).listDocuments(0, 20);
    }

    @Test
    void uploadDocument_ReturnsAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes());

        UUID docId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UploadResult mockResult = new UploadResult(docId, taskId);

        when(documentService.uploadAndEnqueue(any(), eq("Test Doc"), any(), any(), any())).thenReturn(mockResult);

        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Test Doc"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.taskId").value(taskId.toString()));
    }
}
