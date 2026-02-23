package org.sirohi.smartnotebook.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.sirohi.smartnotebook.service.LocalModelService;
import org.sirohi.smartnotebook.service.HardwareDetectionService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(LocalModelController.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.features.local-models-enabled=false")
class LocalModelControllerFeatureFlagTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalModelService localModelService;

    @MockitoBean
    private HardwareDetectionService hardwareDetectionService;

    @Test
    void whenCloudModeEnabled_listingModelsReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/models/local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/models/local/installed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void whenCloudModeEnabled_gettingHardwareInfoReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/models/local/hardware"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Local models are disabled in this environment (Cloud Mode)."));
    }
}
