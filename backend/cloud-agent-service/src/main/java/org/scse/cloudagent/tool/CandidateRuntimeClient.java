package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentErrorCode;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class CandidateRuntimeClient {
    private final ObjectMapper mapper;
    private final AgentProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public CandidateRuntimeClient(ObjectMapper mapper, AgentProperties properties) { this.mapper = mapper; this.properties = properties; }
    public JsonNode validatePlan(JsonNode plan) { return post("validation-plan", Map.of("plan", plan)); }
    public JsonNode validatePlan(JsonNode plan, String source, boolean completeSource) {
        return post("validation-plan", Map.of("plan", plan, "sourceCode", source, "completeSource", completeSource));
    }
    public JsonNode validateCandidate(String source, JsonNode plan) {
        return post("validate-candidate", Map.of("sourceCode", source, "plan", plan, "planHash", SourceHashes.planHash(plan)));
    }
    private JsonNode post(String endpoint, Object payload) {
        // These endpoints run isolated, disposable checks; retrying a lost
        // transport response cannot publish or mutate a user's chain state.
        // Do not retry declared input errors or failed behavioral checks.
        for (int attempt = 0; attempt < 2; attempt++) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.dependencies().runtimeBaseUrl() + "/api/runtime/" + endpoint))
                    .timeout(Duration.ofMillis(properties.dependencies().compileTimeoutMs()))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpClient transport = attempt == 0 ? client
                    : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = transport.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.body().length() > 500_000) throw new IllegalStateException("Candidate response too large");
            JsonNode result = mapper.readTree(response.body());
            if (response.statusCode() == 400) throw new InvalidToolArgumentsException("Invalid validation plan: " + result.path("message").asText("invalid input"));
            if (response.statusCode() != 200 || !result.isObject()) throw new IllegalStateException("Candidate runtime unavailable: HTTP " + response.statusCode());
            if (attempt > 0) ((com.fasterxml.jackson.databind.node.ObjectNode) result).put("transportRetries", attempt);
            return result;
        } catch (InvalidToolArgumentsException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "Candidate runtime interrupted", e);
        } catch (java.net.http.HttpTimeoutException e) { throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "Candidate runtime timed out", e); }
        catch (java.io.IOException e) {
            if (attempt == 0 && !(e instanceof com.fasterxml.jackson.core.JsonProcessingException)) continue;
            throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Candidate runtime response failed: " + e.getClass().getSimpleName(), e);
        }
        catch (Exception e) { throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Candidate runtime request failed: " + e.getClass().getSimpleName(), e); }
        }
        throw new IllegalStateException("Unreachable bounded transport retry");
    }
}
