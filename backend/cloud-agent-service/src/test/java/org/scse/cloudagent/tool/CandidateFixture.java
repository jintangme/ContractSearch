package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

public final class CandidateFixture {
    public static final String SOURCE = """
            // SPDX-License-Identifier: MIT
            pragma solidity ^0.8.20;
            contract Counter {
              uint public count;
              address public owner;
              constructor(address initialOwner) { owner = initialOwner; }
              function increment() public { require(msg.sender == owner, "owner only"); count += 1; }
            }
            """;
    public static Path repository() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !path.resolve("contract-runtime-service/test/fixtures/candidate-counter.json").toFile().isFile()) path = path.getParent();
        if (path == null) throw new IllegalStateException("Repository fixture not found");
        return path;
    }
    public static JsonNode plan() throws Exception {
        return new ObjectMapper().readTree(repository().resolve("contract-runtime-service/test/fixtures/candidate-counter.json").toFile());
    }
    public static ObjectNode receipt(String source, JsonNode plan) {
        ObjectNode r = new ObjectMapper().createObjectNode();
        r.put("sourceHash", SourceHashes.sha256(source)).put("planHash", SourceHashes.planHash(plan)).put("allChecksPassed", true);
        r.putObject("compile").put("success", true);
        var rows = r.putArray("scenarios");
        for (JsonNode scenario : plan.path("scenarios")) {
            var row = rows.addObject().put("id", scenario.path("id").asText())
                    .put("requirementId", scenario.path("requirementId").asText()).put("passed", true);
            var steps = row.putArray("steps");
            for (int i = 0; i < scenario.path("steps").size(); i++) steps.addObject().put("index", i).put("passed", true);
        }
        return r;
    }
}
