package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.scse.cloudagent.config.AgentProperties;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_AGENT_TOOL_INTEGRATION", matches = "true")
class AgentToolsLiveIntegrationTest {
    @Test
    void searchesReadsAndCompilesThroughTheRealLocalServices() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentProperties properties = new AgentProperties(false, "", "glm-4.5-air",
                new AgentProperties.Provider("https://open.bigmodel.cn/api/paas/v4", "", 1000, false),
                new AgentProperties.Migrations(false, "", "", ""),
                new AgentProperties.MiniLoop(false, 6, 4, 2),
                new AgentProperties.Dependencies(
                        System.getenv().getOrDefault("AGENT_TOOL_TEST_CONTRACT_URL", "http://127.0.0.1:18081"),
                        System.getenv().getOrDefault("AGENT_TOOL_TEST_RUNTIME_URL", "http://127.0.0.1:19090"),
                        15000, 60000));
        ContractCatalogClient catalog = new ContractCatalogClient(objectMapper, properties);
        CompileRuntimeClient runtime = new CompileRuntimeClient(objectMapper, properties);
        ToolExecutionContext context = new ToolExecutionContext(1, UUID.randomUUID());

        ToolExecutionResult search = new SearchContractsTool(catalog).execute(
                objectMapper.readTree("{\"query\":\"pragma solidity contract\",\"mode\":\"code\",\"limit\":5}"), context);
        assertThat(search.output().get("count")).isNotEqualTo(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) search.output().get("results");
        long contractId = ((Number) results.get(0).get("id")).longValue();

        JsonNode idArgument = objectMapper.readTree("{\"contractId\":" + contractId + "}");
        ToolExecutionResult detail = new GetContractTool(catalog).execute(idArgument, context);
        assertThat(detail.output()).containsKeys("id", "sourceCode");

        ToolExecutionResult compile = new CompileContractTool(catalog, runtime).execute(idArgument, context);
        assertThat(compile.output()).containsKeys("success", "errors", "warnings", "contractId");
        assertThat(compile.output()).doesNotContainKeys("bytecode", "abi");
        assertThat(compile.evidence()).hasSize(1);
    }
}
