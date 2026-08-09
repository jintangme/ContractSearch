package org.scse.smartcontractsearchengine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SolidityAbiCompilerService {

    private static final Duration COMPILE_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("\\bimport\\s+[\"']");
    private static final Pattern CONTRACT_PATTERN = Pattern.compile(
            "\\b(abstract\\s+contract|contract|interface|library)\\s+([A-Za-z_][A-Za-z0-9_]*)"
    );

    private final ObjectMapper objectMapper;

    public SolidityAbiCompilerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AbiCompileResult compile(String sourceCode, String preferredContractName) {
        if (isBlank(sourceCode)) {
            return AbiCompileResult.failed("源码为空，无法生成 ABI。", "");
        }

        if (IMPORT_PATTERN.matcher(sourceCode).find()) {
            return AbiCompileResult.failed(
                    "源码包含外部 import，当前上传链路仅支持单文件源码自动生成 ABI。请合并依赖后重新上传。",
                    ""
            );
        }

        CompilerCommand compiler = resolveCompiler();
        if (!compiler.available()) {
            return AbiCompileResult.failed(compiler.message(), "");
        }

        try {
            String standardJson = buildStandardJson(sourceCode);
            Process process = new ProcessBuilder(compiler.compileCommand()).start();
            process.getOutputStream().write(standardJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

            boolean finished = process.waitFor(COMPILE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return AbiCompileResult.failed("Solidity 编译超时，未能在限定时间内生成 ABI。", compiler.version());
            }

            String stdout = stdoutFuture.get(3, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(3, TimeUnit.SECONDS);
            return parseCompileOutput(stdout, stderr, preferredContractName, sourceCode, compiler.version());
        } catch (Exception e) {
            log.warn("自动生成 ABI 失败", e);
            return AbiCompileResult.failed("Solidity 编译过程异常：" + cleanMessage(e.getMessage()), compiler.version());
        }
    }

    private String buildStandardJson(String sourceCode) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("language", "Solidity");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("content", sourceCode);
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("Uploaded.sol", source);
        root.put("sources", sources);

        Map<String, Object> fileSelection = new LinkedHashMap<>();
        fileSelection.put("*", List.of("abi"));
        Map<String, Object> outputSelection = new LinkedHashMap<>();
        outputSelection.put("*", fileSelection);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("outputSelection", outputSelection);
        root.put("settings", settings);

        return objectMapper.writeValueAsString(root);
    }

    private AbiCompileResult parseCompileOutput(String stdout,
                                                String stderr,
                                                String preferredContractName,
                                                String sourceCode,
                                                String compilerVersion) throws IOException {
        String jsonOutput = extractJsonOutput(stdout);
        if (isBlank(jsonOutput)) {
            return AbiCompileResult.failed("编译器未返回标准 JSON 输出。" + appendOutput(stderr), compilerVersion);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonOutput);
        } catch (Exception e) {
            return AbiCompileResult.failed("编译器输出无法解析为 JSON。" + appendOutput(firstNonBlank(stderr, stdout)), compilerVersion);
        }

        String compileErrors = collectCompileErrors(root.path("errors"));
        if (!isBlank(compileErrors)) {
            return AbiCompileResult.failed(compileErrors, compilerVersion);
        }

        JsonNode compiledContracts = root.path("contracts").path("Uploaded.sol");
        if (!compiledContracts.isObject()) {
            return AbiCompileResult.failed("编译完成但未找到 Uploaded.sol 中的合约 ABI。", compilerVersion);
        }

        String selectedName = selectContractName(compiledContracts, preferredContractName, sourceCode);
        if (isBlank(selectedName)) {
            return AbiCompileResult.failed("编译完成但未找到可导出的合约 ABI。", compilerVersion);
        }

        JsonNode abiNode = compiledContracts.path(selectedName).path("abi");
        if (!abiNode.isArray()) {
            return AbiCompileResult.failed("编译完成但目标合约未包含 ABI 输出。", compilerVersion);
        }

        String abi = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(abiNode);
        return AbiCompileResult.generated(abi, selectedName, compilerVersion);
    }

    private String collectCompileErrors(JsonNode errorsNode) {
        if (!errorsNode.isArray()) return "";

        List<String> errors = new ArrayList<>();
        for (JsonNode error : errorsNode) {
            String severity = error.path("severity").asText("");
            if (!"error".equalsIgnoreCase(severity)) continue;
            String message = firstNonBlank(
                    error.path("formattedMessage").asText(""),
                    error.path("message").asText("")
            );
            if (!isBlank(message)) {
                errors.add(cleanMessage(message));
            }
        }
        return String.join("；", errors);
    }

    private String selectContractName(JsonNode compiledContracts, String preferredContractName, String sourceCode) {
        if (!isBlank(preferredContractName) && compiledContracts.has(preferredContractName)) {
            return preferredContractName.trim();
        }

        for (String name : detectConcreteContractNames(sourceCode)) {
            if (compiledContracts.has(name)) {
                return name;
            }
        }

        Iterator<String> names = compiledContracts.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (compiledContracts.path(name).path("abi").isArray()) {
                return name;
            }
        }
        return "";
    }

    private List<String> detectConcreteContractNames(String sourceCode) {
        List<String> names = new ArrayList<>();
        Matcher matcher = CONTRACT_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            String kind = matcher.group(1);
            String name = matcher.group(2);
            if ("contract".equals(kind) && !isBlank(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private CompilerCommand resolveCompiler() {
        String configuredSolc = System.getenv("SOLC_PATH");
        if (!isBlank(configuredSolc)) {
            CompilerVersionResult version = readCompilerVersion(
                    List.of(configuredSolc.trim(), "--version"),
                    "SOLC_PATH 指定的 Solidity 编译器不可用"
            );
            if (version.available()) {
                return CompilerCommand.available(List.of(configuredSolc.trim(), "--standard-json"), version.version());
            }
            return CompilerCommand.unavailable(version.message());
        }

        CompilerVersionResult nativeSolc = readCompilerVersion(
                List.of("solc", "--version"),
                "系统 Solidity 编译器 solc 不可用"
        );
        if (nativeSolc.available()) {
            return CompilerCommand.available(List.of("solc", "--standard-json"), nativeSolc.version());
        }

        Path solcJsPath = findSolcJsPath();
        if (solcJsPath != null) {
            String nodeBinary = nodeBinary();
            CompilerVersionResult solcJsVersion = readCompilerVersion(
                    List.of(nodeBinary, solcJsPath.toString(), "--version"),
                    "项目内 solc-js 不可用"
            );
            if (solcJsVersion.available()) {
                return CompilerCommand.available(
                        List.of(nodeBinary, solcJsPath.toString(), "--standard-json"),
                        solcJsVersion.version() + " (solc-js)"
                );
            }
            return CompilerCommand.unavailable(solcJsVersion.message());
        }

        return CompilerCommand.unavailable(
                "未检测到可用 Solidity 编译器。已尝试系统 solc 和项目内 solc-js；请先在 scse_front_end 执行 npm install，或通过 SOLC_PATH 指定编译器路径。"
        );
    }

    private CompilerVersionResult readCompilerVersion(List<String> versionCommand, String unavailablePrefix) {
        try {
            Process process = new ProcessBuilder(versionCommand).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CompilerVersionResult.unavailable(unavailablePrefix + "：响应超时。");
            }
            String output = readStream(process.getInputStream()) + "\n" + readStream(process.getErrorStream());
            if (process.exitValue() != 0) {
                return CompilerVersionResult.unavailable(unavailablePrefix + "：" + cleanMessage(output));
            }
            return CompilerVersionResult.available(extractVersion(output));
        } catch (IOException e) {
            return CompilerVersionResult.unavailable(unavailablePrefix + "：" + cleanMessage(e.getMessage()));
        } catch (Exception e) {
            return CompilerVersionResult.unavailable(unavailablePrefix + "：" + cleanMessage(e.getMessage()));
        }
    }

    private String extractVersion(String output) {
        Matcher matcher = Pattern.compile("Version:\\s*([^\\s]+)").matcher(output == null ? "" : output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return cleanMessage(output).split("\\R")[0];
    }

    private Path findSolcJsPath() {
        String configured = System.getenv("SOLC_JS_PATH");
        if (!isBlank(configured)) {
            Path configuredPath = Paths.get(configured.trim()).toAbsolutePath().normalize();
            if (Files.isRegularFile(configuredPath)) {
                return configuredPath;
            }
        }

        List<Path> starts = List.of(
                Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize(),
                Paths.get("").toAbsolutePath().normalize()
        );

        for (Path start : starts) {
            Path current = start;
            for (int i = 0; i < 8 && current != null; i += 1) {
                List<Path> candidates = List.of(
                        current.resolve("scse_front_end/node_modules/solc/solc.js"),
                        current.resolve("代码/scse_front_end/node_modules/solc/solc.js"),
                        current.resolve("../scse_front_end/node_modules/solc/solc.js"),
                        current.resolve("../../scse_front_end/node_modules/solc/solc.js")
                );
                for (Path candidate : candidates) {
                    Path normalized = candidate.toAbsolutePath().normalize();
                    if (Files.isRegularFile(normalized)) {
                        return normalized;
                    }
                }
                current = current.getParent();
            }
        }
        return null;
    }

    private String nodeBinary() {
        String configured = System.getenv("NODE_BINARY");
        if (!isBlank(configured)) return configured.trim();
        for (String candidate : List.of("/opt/homebrew/bin/node", "/usr/local/bin/node", "/usr/bin/node")) {
            Path path = Paths.get(candidate);
            if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                return candidate;
            }
        }
        return "node";
    }

    private String readStream(java.io.InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String appendOutput(String output) {
        if (isBlank(output)) return "";
        return " 输出：" + cleanMessage(output);
    }

    private String extractJsonOutput(String output) {
        if (isBlank(output)) return "";
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return output.substring(start, end + 1);
        }
        return output.trim();
    }

    private String cleanMessage(String message) {
        if (message == null) return "";
        String cleaned = message.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > 500 ? cleaned.substring(0, 500) + "..." : cleaned;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record CompilerVersionResult(boolean available, String version, String message) {
        static CompilerVersionResult available(String version) {
            return new CompilerVersionResult(true, version, "");
        }

        static CompilerVersionResult unavailable(String message) {
            return new CompilerVersionResult(false, "", message);
        }
    }

    private record CompilerCommand(boolean available, List<String> compileCommand, String version, String message) {
        static CompilerCommand available(List<String> compileCommand, String version) {
            return new CompilerCommand(true, compileCommand, version, "");
        }

        static CompilerCommand unavailable(String message) {
            return new CompilerCommand(false, List.of(), "", message);
        }
    }

    @Data
    public static class AbiCompileResult {
        private String status;
        private String abi;
        private String errorMessage;
        private String compilerVersion;
        private String contractName;

        static AbiCompileResult generated(String abi, String contractName, String compilerVersion) {
            AbiCompileResult result = new AbiCompileResult();
            result.status = "generated";
            result.abi = abi;
            result.contractName = contractName;
            result.compilerVersion = compilerVersion;
            result.errorMessage = "";
            return result;
        }

        static AbiCompileResult failed(String errorMessage, String compilerVersion) {
            AbiCompileResult result = new AbiCompileResult();
            result.status = "failed";
            result.abi = "";
            result.compilerVersion = compilerVersion;
            result.errorMessage = errorMessage;
            result.contractName = "";
            return result;
        }
    }
}
