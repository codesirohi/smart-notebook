package org.sirohi.smartnotebook.controller;

import org.junit.jupiter.api.Test;
import org.sirohi.smartnotebook.model.Notebook;
import org.sirohi.smartnotebook.dto.NotebookRequest;
import org.sirohi.smartnotebook.dto.NotebookResponse;
import java.util.UUID;
import org.sirohi.smartnotebook.service.NotebookService;
import org.sirohi.smartnotebook.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotebookController.class)
@ActiveProfiles("test")
class NotebookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotebookService notebookService;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void createNotebook_ReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        NotebookResponse mockResponse = new NotebookResponse(id, "Test Notebook", "Test desc", 0L, 0L, null, null);

        when(notebookService.create(any(NotebookRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/notebooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test Notebook\", \"description\": \"Test desc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Test Notebook"));

        verify(notebookService, times(1)).create(any(NotebookRequest.class));
    }

    @Test
    void getAllNotebooks_ReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        NotebookResponse nb1 = new NotebookResponse(id, "Notebook 1", "Desc", 0L, 0L, null, null);

        when(notebookService.listAll()).thenReturn(List.of(nb1));

        mockMvc.perform(get("/api/notebooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void getNotebook_WhenExists_ReturnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        NotebookResponse nb = new NotebookResponse(id, "Notebook 1", "desc", 0L, 0L, null, null);

        when(notebookService.getById(id)).thenReturn(nb);

        mockMvc.perform(get("/api/notebooks/" + id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteNotebook_ReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(notebookService).delete(id);

        mockMvc.perform(delete("/api/notebooks/" + id.toString()))
                .andExpect(status().isNoContent());

        verify(notebookService, times(1)).delete(id);
    }
}
