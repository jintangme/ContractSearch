package org.scse.cloudagent.tool;

import org.scse.cloudagent.domain.AgentErrorCode;
import org.scse.cloudagent.domain.SideEffect;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MiniLoopToolPolicy {
    public static final Set<String> ALLOWED_TOOLS = Set.of(
            "search_contracts.v1", "get_contract.v1", "compile_contract.v1"
    );

    public void authorize(AgentTool tool) {
        if (!ALLOWED_TOOLS.contains(tool.definition().name())) {
            throw new AgentToolException(AgentErrorCode.TOOL_POLICY_DENIED,
                    "Tool is outside the Phase 1A allowlist: " + tool.definition().name());
        }
        SideEffect sideEffect = tool.definition().sideEffect();
        if (sideEffect != SideEffect.READ && sideEffect != SideEffect.EXECUTE_SANDBOXED) {
            throw new AgentToolException(AgentErrorCode.TOOL_POLICY_DENIED,
                    "Tool side effect is not allowed in Phase 1A: " + sideEffect);
        }
    }
}
