package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatRequest {
    private String model;
    private String message;
    private String responseFormat;
    private String responseMode;
    private String language;
    private Boolean editIntent;
    private String editTarget;
    private AiChatContext context;

    @Data
    public static class AiChatContext {
        private String activeFileName;
        private String activeExampleId;
        private String activeExampleName;
        private String activePanelTab;
        private String source;
        private String contractName;
        private String soliditySource;
        private String compileStatus;
        private String compileOutput;
        private String abi;
        private List<LeanDiagnostic> compileErrors;
        private List<LeanDiagnostic> compileWarnings;
        private List<Object> events;
        private String leanSource;
        private String leanCheckStatus;
        private List<LeanDiagnostic> leanErrors;
        private List<LeanDiagnostic> leanWarnings;
        private String mode;
        private PrecheckReport precheckReport;
        private List<PrecheckIssue> blockingIssues;
        private List<PrecheckIssue> advisoryIssues;
        private String lastIntent;
        private String lastResponseMode;
        private String lastUserTask;
        private String lastAiSummary;
    }

    @Data
    public static class LeanDiagnostic {
        private Integer line;
        private Integer column;
        private String severity;
        private String message;
    }

    @Data
    public static class PrecheckReport {
        private String conclusion;
        private String conclusionText;
        private String riskLevel;
        private List<PrecheckIssue> issues;
    }

    @Data
    public static class PrecheckIssue {
        private String id;
        private String severity;
        private String title;
        private String message;
        private Integer line;
        private Integer column;
        private String suggestion;
        private String source;
    }
}
