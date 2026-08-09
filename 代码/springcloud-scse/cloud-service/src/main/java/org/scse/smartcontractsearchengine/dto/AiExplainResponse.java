package org.scse.smartcontractsearchengine.dto;

public class AiExplainResponse {

    private String contractName;
    private String summary;
    private String contractType;
    private String complexityLevel;
    private String overallRiskLevel;
    private String corePurpose;
    private RiskScores riskScores;
    private String provider;
    private String requestedModel;
    private String modelUsed;

    public AiExplainResponse() {
    }

    public AiExplainResponse(String contractName, String summary) {
        this.contractName = contractName;
        this.summary = summary;
    }

    public String getContractName() {
        return contractName;
    }

    public void setContractName(String contractName) {
        this.contractName = contractName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContractType() {
        return contractType;
    }

    public void setContractType(String contractType) {
        this.contractType = contractType;
    }

    public String getComplexityLevel() {
        return complexityLevel;
    }

    public void setComplexityLevel(String complexityLevel) {
        this.complexityLevel = complexityLevel;
    }

    public String getOverallRiskLevel() {
        return overallRiskLevel;
    }

    public void setOverallRiskLevel(String overallRiskLevel) {
        this.overallRiskLevel = overallRiskLevel;
    }

    public String getCorePurpose() {
        return corePurpose;
    }

    public void setCorePurpose(String corePurpose) {
        this.corePurpose = corePurpose;
    }

    public RiskScores getRiskScores() {
        return riskScores;
    }

    public void setRiskScores(RiskScores riskScores) {
        this.riskScores = riskScores;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRequestedModel() {
        return requestedModel;
    }

    public void setRequestedModel(String requestedModel) {
        this.requestedModel = requestedModel;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public static class RiskScores {
        private Integer permissionRisk;
        private Integer assetFlowRisk;
        private Integer complexityRisk;

        public RiskScores() {
        }

        public RiskScores(Integer permissionRisk, Integer assetFlowRisk, Integer complexityRisk) {
            this.permissionRisk = permissionRisk;
            this.assetFlowRisk = assetFlowRisk;
            this.complexityRisk = complexityRisk;
        }

        public Integer getPermissionRisk() {
            return permissionRisk;
        }

        public void setPermissionRisk(Integer permissionRisk) {
            this.permissionRisk = permissionRisk;
        }

        public Integer getAssetFlowRisk() {
            return assetFlowRisk;
        }

        public void setAssetFlowRisk(Integer assetFlowRisk) {
            this.assetFlowRisk = assetFlowRisk;
        }

        public Integer getComplexityRisk() {
            return complexityRisk;
        }

        public void setComplexityRisk(Integer complexityRisk) {
            this.complexityRisk = complexityRisk;
        }
    }
}
