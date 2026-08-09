package org.scse.cloudagent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agent.enabled=false",
        "agent.migrations.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class AgentFeatureDisabledTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void agentApiDoesNotExistWhenFeatureIsOff() throws Exception {
        mockMvc.perform(get("/api/agent/v1/runs/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
