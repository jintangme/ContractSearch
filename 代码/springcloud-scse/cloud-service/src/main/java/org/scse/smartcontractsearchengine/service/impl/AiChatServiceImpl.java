package org.scse.smartcontractsearchengine.service.impl;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.common.enums.OpenAiClientType;
import org.scse.smartcontractsearchengine.common.ai.AiModelRegistry;
import org.scse.smartcontractsearchengine.config.OpenAIClientFactory;
import org.scse.smartcontractsearchengine.model.dto.AiChatRequest;
import org.scse.smartcontractsearchengine.model.dto.AiChatResponse;
import org.scse.smartcontractsearchengine.service.AiChatService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {
    private static final int MAX_SOURCE_CHARS = 6000;
    private static final int MAX_LEAN_CHARS = 6000;

    private final OpenAIClientFactory openAIClientFactory;

    public AiChatServiceImpl(OpenAIClientFactory openAIClientFactory) {
        this.openAIClientFactory = openAIClientFactory;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        String message = request == null ? "" : safe(request.getMessage());
        if (message.isBlank()) {
            return new AiChatResponse(false, "消息不能为空");
        }

        String requestedModel = request.getModel();
        String modelUsed;
        try {
            modelUsed = AiModelRegistry.resolveModel(requestedModel);
        } catch (IllegalArgumentException e) {
            return new AiChatResponse(false, e.getMessage(), AiModelRegistry.PROVIDER, requestedModel, null);
        }

        OpenAIClient client = openAIClientFactory.getClient(OpenAiClientType.ZHIPU);
        if (client == null) {
            return new AiChatResponse(false, "AI 服务暂时不可用，请检查后端模型配置。", AiModelRegistry.PROVIDER, requestedModel, modelUsed);
        }

        String responseMode = resolveResponseMode(request, message);
        boolean editIntent = "edit_proposal".equals(responseMode);
        String editTarget = resolveEditTarget(request.getEditTarget(), message, request.getContext());
        String language = safe(request.getLanguage()).isBlank() ? "zh-CN" : safe(request.getLanguage());
        String userPrompt = buildUserPrompt(message, request.getContext(), modelUsed, responseMode, editTarget, language);
        try {
            log.info("[AI] scene=chat provider={} requestedModel={} modelUsed={}", AiModelRegistry.PROVIDER, requestedModel, modelUsed);
            String answer = CompletableFuture.supplyAsync(() -> callModel(client, modelUsed, userPrompt, responseMode))
                    .orTimeout(45, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.error("AI chat failed or timed out", ex);
                        return "AI 服务暂时不可用，请稍后重试。";
                    })
                    .join();
            boolean success = !answer.startsWith("AI 服务暂时不可用");
            return new AiChatResponse(success, answer, AiModelRegistry.PROVIDER, requestedModel, modelUsed);
        } catch (Exception e) {
            log.error("AI chat failed", e);
            return new AiChatResponse(false, "AI 服务暂时不可用", AiModelRegistry.PROVIDER, requestedModel, modelUsed);
        }
    }

    private String callModel(OpenAIClient client, String modelUsed, String userPrompt, String responseMode) {
        try {
            var params = ChatCompletionCreateParams.builder()
                    .model(modelUsed)
                    .addSystemMessage(buildSystemPrompt(responseMode))
                    .addUserMessage(userPrompt)
                    .temperature(0.25)
                    .build();
            var completion = client.chat().completions().create(params);
            return completion.choices().get(0).message().content().orElse("AI 服务暂时不可用");
        } catch (Exception e) {
            log.error("Calling AI chat model failed", e);
            return "AI 服务暂时不可用，请检查模型服务或 API Key 配置。";
        }
    }

    private String buildSystemPrompt(String responseMode) {
        String base = """
                你是 Contract Lab 的中文代码助手，体验类似 Codex / Cursor。
                你必须根据 responseMode 决定输出类型。
                你可以读取内部上下文中的 Solidity 合约、Lean4 证明、编译错误和 Lean 错误，但不要把内部上下文原样回复给用户。
                如果内部上下文已经包含源码、预审报告或诊断信息，你必须直接使用这些材料，不要要求用户再次提供相同内容。
                所有代码修改都只能作为提案返回，不能声称已经修改了代码；用户会在前端审核后手动应用。
                涉及 Lean 时，不要声称直接证明 Solidity/EVM 语义，只能说这是抽象模型或 Lean 证明草稿。
                除非用户明确要求英文，否则全部使用中文；代码标识符、函数名和源码内容可以保持原样。
                输出必须是严格 JSON，不要 Markdown，不要代码围栏，不要解释性前后缀。
                除 newSourceCode 里的代码本身外，title、summary、changes、message 都优先使用中文。
                普通回答格式：{"type":"message","message":"..."}
                代码修改格式：{"type":"edit_proposal","target":"solidity|lean","title":"...","summary":"...","changes":["..."],"newSourceCode":"完整的新文件内容"}
                newSourceCode 必须是完整文件内容，changes 不超过 5 条，summary 简洁。
                """;
        if ("edit_proposal".equals(responseMode)) {
            return base + "\nresponseMode=edit_proposal：必须返回 edit_proposal JSON，标题、摘要和 changes 必须中文，不要返回 message，不要总结上下文。";
        }
        if ("clarify".equals(responseMode)) {
            return base + "\nresponseMode=clarify：只返回 {\"type\":\"message\",\"message\":\"你希望我修改哪一部分？可以说具体功能或目标。\"}，不要生成代码，不要长篇解释。";
        }
        return base + "\nresponseMode=message：直接用中文简洁回答用户问题，不要生成代码修改提案，不要输出内部上下文摘要。";
    }

    private String buildUserPrompt(String message, AiChatRequest.AiChatContext context, String model, String responseMode, String editTarget, String language) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户消息：\n").append(message).append("\n\n");
        builder.append("内部控制字段（不要原样输出）：\n");
        builder.append("- selectedModel: ").append(model).append("\n");
        builder.append("- responseMode: ").append(responseMode).append("\n");
        builder.append("- language: ").append(language).append("\n");
        builder.append("- expectedTarget: ").append(editTarget).append("\n");
        if (context == null) {
            return builder.toString();
        }
        builder.append("- mode: ").append(safe(context.getMode())).append("\n");
        builder.append("- activeFileName: ").append(safe(context.getActiveFileName())).append("\n");
        builder.append("- activeExample: ").append(safe(context.getActiveExampleName())).append("\n");
        builder.append("- compileStatus: ").append(safe(context.getCompileStatus())).append("\n");
        builder.append("- leanCheckStatus: ").append(safe(context.getLeanCheckStatus())).append("\n\n");
        builder.append("- lastIntent: ").append(safe(context.getLastIntent())).append("\n");
        builder.append("- lastResponseMode: ").append(safe(context.getLastResponseMode())).append("\n");
        builder.append("- lastUserTask: ").append(safe(context.getLastUserTask())).append("\n");
        builder.append("- lastAiSummary: ").append(safe(context.getLastAiSummary())).append("\n\n");
        if (context.getPrecheckReport() != null) {
            builder.append("已附加预审报告（这是可用上下文，不要要求用户再次提供）：\n")
                    .append(formatPrecheckReport(context.getPrecheckReport()))
                    .append("\n\n");
            builder.append("预审问题分类：\n")
                    .append("- blockingIssues: ").append(formatPrecheckIssues(context.getBlockingIssues())).append("\n")
                    .append("- advisoryIssues: ").append(formatPrecheckIssues(context.getAdvisoryIssues())).append("\n\n");
        }
        builder.append("当前 Solidity 源码（内部上下文，可能截断）：\n```solidity\n")
                .append(truncate(context.getSoliditySource(), MAX_SOURCE_CHARS))
                .append("\n```\n\n");
        builder.append("当前 Lean4 证明代码（内部上下文，可能截断）：\n```lean\n")
                .append(truncate(context.getLeanSource(), MAX_LEAN_CHARS))
                .append("\n```\n\n");
        builder.append("Compile errors：\n").append(formatDiagnostics(context.getCompileErrors())).append("\n\n");
        builder.append("Compile warnings：\n").append(formatDiagnostics(context.getCompileWarnings())).append("\n\n");
        builder.append("Lean errors：\n").append(formatDiagnostics(context.getLeanErrors())).append("\n\n");
        builder.append("Lean warnings：\n").append(formatDiagnostics(context.getLeanWarnings())).append("\n");
        if ("edit_proposal".equals(responseMode) && context.getPrecheckReport() != null) {
            builder.append("\n用户要求根据预审报告修复时，必须基于上面的预审报告 issues 和 Solidity 源码生成 edit_proposal。")
                    .append("不要回复“请提供预审报告”或“请提供代码”。")
                    .append("Critical / High 是阻断问题，需要优先修复；Medium / Low 是优化建议，不要为了低风险建议大幅重构，优先保持原功能和简洁性，不要强行追求零问题。")
                    .append("必须返回完整可替换 Solidity 文件内容 newSourceCode；不要只返回建议、片段或解释。\n");
        }
        if ("edit_proposal".equals(responseMode) && "solidity".equals(editTarget) && safe(context.getSoliditySource()).contains("TODO")) {
            builder.append("\n如果用户要求填充模板，请生成一个完整、可编译、无外部依赖的 Solidity 合约。")
                    .append("可以使用 AccessControlledStorage：owner、onlyOwner、value、setValue、getValue、resetValue、ValueChanged、OwnershipTransferred、transferOwnership。\n");
        }
        return builder.toString();
    }

    private String formatPrecheckReport(AiChatRequest.PrecheckReport report) {
        if (report == null) return "无";
        StringBuilder builder = new StringBuilder();
        builder.append("- conclusion: ").append(safe(report.getConclusion())).append("\n");
        builder.append("- conclusionText: ").append(safe(report.getConclusionText())).append("\n");
        builder.append("- riskLevel: ").append(safe(report.getRiskLevel())).append("\n");
        builder.append("- issues:\n");
        List<AiChatRequest.PrecheckIssue> issues = report.getIssues();
        if (issues == null || issues.isEmpty()) {
            builder.append("  []");
            return builder.toString();
        }
        for (AiChatRequest.PrecheckIssue issue : issues) {
            builder.append("  - severity=").append(safe(issue.getSeverity()))
                    .append(", line=").append(issue.getLine())
                    .append(", column=").append(issue.getColumn())
                    .append(", title=").append(safe(issue.getTitle()))
                    .append(", message=").append(safe(issue.getMessage()));
            if (!safe(issue.getSuggestion()).isBlank()) {
                builder.append(", suggestion=").append(safe(issue.getSuggestion()));
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private String formatPrecheckIssues(List<AiChatRequest.PrecheckIssue> issues) {
        if (issues == null || issues.isEmpty()) return "[]";
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < issues.size(); i++) {
            AiChatRequest.PrecheckIssue issue = issues.get(i);
            if (i > 0) builder.append("; ");
            builder.append(safe(issue.getSeverity()))
                    .append(" line=")
                    .append(issue.getLine())
                    .append(" title=")
                    .append(safe(issue.getTitle()));
            String detail = !safe(issue.getSuggestion()).isBlank() ? safe(issue.getSuggestion()) : safe(issue.getMessage());
            if (!detail.isBlank()) {
                builder.append(" detail=").append(detail);
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private String formatDiagnostics(List<AiChatRequest.LeanDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        for (AiChatRequest.LeanDiagnostic diagnostic : diagnostics) {
            builder.append("- line=").append(diagnostic.getLine())
                    .append(", column=").append(diagnostic.getColumn())
                    .append(", severity=").append(safe(diagnostic.getSeverity()))
                    .append(", message=").append(safe(diagnostic.getMessage()))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String resolveResponseMode(AiChatRequest request, String message) {
        String explicit = safe(request.getResponseMode()).trim().toLowerCase(Locale.ROOT);
        if ("message".equals(explicit) || "edit_proposal".equals(explicit) || "clarify".equals(explicit)) {
            return explicit;
        }

        String legacy = safe(request.getResponseFormat()).trim().toLowerCase(Locale.ROOT);
        if ("edit_proposal".equals(legacy)) return "edit_proposal";
        if ("message".equals(legacy) || "clarify".equals(legacy)) return legacy;
        if (Boolean.TRUE.equals(request.getEditIntent())) return "edit_proposal";
        return classifyMessageIntent(message);
    }

    private String classifyMessageIntent(String message) {
        String value = safe(message).toLowerCase(Locale.ROOT);
        String[] consultation = {
                "你认为", "是否", "是不是", "对吗", "可以吗", "合理吗", "为什么", "什么意思",
                "是什么", "怎么理解", "会不会", "有没有问题", "解释", "分析", "评价",
                "is this correct", "what does this mean", "why", "how does this work", "is this reasonable"
        };
        String[] explicitEdit = {
                "帮我修改", "帮我加", "帮我实现", "帮我填充", "帮我补全", "帮我修复", "帮我重构",
                "帮我删除", "帮我替换", "帮我生成", "改成", "加上", "加一个", "删除", "替换",
                "应用到代码", "生成代码", "生成一个合约", "写一个函数", "写一个合约", "把这个模板填完整",
                "modify", "add", "implement", "fill", "complete", "refactor", "fix", "replace",
                "apply", "generate code", "write a function", "update the code"
        };
        String[] vagueEdit = {"帮我改一下", "优化一下", "处理一下", "帮我弄一下", "改一下", "弄一下"};

        boolean hasExplicitEdit = containsAny(value, explicitEdit);
        if (containsAny(value, consultation) && !hasExplicitEdit) return "message";
        if (hasExplicitEdit) return "edit_proposal";
        if (containsAny(value, vagueEdit)) return "clarify";
        return "message";
    }

    private boolean containsAny(String value, String[] keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean isLikelyEditRequest(String message) {
        return "edit_proposal".equals(classifyMessageIntent(message));
    }

    private boolean isLikelyConsultation(String message) {
        String value = safe(message).toLowerCase(Locale.ROOT);
        String[] keywords = {
                "你认为", "是否", "是不是", "对吗", "可以吗", "合理吗", "为什么", "什么意思",
                "是什么", "怎么理解", "会不会", "有没有问题", "解释", "分析", "评价",
                "is this correct", "what does this mean", "why", "how does this work", "is this reasonable"
        };
        return containsAny(value, keywords);
    }

    private String resolveEditTarget(String requestedTarget, String message, AiChatRequest.AiChatContext context) {
        String explicit = safe(requestedTarget).trim().toLowerCase(Locale.ROOT);
        if ("solidity".equals(explicit) || "lean".equals(explicit)) return explicit;

        String value = safe(message).toLowerCase(Locale.ROOT);
        String[] leanHints = {"lean", "证明", "theorem", "structure", "rfl", "trivial", "证明错误", "形式化"};
        String[] solidityHints = {"合约", "solidity", "函数", "modifier", "owner", "event", "编译错误", "部署", "交易", "setvalue", "模板"};
        for (String keyword : leanHints) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) return "lean";
        }
        for (String keyword : solidityHints) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) return "solidity";
        }
        return context != null && "proof".equalsIgnoreCase(safe(context.getMode())) ? "lean" : "solidity";
    }

    private String truncate(String value, int maxChars) {
        String text = safe(value);
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n... [truncated]";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
