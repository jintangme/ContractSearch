package org.scse.smartcontractsearchengine.service.impl;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import jakarta.annotation.PostConstruct;
import org.scse.smartcontractsearchengine.common.enums.OpenAiClientType;
import org.scse.smartcontractsearchengine.common.ai.AiModelRegistry;
import org.scse.smartcontractsearchengine.config.OpenAIClientFactory;
import org.scse.smartcontractsearchengine.context.UserContext;
import org.scse.smartcontractsearchengine.model.vo.ContractCheckResponse;
import org.scse.smartcontractsearchengine.service.ContractCheckService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ContractCheckServiceImpl implements ContractCheckService {

    private final OpenAIClientFactory openAIClientFactory;
    private final JdbcTemplate jdbcTemplate;

    public ContractCheckServiceImpl(OpenAIClientFactory openAIClientFactory,
                                    JdbcTemplate jdbcTemplate) {
        this.openAIClientFactory = openAIClientFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initRecordTable() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS contract_check_record (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT,
                        input_mode VARCHAR(24),
                        file_name VARCHAR(255),
                        contract_name VARCHAR(255),
                        source_code TEXT,
                        status VARCHAR(24) NOT NULL DEFAULT 'success',
                        summary TEXT,
                        conclusion TEXT,
                        risk_level VARCHAR(24),
                        key_issues TEXT,
                        suggestions TEXT,
                        raw_ai_result TEXT,
                        contract_type VARCHAR(64),
                        complexity_level VARCHAR(24),
                        key_focus TEXT,
                        annotation_result TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        } catch (Exception e) {
            log.warn("初始化智能预审记录表失败，预审功能仍可继续使用", e);
        }
    }

    @Override
    public ContractCheckResponse checkContract(String contractName, String sourceCode, String inputMode, String fileName, String model) {
        String modelUsed = AiModelRegistry.resolveModel(model);
        List<String> issues = new ArrayList<>();

        if (sourceCode == null || sourceCode.isBlank()) {
            issues.add("源码不能为空");
            ContractCheckResponse response = ContractCheckResponse.builder()
                    .validSolidity(false)
                    .structurallyComplete(false)
                    .riskLevel("high")
                    .recommendation("no")
                    .summary("未检测到有效源码，无法完成智能预审。")
                    .issues(issues)
                    .codeLength(0)
                    .aiSummary("未提供源码，无法进行 AI 预审。")
                    .conclusion("当前无法完成预审，请先提供有效 Solidity 源码。")
                    .contractType("unknown")
                    .complexityLevel("unknown")
                    .aiSuggestions(List.of("请提供完整 Solidity 合约源码后再进行检查"))
                    .keyFocus(List.of())
                    .annotations(List.of())
                    .riskScores(new ContractCheckResponse.RiskScores(90, 80, 60))
                    .provider(AiModelRegistry.PROVIDER)
                    .requestedModel(model)
                    .modelUsed(modelUsed)
                    .saved(false)
                    .build();
            saveRecordSafely(response, contractName, sourceCode, inputMode, fileName, "failed");
            return response;
        }

        String code = sourceCode.trim();
        String lowerCode = code.toLowerCase();
        int codeLength = code.length();

        boolean hasPragma = lowerCode.contains("pragma solidity");
        boolean hasContractKeyword = lowerCode.contains("contract ");
        boolean hasFunction = lowerCode.contains("function ");
        boolean hasBrace = code.contains("{") && code.contains("}");
        boolean hasOwner = lowerCode.contains("owner") || code.contains("Ownable");
        boolean hasPause = lowerCode.contains("pause(") || code.contains("Pausable");
        boolean hasTransfer = lowerCode.contains("transfer(") || lowerCode.contains("transferfrom(");
        boolean hasDelegateCall = lowerCode.contains("delegatecall");
        boolean hasTxOrigin = lowerCode.contains("tx.origin");
        boolean hasSelfdestruct = lowerCode.contains("selfdestruct") || lowerCode.contains("suicide");
        boolean hasValueCall = lowerCode.contains(".call{value") || lowerCode.contains("call{value");
        boolean hasExternalCall = lowerCode.contains(".call(") || lowerCode.contains("call{") || hasDelegateCall;
        boolean hasMint = lowerCode.contains("mint(") || lowerCode.contains("_mint(");
        boolean hasWithdraw = lowerCode.contains("withdraw") || lowerCode.contains("claim");
        boolean hasUnchecked = lowerCode.contains("unchecked");
        boolean hasReentrancyRisk = hasValueCall && hasWithdraw;

        boolean validSolidity = hasPragma && hasContractKeyword;
        boolean structurallyComplete = validSolidity && hasBrace && hasFunction;

        if (!hasPragma) issues.add("未检测到 pragma solidity 声明");
        if (!hasContractKeyword) issues.add("未检测到 contract 关键字，源码可能不是标准 Solidity 合约");
        if (!hasFunction) issues.add("未检测到 function 定义，源码结构可能不完整");
        if (!hasBrace) issues.add("源码缺少基本花括号结构，可能不完整");
        if (codeLength < 120) issues.add("源码长度过短，可能不是完整合约");
        if (hasTxOrigin) issues.add("检测到 tx.origin，用于权限判断时可能存在安全风险");
        if (hasDelegateCall) issues.add("检测到 delegatecall，需重点关注调用安全性");
        if (hasSelfdestruct) issues.add("检测到 selfdestruct/suicide，需重点关注合约可销毁风险");
        if (hasExternalCall && hasTransfer) issues.add("源码同时存在外部调用与资产流转逻辑，建议重点检查调用顺序与重入风险");
        if (hasReentrancyRisk) issues.add("检测到 withdraw 中使用低级 value call，且可能在外部调用后更新余额，存在典型重入风险");
        if (hasUnchecked) issues.add("检测到 unchecked 代码块，建议确认算术边界与溢出风险是否可控");

        String riskLevel = "low";
        if (hasDelegateCall || hasTxOrigin || hasSelfdestruct || hasReentrancyRisk) {
            riskLevel = "high";
        } else if (hasOwner || hasPause || hasTransfer) {
            riskLevel = "medium";
        }

        String recommendation;
        if (!validSolidity || !structurallyComplete) {
            recommendation = "no";
        } else if (hasReentrancyRisk || hasSelfdestruct) {
            recommendation = "no";
        } else if ("high".equals(riskLevel)) {
            recommendation = "review";
        } else {
            recommendation = "yes";
        }

        String summary = buildRuleSummary(contractName, validSolidity, structurallyComplete, riskLevel, hasOwner, hasPause, hasTransfer);
        String complexityLevel = inferComplexity(codeLength, hasOwner, hasPause, hasTransfer, hasExternalCall, hasMint, hasWithdraw);
        List<String> keyFocus = buildKeyFocus(code, hasOwner, hasPause, hasTransfer, hasDelegateCall, hasTxOrigin, hasSelfdestruct, hasMint, hasWithdraw);
        List<ContractCheckResponse.AnnotationSuggestion> annotations = buildAnnotations(hasOwner, hasTransfer, hasDelegateCall, hasTxOrigin, hasSelfdestruct, hasExternalCall, hasUnchecked, hasReentrancyRisk);
        ContractCheckResponse.RiskScores riskScores = buildRiskScores(hasOwner, hasPause, hasTransfer, hasExternalCall, hasDelegateCall, hasTxOrigin, hasSelfdestruct, hasMint, hasWithdraw, hasReentrancyRisk, codeLength);

        ContractCheckResponse base = ContractCheckResponse.builder()
                .validSolidity(validSolidity)
                .structurallyComplete(structurallyComplete)
                .riskLevel(riskLevel)
                .recommendation(recommendation)
                .summary(summary)
                .issues(issues)
                .codeLength(codeLength)
                .aiSummary("AI 预审暂未执行。")
                .conclusion(buildConclusion(recommendation))
                .contractType("unknown")
                .complexityLevel(complexityLevel)
                .aiSuggestions(new ArrayList<>())
                .keyFocus(keyFocus)
                .annotations(annotations)
                .riskScores(riskScores)
                .provider(AiModelRegistry.PROVIDER)
                .requestedModel(model)
                .modelUsed(modelUsed)
                .saved(false)
                .build();

        // 基础结构不通过时，直接返回，不浪费 AI 调用
        if (!validSolidity) {
            base.setAiSummary("该源码未通过基础 Solidity 特征检查，不建议继续进入 AI 深度预审。");
            base.setAiSuggestions(List.of("请补充完整的 Solidity 源码", "确认包含 pragma 与 contract 定义"));
            saveRecordSafely(base, contractName, code, inputMode, fileName, "success");
            return base;
        }

        enrichWithAi(base, contractName, code, model, modelUsed);
        saveRecordSafely(base, contractName, code, inputMode, fileName, "success");
        return base;
    }

    private void enrichWithAi(ContractCheckResponse response, String contractName, String sourceCode, String requestedModel, String modelUsed) {
        OpenAIClient client = openAIClientFactory.getClient(OpenAiClientType.ZHIPU);
        if (client == null) {
            log.warn("AI client is null, ZHIPU client not initialized");
            response.setAiSummary("AI 服务未初始化，当前仅返回规则检查结果。");
            response.setAiSuggestions(List.of("请检查大模型 API Key 配置"));
            return;
        }

        String code = sourceCode.length() > 9000 ? sourceCode.substring(0, 9000) : sourceCode;
        String prompt = buildAiPrompt(contractName, code);

        try {
            log.info("[AI] scene=precheck provider={} requestedModel={} modelUsed={}", AiModelRegistry.PROVIDER, requestedModel, modelUsed);

            String aiText = CompletableFuture.supplyAsync(() -> callAi(client, prompt, modelUsed))
                    .orTimeout(45, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.error("AI 预审超时或失败", ex);
                        return "AI 预审超时或失败";
                    })
                    .join();

            log.info("AI 原始返回内容：{}", aiText);

            parseAiResult(response, aiText);
        } catch (Exception e) {
            log.error("AI 预审执行失败", e);
            response.setAiSummary("AI 预审执行失败，当前返回规则检查结果。");
            response.setAiSuggestions(List.of("请稍后重试 AI 检查"));
        }
    }

    private String callAi(OpenAIClient client, String prompt, String modelUsed) {
        try {
            log.info("正在调用智谱模型：{}", modelUsed);

            var params = ChatCompletionCreateParams.builder()
                    .model(modelUsed)
                    .addSystemMessage("""
                            你是一个智能合约预审助手。
                            你的任务是对上传的 Solidity 合约做简短预审，判断其大致类型、是否适合平台展示，并给出简短建议。
                    
                            请尽量按以下格式输出：
                            合约类型：xxx
                            AI结论：xxx
                            建议1：xxx
                            建议2：xxx
                    
                            要求：
                            1. 合约类型只可从 token、governance、audit、nft、defi、utility、other 中选择。
                            2. AI结论控制在 2 句以内，简要说明用途与是否建议人工复核。
                            3. 建议控制在 2 条以内，每条尽量简短。
                            4. 不要输出代码块，不要展开长篇分析。
                            5. 不确定时请保守表达。
                            """)
                    .addUserMessage(prompt)
                    .temperature(0.3)
                    .build();

            var completion = client.chat().completions().create(params);

            String content = completion.choices().get(0).message().content().orElse("AI 预审失败");
            log.info("智谱模型调用完成");
            return content;
        } catch (Exception e) {
            log.error("调用智谱模型失败", e);
            return "AI 预审失败";
        }
    }

    private String buildAiPrompt(String contractName, String sourceCode) {
        String code = sourceCode.length() > 3000 ? sourceCode.substring(0, 3000) : sourceCode;

        return """
            请对以下 Solidity 合约做简短预审，只需判断其大致用途、是否适合平台展示，并给出简短建议。

            合约名称：%s

            源码片段：
            %s
            """.formatted(
                contractName == null || contractName.isBlank() ? "未提供" : contractName,
                code
        );
    }

    private void parseAiResult(ContractCheckResponse response, String aiText) {
        if (aiText == null || aiText.isBlank() || aiText.startsWith("AI 预审")) {
            response.setAiSummary("AI 预审未返回有效内容，当前仅展示规则检查结果。");
            response.setAiSuggestions(List.of("请稍后重试"));
            return;
        }

        String contractType = "other";
        String aiSummary = "";
        List<String> suggestions = new ArrayList<>();

        String[] lines = aiText.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) continue;

            if (line.startsWith("合约类型：")) {
                contractType = line.replace("合约类型：", "").trim();
            } else if (line.startsWith("AI结论：")) {
                aiSummary = line.replace("AI结论：", "").trim();
            } else if (line.startsWith("建议")) {
                int idx = line.indexOf("：");
                if (idx > -1 && idx < line.length() - 1) {
                    suggestions.add(line.substring(idx + 1).trim());
                }
            }
        }

        // 如果没有按格式返回，就把整段 AI 文本直接当作结论
        if (aiSummary.isBlank()) {
            aiSummary = aiText.trim();
        }

        if (suggestions.isEmpty()) {
            suggestions = Arrays.asList("建议结合人工进一步复核", "重点关注权限与关键调用逻辑");
        }

        response.setContractType(contractType.isBlank() ? "other" : contractType);
        response.setAiSummary(aiSummary);
        response.setAiSuggestions(suggestions);
    }

    private String buildConclusion(String recommendation) {
        if ("yes".equals(recommendation)) {
            return "该源码通过基础预审，可继续整理为平台候选内容。";
        }
        if ("review".equals(recommendation)) {
            return "该源码具备一定参考价值，但建议先进入人工复核，重点确认权限与关键调用逻辑。";
        }
        return "该源码当前不建议直接收录，建议先补全结构或修复明显风险点。";
    }

    private String inferComplexity(int codeLength,
                                   boolean hasOwner,
                                   boolean hasPause,
                                   boolean hasTransfer,
                                   boolean hasExternalCall,
                                   boolean hasMint,
                                   boolean hasWithdraw) {
        int score = 0;
        if (hasOwner) score++;
        if (hasPause) score++;
        if (hasTransfer) score++;
        if (hasExternalCall) score += 2;
        if (hasMint) score++;
        if (hasWithdraw) score++;
        if (codeLength > 8000) score += 2;
        else if (codeLength > 3500) score++;

        if (score >= 5) return "high";
        if (score >= 3) return "medium";
        return "low";
    }

    private List<String> buildKeyFocus(String code,
                                       boolean hasOwner,
                                       boolean hasPause,
                                       boolean hasTransfer,
                                       boolean hasDelegateCall,
                                       boolean hasTxOrigin,
                                       boolean hasSelfdestruct,
                                       boolean hasMint,
                                       boolean hasWithdraw) {
        List<String> focus = new ArrayList<>();
        if (hasOwner) focus.add("owner / onlyOwner");
        if (hasPause) focus.add("pause / Pausable");
        if (hasTransfer) focus.add("transfer / transferFrom");
        if (hasMint) focus.add("mint / _mint");
        if (hasWithdraw) focus.add("withdraw / claim");
        if (hasDelegateCall) focus.add("delegatecall");
        if (hasTxOrigin) focus.add("tx.origin");
        if (hasSelfdestruct) focus.add("selfdestruct");

        String detectedContract = detectFirst(code, "\\b(contract|interface|library)\\s+([A-Za-z_][A-Za-z0-9_]*)", 2);
        if (detectedContract != null && focus.stream().noneMatch(item -> item.equals(detectedContract))) {
            focus.add(0, detectedContract);
        }
        return focus.size() > 8 ? focus.subList(0, 8) : focus;
    }

    private List<ContractCheckResponse.AnnotationSuggestion> buildAnnotations(boolean hasOwner,
                                                                              boolean hasTransfer,
                                                                              boolean hasDelegateCall,
                                                                              boolean hasTxOrigin,
                                                                              boolean hasSelfdestruct,
                                                                              boolean hasExternalCall,
                                                                              boolean hasUnchecked,
                                                                              boolean hasReentrancyRisk) {
        List<ContractCheckResponse.AnnotationSuggestion> annotations = new ArrayList<>();
        if (hasReentrancyRisk) {
            annotations.add(annotation("call{value / withdraw", "提现流程中先执行外部 value call，再更新余额，攻击合约可能在回调中重复进入 withdraw。", "先扣减余额再执行外部调用，或加入 ReentrancyGuard。"));
        }
        if (hasTxOrigin) {
            annotations.add(annotation("tx.origin", "使用 tx.origin 做权限判断容易受到调用链影响。", "改为 msg.sender 或基于角色权限的访问控制。"));
        }
        if (hasDelegateCall) {
            annotations.add(annotation("delegatecall", "delegatecall 会在当前合约上下文执行外部代码，风险边界较高。", "限制目标地址来源，并在调用前后增加状态与权限校验。"));
        }
        if (hasSelfdestruct) {
            annotations.add(annotation("selfdestruct / suicide", "合约可销毁逻辑会影响资产与状态的长期可用性。", "确认销毁权限、触发条件和资产处理路径是否足够明确。"));
        }
        if (hasExternalCall && hasTransfer) {
            annotations.add(annotation("外部调用 + 资产流转", "外部调用与资金变更组合出现时，需要关注重入与状态更新顺序。", "优先采用 checks-effects-interactions，并考虑重入保护。"));
        }
        if (hasOwner) {
            annotations.add(annotation("owner / onlyOwner", "管理员权限会影响核心操作边界。", "检查 owner 可执行函数是否过多，并补充事件记录或多签约束。"));
        }
        if (hasUnchecked) {
            annotations.add(annotation("unchecked", "unchecked 会跳过 Solidity 0.8+ 的默认溢出检查。", "确认循环边界或算术前置条件是否充分。"));
        }
        return annotations.size() > 5 ? annotations.subList(0, 5) : annotations;
    }

    private ContractCheckResponse.AnnotationSuggestion annotation(String target, String problem, String suggestion) {
        return ContractCheckResponse.AnnotationSuggestion.builder()
                .target(target)
                .problem(problem)
                .suggestion(suggestion)
                .build();
    }

    private ContractCheckResponse.RiskScores buildRiskScores(boolean hasOwner,
                                                             boolean hasPause,
                                                             boolean hasTransfer,
                                                             boolean hasExternalCall,
                                                             boolean hasDelegateCall,
                                                             boolean hasTxOrigin,
                                                             boolean hasSelfdestruct,
                                                             boolean hasMint,
                                                             boolean hasWithdraw,
                                                             boolean hasReentrancyRisk,
                                                             int codeLength) {
        int permissionRisk = 22
                + (hasOwner ? 26 : 0)
                + (hasPause ? 10 : 0)
                + (hasTxOrigin ? 28 : 0)
                + (hasSelfdestruct ? 16 : 0);
        int fundFlowRisk = 18
                + (hasTransfer ? 24 : 0)
                + (hasMint ? 14 : 0)
                + (hasWithdraw ? 18 : 0)
                + (hasExternalCall ? 16 : 0)
                + (hasReentrancyRisk ? 28 : 0);
        int logicRisk = 20
                + (hasDelegateCall ? 30 : 0)
                + (hasExternalCall ? 12 : 0)
                + (hasReentrancyRisk ? 26 : 0)
                + (hasPause ? 8 : 0)
                + (codeLength > 8000 ? 18 : codeLength > 3500 ? 10 : 0);

        return ContractCheckResponse.RiskScores.builder()
                .permissionRisk(clampRisk(permissionRisk))
                .fundFlowRisk(clampRisk(fundFlowRisk))
                .logicComplexityRisk(clampRisk(logicRisk))
                .build();
    }

    private int clampRisk(int value) {
        return Math.max(5, Math.min(95, value));
    }

    private String detectFirst(String text, String regex, int group) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            return matcher.group(group);
        }
        return null;
    }

    private void saveRecordSafely(ContractCheckResponse response,
                                  String contractName,
                                  String sourceCode,
                                  String inputMode,
                                  String fileName,
                                  String status) {
        try {
            Long recordId = jdbcTemplate.queryForObject("""
                    INSERT INTO contract_check_record (
                        user_id, input_mode, file_name, contract_name, source_code, status,
                        summary, conclusion, risk_level, key_issues, suggestions, raw_ai_result,
                        contract_type, complexity_level, key_focus, annotation_result
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """,
                    Long.class,
                    UserContext.getUserId(),
                    normalizeInputMode(inputMode),
                    safeLimit(fileName, 255),
                    safeLimit(contractName, 255),
                    sourceCode,
                    status,
                    response.getSummary(),
                    response.getConclusion(),
                    response.getRiskLevel(),
                    joinLines(response.getIssues()),
                    joinLines(response.getAiSuggestions()),
                    response.getAiSummary(),
                    response.getContractType(),
                    response.getComplexityLevel(),
                    joinLines(response.getKeyFocus()),
                    formatAnnotations(response.getAnnotations())
            );
            response.setSaved(true);
            response.setRecordId(recordId);
        } catch (Exception e) {
            log.warn("保存智能预审记录失败，当前预审结果仍正常返回", e);
            response.setSaved(false);
        }
    }

    private String normalizeInputMode(String inputMode) {
        if ("upload".equalsIgnoreCase(inputMode)) {
            return "upload";
        }
        return "paste";
    }

    private String safeLimit(String value, int limit) {
        if (value == null) return null;
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private String joinLines(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join("\n", list);
    }

    private String formatAnnotations(List<ContractCheckResponse.AnnotationSuggestion> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return "";
        }
        return annotations.stream()
                .map(item -> "问题片段：" + item.getTarget() + "\n问题说明：" + item.getProblem() + "\n建议修改：" + item.getSuggestion())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private String buildRuleSummary(String contractName,
                                    boolean validSolidity,
                                    boolean structurallyComplete,
                                    String riskLevel,
                                    boolean hasOwner,
                                    boolean hasPause,
                                    boolean hasTransfer) {
        String name = (contractName == null || contractName.isBlank()) ? "该合约" : "合约“" + contractName + "”";
        StringBuilder sb = new StringBuilder();

        if (!validSolidity) {
            sb.append(name).append("未通过基础 Solidity 特征检查，当前不建议收录。");
            return sb.toString();
        }

        sb.append(name).append("已通过基础 Solidity 特征识别");
        if (structurallyComplete) {
            sb.append("，且结构基本完整");
        } else {
            sb.append("，但结构完整性仍需进一步确认");
        }
        sb.append("。");

        sb.append("从规则特征看，当前风险等级为 ").append(riskLevel).append("。");

        List<String> features = new ArrayList<>();
        if (hasOwner) features.add("权限控制");
        if (hasPause) features.add("暂停机制");
        if (hasTransfer) features.add("转账相关逻辑");

        if (!features.isEmpty()) {
            sb.append("源码中可能涉及").append(String.join("、", features)).append("等功能。");
        }

        return sb.toString();
    }
}
