package org.scse.smartcontractsearchengine.service.impl;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.common.enums.OpenAiClientType;
import org.scse.smartcontractsearchengine.common.ai.AiModelRegistry;
import org.scse.smartcontractsearchengine.config.OpenAIClientFactory;
import org.scse.smartcontractsearchengine.dto.AiExplainResponse;
import org.scse.smartcontractsearchengine.model.vo.ContractDetailVo;
import org.scse.smartcontractsearchengine.service.AiExplainService;
import org.scse.smartcontractsearchengine.service.SearchService;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiExplainServiceImpl implements AiExplainService {

    private final OpenAIClientFactory openAIClientFactory;
    private final SearchService searchService;

    public AiExplainServiceImpl(OpenAIClientFactory openAIClientFactory,
                                SearchService searchService) {
        this.openAIClientFactory = openAIClientFactory;
        this.searchService = searchService;
    }

    @Override
    public AiExplainResponse explainContract(Long id, String mode, String model) {
        ContractDetailVo contract = searchService.getContractById(id);
        if (contract == null) {
            throw new RuntimeException("未找到该合约");
        }

        String normalizedMode = normalizeMode(mode);
        String modelUsed = AiModelRegistry.resolveModel(model);

        String contractName = safeText(contract.getContractName(), "未命名合约");
        String address = safeText(contract.getAddress(), "");
        String category = safeText(contract.getCategory(), "");
        String description = safeText(contract.getDescription(), "");
        String sourceCode = safeText(contract.getSourceCode(), "");

        int sourceLimit = "deep".equals(normalizedMode) ? 16000 : 7000;
        if (sourceCode.length() > sourceLimit) {
            sourceCode = sourceCode.substring(0, sourceLimit);
        }

        String fallbackSummary = buildLocalSummary(contractName, category, description, sourceCode, normalizedMode);

        OpenAIClient client = openAIClientFactory.getClient(OpenAiClientType.ZHIPU);
        if (client == null) {
            log.warn("AI 客户端未初始化，直接返回本地解释");
            return buildResponse(contractName, fallbackSummary, normalizedMode, category, description, sourceCode, model, modelUsed);
        }

        String prompt = buildPrompt(contractName, address, category, description, sourceCode, normalizedMode);
        String systemPrompt = buildSystemPrompt(normalizedMode);

        try {
            log.info("[AI] scene=explain provider={} requestedModel={} modelUsed={}", AiModelRegistry.PROVIDER, model, modelUsed);

            long timeoutSeconds = "deep".equals(normalizedMode) ? 28 : 15;
            String content = CompletableFuture.supplyAsync(() -> callAi(client, modelUsed, systemPrompt, prompt, normalizedMode))
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.error("AI 调用失败或超时，使用本地解释兜底，contractId={}, mode={}", id, normalizedMode, ex);
                        return fallbackSummary;
                    })
                    .join();

            log.info("AI 解释生成完成，contractId={}, mode={}", id, normalizedMode);
            return buildResponse(contractName, content, normalizedMode, category, description, sourceCode, model, modelUsed);
        } catch (Exception e) {
            log.error("AI 解释整体流程异常，使用本地解释兜底，contractId={}, mode={}", id, normalizedMode, e);
            return buildResponse(contractName, fallbackSummary, normalizedMode, category, description, sourceCode, model, modelUsed);
        }
    }

    private AiExplainResponse buildResponse(String contractName,
                                            String summary,
                                            String mode,
                                            String category,
                                            String description,
                                            String sourceCode,
                                            String requestedModel,
                                            String modelUsed) {
        AiExplainResponse response = new AiExplainResponse(contractName, summary);
        response.setProvider(AiModelRegistry.PROVIDER);
        response.setRequestedModel(requestedModel);
        response.setModelUsed(modelUsed);
        if (!"deep".equals(mode)) {
            return response;
        }

        String lowerSource = sourceCode.toLowerCase();
        String lowerText = (contractName + " " + category + " " + description + " " + sourceCode).toLowerCase();
        String contractType = inferContractType(category, lowerText);
        int featureScore = countFeatureScore(lowerSource, lowerText);
        int permissionRisk = clampRisk(24
                + (containsAny(lowerSource, "ownable", "onlyowner", "owner") ? 24 : 0)
                + (containsAny(lowerSource, "whitelist", "role", "accesscontrol") ? 16 : 0)
                + (containsAny(lowerSource, "pause", "pausable") ? 10 : 0));
        int assetFlowRisk = clampRisk(20
                + (containsAny(lowerSource, "transfer(", "transferfrom(", "approve(", "approval") ? 24 : 0)
                + (containsAny(lowerText, "mint", "burn", "erc20", "erc721", "token", "nft") ? 14 : 0)
                + (containsAny(lowerText, "vault", "staking", "reward", "auction", "marketplace") ? 16 : 0));
        int complexityRisk = clampRisk(24
                + featureScore * 7
                + (sourceCode.length() > 9000 ? 18 : sourceCode.length() > 4500 ? 10 : 0));
        int averageRisk = (permissionRisk + assetFlowRisk + complexityRisk) / 3;

        response.setContractType(contractType);
        response.setComplexityLevel(inferComplexityLevel(featureScore, sourceCode.length()));
        response.setOverallRiskLevel(inferRiskLevel(averageRisk));
        response.setCorePurpose(inferCorePurpose(description, contractType));
        response.setRiskScores(new AiExplainResponse.RiskScores(permissionRisk, assetFlowRisk, complexityRisk));
        return response;
    }

    private String inferContractType(String category, String lowerText) {
        if (category != null && !category.isBlank()) {
            return category.trim();
        }
        if (containsAny(lowerText, "erc721", "nft", "non-fungible")) {
            return "NFT / ERC721";
        }
        if (containsAny(lowerText, "erc20", "token")) {
            return "Token / ERC20";
        }
        if (containsAny(lowerText, "marketplace", "auction")) {
            return "Marketplace";
        }
        if (containsAny(lowerText, "governance", "voting", "dao")) {
            return "Governance";
        }
        if (containsAny(lowerText, "staking", "vault", "reward", "treasury")) {
            return "DeFi / Vault";
        }
        return "Smart Contract";
    }

    private String inferComplexityLevel(int featureScore, int sourceLength) {
        if (featureScore >= 6 || sourceLength > 9000) {
            return "高";
        }
        if (featureScore >= 3 || sourceLength > 4500) {
            return "中";
        }
        return "低";
    }

    private String inferRiskLevel(int score) {
        if (score >= 65) {
            return "高";
        }
        if (score >= 40) {
            return "中";
        }
        return "低";
    }

    private String inferCorePurpose(String description, String contractType) {
        String cleaned = stripDescriptionPrefix(description);
        if (!cleaned.isBlank()) {
            return truncate(cleaned, 88);
        }
        return "围绕 " + contractType + " 场景提供链上状态管理、权限控制或业务流程封装。";
    }

    private String stripDescriptionPrefix(String description) {
        return safeText(description, "")
                .replaceAll("\\r", "")
                .replaceAll("\\n+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^(\\d+[.．、]|[（(]?[一二三四五六七八九十][）)]?[、.．])\\s*", "")
                .trim();
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private int countFeatureScore(String lowerSource, String lowerText) {
        int score = 0;
        if (containsAny(lowerSource, "ownable", "onlyowner", "accesscontrol")) score++;
        if (containsAny(lowerSource, "pause", "pausable")) score++;
        if (containsAny(lowerSource, "whitelist", "role")) score++;
        if (containsAny(lowerSource, "transfer(", "transferfrom(", "approve(")) score++;
        if (containsAny(lowerText, "mint", "burn", "token", "erc20", "erc721", "nft")) score++;
        if (containsAny(lowerText, "staking", "vault", "reward", "treasury")) score++;
        if (containsAny(lowerText, "marketplace", "auction")) score++;
        if (containsAny(lowerText, "governance", "voting", "dao")) score++;
        if (containsAny(lowerSource, "request", "queue", "report", "audit")) score++;
        return score;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int clampRisk(int value) {
        return Math.max(8, Math.min(92, value));
    }

    private String callAi(OpenAIClient client,
                          String modelUsed,
                          String systemPrompt,
                          String prompt,
                          String mode) {
        var params = ChatCompletionCreateParams.builder()
                .model(modelUsed)
                .addSystemMessage(systemPrompt)
                .addUserMessage(prompt)
                .temperature("deep".equals(mode) ? 0.25 : 0.35)
                .build();

        var completion = client.chat().completions().create(params);

        try {
            return completion.choices().get(0).message().content().orElse("暂时无法生成解释");
        } catch (Exception e) {
            log.error("解析 AI 返回内容失败", e);
            return "暂时无法生成解释";
        }
    }

    private String buildSystemPrompt(String mode) {
        if ("deep".equals(mode)) {
            return """
                    你是一个面向学习、展示和研究场景的智能合约分析助手。

                    你的任务不是简单复述描述，而是基于合约名称、分类、已有描述和源码片段，
                    产出一份“研究报告风格”的中文解读。

                    深入分析模式的要求：
                    1. 输出要明显区别于普通摘要，内容要系统、完整、有层次。
                    2. 不要输出代码块，不要使用 markdown 表格。
                    3. 必须分成以下 6 个部分，并且每个部分都要单独成段、使用完全一致的标题，不要给标题加“一、二、三、1.”等编号：
                       合约定位
                       功能模块
                       权限结构
                       资产与调用逻辑
                       风险提示
                       应用场景与阅读建议
                    4. 每个标题后只写纯正文，不要重复标题，不要输出章节编号或编号残片。
                    5. 每个部分都要写成自然中文段落或短列表，避免空洞套话。
                    6. 可以结合已有描述，但绝对不要直接照抄原文。
                    7. 允许基于源码结构做合理推断，但不确定时必须使用“可能”“大致”“从结构上看”等保守表达。
                    8. 不要把没有证据的内容说成确定事实。
                    9. 输出风格要像“课程项目报告 / 答辩说明 / 研究型产品分析”，而不是普通聊天回复。
                    10. 总长度控制在 650 到 1100 字左右。
                    11. 如果源码里出现 Ownable、Pausable、Whitelist、transfer、audit、queue、request、report 等关键词，要优先结合这些特征展开分析。
                    """;
        }

        return """
                你是一个面向普通用户的智能合约解释助手。

                你的任务是根据合约名称、分类、已有描述、源码片段，
                用清晰、自然、保守的中文解释这个合约的大致用途。

                快速解读模式的要求：
                1. 使用自然中文，不要输出代码块。
                2. 必须分成以下 4 个小标题，并使用完全一致的标题，不要给标题加“一、二、三、1.”等编号：
                   一句话总结
                   主要用途
                   核心机制
                   适合关注的点
                3. 每个标题后只写纯正文，不要重复标题，不要输出章节编号或编号残片。
                4. 每个部分控制在 1 到 2 句话。
                5. 可以参考已有描述，但不要直接照抄原文。
                6. 对不确定的内容要使用“可能”“大致”“从结构上看”等保守表达。
                7. 不要捏造源码中没有明确体现的细节。
                8. 总长度控制在 160 到 280 字左右。
                """;
    }

    private String buildPrompt(String contractName,
                               String address,
                               String category,
                               String description,
                               String sourceCode,
                               String mode) {
        if ("deep".equals(mode)) {
            return """
                    请对下面这个智能合约做一份“研究报告风格”的深入分析。

                    你的目标读者是：
                    - 课程项目答辩评委
                    - 想学习智能合约设计思路的学生
                    - 希望快速建立理解框架的研究者或产品观察者

                    请特别注意：
                    - 不能只把已有描述换个说法
                    - 要尽可能从源码结构中提炼信息
                    - 要强调“它是什么、怎么运作、谁能控制、适合什么场景、应关注什么问题”
                    - 如果源码不足以支持强结论，就必须保守表达
                    - 输出要明显有层次感，适合展示与答辩

                    合约名称：%s
                    合约地址：%s
                    分类：%s
                    已有描述：%s

                    合约源码片段：
                    %s
                    """.formatted(contractName, address, category, description, sourceCode);
        }

        return """
                请解释下面这个智能合约，目标读者是不熟悉底层代码、但希望快速理解合约用途的普通用户。

                请重点回答：
                - 这个合约大致是做什么的
                - 它可能有哪些核心功能
                - 它适合什么场景，或者为什么值得关注

                请注意：
                - 优先基于已有描述和源码中的明显特征进行概括
                - 不要照抄已有描述
                - 不要输出生硬的条目式说明
                - 不确定时请保守表达

                合约名称：%s
                合约地址：%s
                分类：%s
                已有描述：%s

                合约源码片段：
                %s
                """.formatted(contractName, address, category, description, sourceCode);
    }

    private String buildLocalSummary(String contractName,
                                     String category,
                                     String description,
                                     String sourceCode,
                                     String mode) {
        boolean hasOwnable = sourceCode.contains("Ownable");
        boolean hasPause = sourceCode.contains("pause(") || sourceCode.contains("Pausable");
        boolean hasTransfer = sourceCode.contains("transfer(") || sourceCode.contains("transferFrom(");
        boolean hasWhitelist = sourceCode.contains("Whitelist");
        boolean hasAudit = sourceCode.toLowerCase().contains("audit");
        boolean hasQueue = sourceCode.toLowerCase().contains("queue");
        boolean hasReport = sourceCode.toLowerCase().contains("report");
        boolean hasRequest = sourceCode.toLowerCase().contains("request");

        if ("deep".equals(mode)) {
            StringBuilder sb = new StringBuilder();

            sb.append("合约定位\n");
            sb.append("从名称“").append(contractName).append("”以及现有描述来看，这个合约并不是单纯的资产转移组件，而更像是围绕特定业务流程设计的管理型合约。");
            if (!category.isBlank()) {
                sb.append("从分类上看，它目前被归入“").append(category).append("”类型，这也说明它可能不属于标准化的单一场景模板，而是更偏业务逻辑封装。");
            }

            sb.append("\n\n功能模块\n");
            if (!description.isBlank()) {
                sb.append("结合已有描述，这个合约主要围绕以下任务展开：").append(description.trim());
            } else {
                sb.append("当前没有现成描述，但从源码结构看，它大致围绕状态管理、角色协作和业务流程控制展开。");
            }
            sb.append("从源码关键词上看，");
            boolean appended = false;
            if (hasAudit) {
                sb.append("它很可能与审计请求、审计任务分配或审计结果提交有关");
                appended = true;
            }
            if (hasQueue) {
                if (appended) sb.append("，同时还涉及队列管理");
                else {
                    sb.append("它可能涉及队列式任务管理");
                    appended = true;
                }
            }
            if (hasReport) {
                if (appended) sb.append("、报告记录");
                else {
                    sb.append("它可能涉及报告记录");
                    appended = true;
                }
            }
            if (hasRequest) {
                if (appended) sb.append("以及请求生命周期控制");
                else {
                    sb.append("它可能涉及请求生命周期控制");
                    appended = true;
                }
            }
            if (!appended) {
                sb.append("它更像是一个围绕业务状态流转展开的功能合约");
            }
            sb.append("。");

            sb.append("\n\n权限结构\n");
            if (hasOwnable) {
                sb.append("源码中出现了 Ownable，这说明合约至少包含所有者角色，某些关键操作可能由 owner 控制。");
            } else {
                sb.append("从目前可见结构看，合约应当存在一定的管理角色，但源码片段中没有非常明确的所有者控制结论。");
            }
            if (hasPause) {
                sb.append("同时出现 Pausable 或 pause 相关逻辑，说明它还具备暂停/恢复机制，这通常用于在异常情况下中止核心流程。");
            }
            if (hasWhitelist) {
                sb.append("如果源码中包含 Whitelist，则说明它还可能引入白名单访问控制，用于限制可操作的地址范围。");
            }

            sb.append("\n\n资产与调用逻辑\n");
            sb.append("从整体结构上看，这类合约往往不是孤立运行的，而是作为某个链上服务系统中的流程中枢。");
            sb.append("它可能负责接收请求、记录状态、分配角色、保存结果，并在不同参与方之间维持业务顺序。");
            if (hasTransfer) {
                sb.append("源码中出现 transfer 或 transferFrom 等关键词时，还需要把资产转移和状态更新放在一起观察。");
            }

            sb.append("\n\n风险提示\n");
            sb.append("从学习和分析角度看，这类合约最值得关注的地方通常不是单个函数本身，而是整个状态流转是否严谨、角色权限是否清晰、异常状态是否可恢复。");
            if (hasOwnable || hasWhitelist || hasPause) {
                sb.append("尤其当源码同时包含 owner、白名单、暂停机制时，就更需要关注这些权限是否会过度集中，以及它们在业务流程中的触发边界是否明确。");
            }
            if (hasTransfer) {
                sb.append("如果还伴随 transfer 或 transferFrom 等逻辑，则还要额外关注资金流转与状态更新是否一致。");
            }
            sb.append("\n\n应用场景与阅读建议\n");
            sb.append("如果把它放到实际应用里，更适合出现在智能合约审计平台、任务分发系统、权限协作系统，或者其他需要链上记录流程状态的场景中。");
            sb.append("建议先阅读角色权限相关函数，再看核心状态变更函数，最后核对外部调用和事件记录是否完整。");

            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("一句话总结\n");
        sb.append("这个合约名为“").append(contractName).append("”，大致用于承载某类链上业务逻辑或资产/流程管理。");
        if (!category.isBlank()) {
            sb.append("从现有分类来看，它更接近“").append(category).append("”类型的智能合约。");
        }

        sb.append("\n\n主要用途\n");
        if (!description.isBlank()) {
            sb.append("根据已有描述，它主要用于：").append(description.trim());
        } else {
            sb.append("当前没有现成描述，但从源码结构看，它应该用于特定业务流程或链上功能管理。");
        }

        sb.append("\n\n核心机制\n");
        sb.append("从源码特征来看，它可能包含");
        boolean appended = false;
        if (hasOwnable) {
            sb.append("所有者权限控制");
            appended = true;
        }
        if (hasPause) {
            if (appended) sb.append("、");
            sb.append("暂停/恢复机制");
            appended = true;
        }
        if (hasWhitelist) {
            if (appended) sb.append("、");
            sb.append("白名单控制");
            appended = true;
        }
        if (hasAudit) {
            if (appended) sb.append("、");
            sb.append("审计流程相关逻辑");
            appended = true;
        }
        if (!appended) {
            sb.append("业务逻辑封装与状态管理");
        }
        sb.append("。");

        sb.append("\n\n适合关注的点\n");
        sb.append("整体来看，这类合约更适合用于理解真实链上业务流程，以及权限和功能模块如何协同工作。");
        return sb.toString();
    }

    private String normalizeMode(String mode) {
        if ("deep".equalsIgnoreCase(mode)) {
            return "deep";
        }
        return "quick";
    }

    private String safeText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
