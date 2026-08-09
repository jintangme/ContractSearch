package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.scse.cloudagent.domain.SideEffect;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Phase1AToolsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolExecutionContext context = new ToolExecutionContext(7, UUID.randomUUID());

    @Test
    void searchClampsContractAndEvidenceShape() throws Exception {
        ContractCatalogClient client = mock(ContractCatalogClient.class);
        when(client.search("ERC20 mint", "semantic", 3, 7)).thenReturn(List.of(Map.of(
                "id", 42L, "contractName", "PointsToken", "description", "Mintable ERC20"
        )));
        SearchContractsTool tool = new SearchContractsTool(client);
        ToolExecutionResult result = tool.execute(objectMapper.readTree(
                "{\"query\":\"ERC20 mint\",\"mode\":\"semantic\",\"limit\":3}"), context);

        assertThat(tool.definition().sideEffect()).isEqualTo(SideEffect.READ);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).sourceRef()).isEqualTo("contract:42");
        assertThat(result.evidence().get(0).metadata()).containsEntry("contentBoundary", "UNTRUSTED");
        verify(client).search("ERC20 mint", "semantic", 3, 7);
    }

    @Test
    void rejectsUnknownOrCoercedArguments() throws Exception {
        GetContractTool getTool = new GetContractTool(mock(ContractCatalogClient.class));
        assertThatThrownBy(() -> getTool.execute(objectMapper.readTree("{\"contractId\":\"42\"}"), context))
                .isInstanceOf(InvalidToolArgumentsException.class);
        assertThatThrownBy(() -> getTool.execute(objectMapper.readTree("{\"contractId\":42,\"url\":\"http://evil\"}"), context))
                .isInstanceOf(InvalidToolArgumentsException.class)
                .hasMessageContaining("Unknown tool arguments");
    }

    @Test
    void compileFetchesCatalogSourceAndReturnsDiagnosticsWithoutBytecode() throws Exception {
        ContractCatalogClient catalog = mock(ContractCatalogClient.class);
        CompileRuntimeClient runtime = mock(CompileRuntimeClient.class);
        when(catalog.getContract(42, 7)).thenReturn(Map.of(
                "id", 42L, "contractName", "PointsToken", "sourceCode", "pragma solidity ^0.8.20; contract PointsToken {}"
        ));
        when(runtime.compile("pragma solidity ^0.8.20; contract PointsToken {}", "PointsToken.sol", "auto"))
                .thenReturn(Map.of("success", true, "selectedCompilerVersion", "0.8.35",
                        "contracts", List.of("PointsToken"), "errors", List.of(), "warnings", List.of()));
        CompileContractTool tool = new CompileContractTool(catalog, runtime);
        ToolExecutionResult result = tool.execute(objectMapper.readTree("{\"contractId\":42}"), context);

        assertThat(tool.definition().sideEffect()).isEqualTo(SideEffect.EXECUTE_SANDBOXED);
        assertThat(result.output()).containsEntry("success", true).doesNotContainKey("bytecode");
        assertThat(result.evidence().get(0).excerpt()).contains("Compilation succeeded", "0.8.35");
    }
}
