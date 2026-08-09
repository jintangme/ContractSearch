package org.scse.cloudagent.controller;

import org.junit.jupiter.api.Test;
import org.scse.cloudagent.security.AgentInternalAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agent.enabled=true",
        "agent.internal-token=phase-one-test-token-1234567890",
        "agent.migrations.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class AgentRunControllerTest {
    private static final String TOKEN = "phase-one-test-token-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsDirectRequestWithoutTrustedGatewayToken() throws Exception {
        mockMvc.perform(post("/api/agent/v1/runs")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completesMockRunAndExposesEvidenceAndProposal() throws Exception {
        String body = mockMvc.perform(post("/api/agent/v1/runs")
                        .header(AgentInternalAuthFilter.INTERNAL_TOKEN_HEADER, TOKEN)
                        .header("X-User-Id", "7")
                        .header("X-Trace-Id", "phase1-test-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"find a safe ERC20\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "phase1-test-trace"))
                .andExpect(jsonPath("$.state").value("COMPLETE"))
                .andExpect(jsonPath("$.model").value("glm-4.5-air"))
                .andExpect(jsonPath("$.traceId").value("phase1-test-trace"))
                .andExpect(jsonPath("$.events.length()").value(5))
                .andExpect(jsonPath("$.evidence.length()").value(1))
                .andExpect(jsonPath("$.proposals[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        String runId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();
        mockMvc.perform(get("/api/agent/v1/runs/{runId}", runId)
                        .header(AgentInternalAuthFilter.INTERNAL_TOKEN_HEADER, TOKEN)
                        .header("X-User-Id", "8"))
                .andExpect(status().isNotFound());
    }
}
