package org.sirohi.smartnotebook.controller;

import org.junit.jupiter.api.Test;
import org.sirohi.smartnotebook.dto.Citation;
import org.sirohi.smartnotebook.dto.QueryRequest;
import org.sirohi.smartnotebook.dto.QueryResponse;
import org.sirohi.smartnotebook.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
@ActiveProfiles("test")
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueryService queryService;

    @Test
    void executeQuery_ReturnsValidResponse() throws Exception {
        QueryResponse mockResponse = new QueryResponse(
                "42 is the answer.",
                List.of(new Citation("A book", 1, "Content here", 0.95)),
                0.99,
                150L);

        when(queryService.query(anyString(), anyInt(), anyList())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"question\": \"What is the answer?\", \"topK\": 5, \"documentIds\": [\"00000000-0000-0000-0000-000000000001\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("42 is the answer."))
                .andExpect(jsonPath("$.latencyMs").value(150))
                .andExpect(jsonPath("$.confidence").value(0.99));

        verify(queryService, times(1)).query(anyString(), eq(5), anyList());
    }
}
