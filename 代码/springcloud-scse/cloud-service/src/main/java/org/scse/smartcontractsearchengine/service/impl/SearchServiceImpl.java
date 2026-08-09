package org.scse.smartcontractsearchengine.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.common.enums.OpenAiClientType;
import org.scse.smartcontractsearchengine.context.UserContext;
import org.scse.smartcontractsearchengine.mapper.GroupContractMapper;
import org.scse.smartcontractsearchengine.mapper.GroupMapper;
import org.scse.smartcontractsearchengine.mapper.SmartContractMapper;
import org.scse.smartcontractsearchengine.model.dto.*;
import org.scse.smartcontractsearchengine.model.entity.Group;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;
import org.scse.smartcontractsearchengine.model.vo.SmartContractSearchReturnVo;
import org.scse.smartcontractsearchengine.service.EmbeddingService;
import org.scse.smartcontractsearchengine.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.scse.smartcontractsearchengine.model.vo.ContractDetailVo;
import org.scse.smartcontractsearchengine.mapper.UserFavoriteMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

    /**
     * 搜索智能合约
     *
     * @param dto
     * @return
     */

    @Autowired
    private SmartContractMapper smartContractMapper;
    @Autowired
    private GroupMapper groupMapper;
    @Autowired
    private GroupContractMapper groupContractMapper;
    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private SolidityAbiCompilerService solidityAbiCompilerService;

    // RRF 常数 k（通常为 60）
    private static final int RRF_K = 60;
    // 每个通道返回的数量（例如 200）
    private static final int CHANNEL_LIMIT = 200;
    // 最终返回数量
    private static final int FINAL_LIMIT = 100;
    // 文本检索最低质量门槛：保留正分结果，并剔除相对头部结果太弱的尾部候选
    private static final double MIN_BM25_SCORE = 0.0001;
    private static final double MIN_BM25_RELATIVE_RATIO = 0.08;
    // description_embedding 使用 cosine distance，越小越相关
    private static final double MAX_VECTOR_DISTANCE = 0.68;
    private static final double MODERATE_VECTOR_DISTANCE = 0.72;
    private static final double SOFT_VECTOR_DISTANCE = 0.74;
    private static final int VECTOR_ONLY_RANK_LIMIT = 12;
    private static final double MIN_RRF_SCORE = 1.0 / (RRF_K + FINAL_LIMIT);

    @Override
    public List<ContractCardVo> search(SmartContractSearchInputDto dto) {
        if (dto == null || !isMeaningfulQuery(dto.getInput())) {
            return new ArrayList<>();
        }

        List<Bm25Result> bm25Results = null;
        List<VectorResult> vectorResults = null;
        String searchMode = dto.getSearchMode();

        if ("semantic".equals(searchMode)) {
            float[] inputEmbedding = embeddingService.getEmbedding(dto.getInput(), OpenAiClientType.REMOTE);
            bm25Results = smartContractMapper.descriptionTextSearch(dto.getInput(), CHANNEL_LIMIT);
            vectorResults = smartContractMapper.descriptionVectorSearch(inputEmbedding, CHANNEL_LIMIT);
        }
        else if ("code".equals(searchMode)){
            bm25Results = smartContractMapper.codeTextSearch(dto.getInput(), CHANNEL_LIMIT);
            vectorResults = new ArrayList<>();
        }
        else {
            return null;
        }

        bm25Results = filterBm25Results(bm25Results);
        vectorResults = "semantic".equals(searchMode)
                ? filterVectorResults(vectorResults)
                : new ArrayList<>();

        assignRanks(bm25Results, vectorResults);
        List<FusedResult> fused = filterFusedResults(fuseResults(bm25Results, vectorResults), searchMode);

        List<SearchResultDto> result = getFullResult(fused, FINAL_LIMIT);
        List<ContractCardVo> resultContracts = new ArrayList<>();
        for (SearchResultDto r : result) {
            ContractCardVo contract = new ContractCardVo();
            BeanUtils.copyProperties(r, contract);
            resultContracts.add(contract);
        }
        return resultContracts;
    }

//    @Override
//    public SmartContractSearchReturnVo searchInFavorite(SmartContractSearchInputDto dto, Long userId) {
//
//        float[] inputEmbedding = embeddingService.getEmbedding(dto.getInput());
//        List<Bm25Result> bm25Results = null;
//        List<VectorResult> vectorResults = null;
//
//        if (dto.getSearchMode().equals("DESCRIPTION_SEARCH")) {
//            bm25Results = new ArrayList<>();
//            vectorResults = smartContractMapper.descriptionVectorSearchInFav(inputEmbedding, CHANNEL_LIMIT, userId);
//        }
//        else if (dto.getSearchMode().equals("CODE_SEARCH")){
//            bm25Results = smartContractMapper.codeTextSearchInFav(dto.getInput(), CHANNEL_LIMIT, userId);
//            vectorResults = smartContractMapper.codeVectorSearchInFav(inputEmbedding, CHANNEL_LIMIT, userId);
//        }
//        else {
//            return null;
//        }
//
//        assignRanks(bm25Results, vectorResults);
//        List<FusedResult> fused = fuseResults(bm25Results, vectorResults);
//
//        List<SearchResultDto> result = getFullResult(fused, FINAL_LIMIT);
//        return SmartContractSearchReturnVo.builder()
//                .total(result.size())
//                .results(result)
//                .build();
//    }

    @Override
    public List<ContractCardVo> getAllContracts() {
        List<SmartContract> records = smartContractMapper.selectList(null);
        List<ContractCardVo> result = new ArrayList<>();
        for (SmartContract sc : records) {
            ContractCardVo vo = new ContractCardVo();
            BeanUtils.copyProperties(sc, vo);
            result.add(vo);
        }
        return result;
    }

    @Override
    public ContractDetailVo getContractById(Long id) {
        return getContractById(id, true);
    }

    @Override
    public ContractDetailVo getContractById(Long id, boolean regenerateAbi) {
        SmartContract contract = smartContractMapper.selectById(id);

        if (contract == null) {
            log.warn("未找到合约，id={}", id);
            return null;
        }

        if (regenerateAbi) {
            regenerateAbiIfMissing(contract);
        }

        ContractDetailVo detail = new ContractDetailVo();
        BeanUtils.copyProperties(contract, detail);

        log.info("UserId: {}", UserContext.getUserId());
        if (UserContext.getUserId() != null) {
            detail.setIsFavorited(
                    userFavoriteMapper.findByUserIdAndContractId(UserContext.getUserId(), id) != null
            );
        }

        return detail;
    }

    private void regenerateAbiIfMissing(SmartContract contract) {
        if (contract == null || !isBlank(contract.getAbi()) || isBlank(contract.getSourceCode())) {
            return;
        }
        String status = contract.getAbiStatus();
        if (!isBlank(status) && !"failed".equals(status) && !"unavailable".equals(status)) {
            return;
        }

        SolidityAbiCompilerService.AbiCompileResult abiResult =
                solidityAbiCompilerService.compile(contract.getSourceCode(), contract.getContractName());
        contract.setAbi(abiResult.getAbi());
        contract.setAbiStatus(abiResult.getStatus());
        contract.setAbiErrorMessage(abiResult.getErrorMessage());
        contract.setCompilerVersion(abiResult.getCompilerVersion());
        smartContractMapper.updateById(contract);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public List<Group> getGroupList() {
        return groupMapper.selectList(null);
    }

    @Override
    public List<ContractCardVo> getContractsByGroup(Long groupId) {
        return groupContractMapper.findContractsByGroupId(groupId);
    }

    /**
     * 为两个结果集分配排名（1-based）
     */
    private void assignRanks(List<Bm25Result> bm25, List<VectorResult> vector) {
        // BM25 已按 score 降序，直接分配排名
        int rank = 1;
        for (Bm25Result r : bm25) {
            r.setRank(rank++);
        }
        // 向量已按 distance 升序，分配排名
        rank = 1;
        for (VectorResult r : vector) {
            r.setRank(rank++);
        }
    }

    private boolean isMeaningfulQuery(String input) {
        if (input == null) {
            return false;
        }
        String normalized = input.trim();
        if (normalized.length() < 2) {
            return false;
        }
        if (!normalized.matches(".*[\\p{IsHan}A-Za-z0-9].*")) {
            return false;
        }
        if (normalized.matches("(?i)^([a-z0-9\\p{IsHan}])\\1{3,}$")) {
            return false;
        }

        String compact = normalized.replaceAll("[^A-Za-z0-9\\p{IsHan}]", "");
        if (compact.length() >= 8 && compact.matches("[A-Za-z]+")) {
            long vowelCount = compact.toLowerCase().chars()
                    .filter(ch -> "aeiou".indexOf(ch) >= 0)
                    .count();
            if ((double) vowelCount / compact.length() < 0.25) {
                return false;
            }
        }
        return true;
    }

    private List<Bm25Result> filterBm25Results(List<Bm25Result> results) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }

        double topScore = results.stream()
                .map(Bm25Result::getScore)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(0.0);

        if (topScore <= MIN_BM25_SCORE) {
            return new ArrayList<>();
        }

        double relativeThreshold = Math.max(MIN_BM25_SCORE, topScore * MIN_BM25_RELATIVE_RATIO);
        return results.stream()
                .filter(result -> result.getScore() != null && result.getScore() >= relativeThreshold)
                .collect(Collectors.toList());
    }

    private List<VectorResult> filterVectorResults(List<VectorResult> results) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }

        return results.stream()
                .filter(result -> result.getDistance() != null && result.getDistance() <= SOFT_VECTOR_DISTANCE)
                .collect(Collectors.toList());
    }

    private List<FusedResult> filterFusedResults(List<FusedResult> results, String searchMode) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }

        return results.stream()
                .filter(result -> isQualifiedResult(result, searchMode))
                .collect(Collectors.toList());
    }

    private boolean isQualifiedResult(FusedResult result, String searchMode) {
        boolean hasTextEvidence = result.getBm25Score() != null && result.getBm25Score() >= MIN_BM25_SCORE;
        boolean hasStrongVectorEvidence = result.getVectorDistance() != null && result.getVectorDistance() <= MAX_VECTOR_DISTANCE;
        boolean hasModerateVectorEvidence = result.getVectorDistance() != null && result.getVectorDistance() <= MODERATE_VECTOR_DISTANCE;
        boolean hasSoftVectorEvidence = result.getVectorDistance() != null && result.getVectorDistance() <= SOFT_VECTOR_DISTANCE;

        if ("code".equals(searchMode)) {
            return hasTextEvidence && result.getRrfScore() >= MIN_RRF_SCORE;
        }
        return hasTextEvidence
                || hasStrongVectorEvidence
                || (hasSoftVectorEvidence && result.getBm25Rank() != null)
                || (hasModerateVectorEvidence
                        && result.getVectorRank() != null
                        && result.getVectorRank() <= VECTOR_ONLY_RANK_LIMIT);
    }

    /**
     * 执行 RRF 融合，并返回按 RRF 分数降序排序的结果
     */
    private List<FusedResult> fuseResults(List<Bm25Result> bm25, List<VectorResult> vector) {
        Map<Long, FusedResult> map = new HashMap<>();

        // 处理 BM25 结果
        for (Bm25Result b : bm25) {
            FusedResult fr = map.computeIfAbsent(b.getId(), k -> new FusedResult());
            fr.setId(b.getId());
            fr.setBm25Score(b.getScore());
            fr.setBm25Rank(b.getRank());
            double score = 1.0 / (RRF_K + b.getRank());
            fr.setRrfScore(fr.getRrfScore() + score);
        }

        // 处理向量结果
        for (VectorResult v : vector) {
            FusedResult fr = map.computeIfAbsent(v.getId(), k -> new FusedResult());
            fr.setId(v.getId());
            fr.setVectorDistance(v.getDistance());
            fr.setVectorRank(v.getRank());
            double score = 1.0 / (RRF_K + v.getRank());
            fr.setRrfScore(fr.getRrfScore() + score);
        }

        // 按 RRF 分数降序排序并返回
        return map.values().stream()
                .sorted((a, b) -> Double.compare(b.getRrfScore(), a.getRrfScore()))
                .collect(Collectors.toList());
    }

    /**
     * 根据已排序的融合结果，获取完整的合约记录并组装成 DTO（保持 RRF 排序）
     */
    private List<SearchResultDto> getFullResult(List<FusedResult> fused, int limit) {
        if (fused == null || fused.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }

        // 从 fused 中提取 ID 列表（已按 RRF 分数降序排序），只取前 limit 个
        List<Long> topIds = fused.stream()
                .limit(limit)
                .map(FusedResult::getId)
                .collect(Collectors.toList());

        if (topIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量获取完整合约记录
        List<SmartContract> fullRecords = smartContractMapper.selectBatchIds(topIds);

        // 构建 ID 到合约的映射，方便快速查找
        Map<Long, SmartContract> recordMap = fullRecords.stream()
                .collect(Collectors.toMap(SmartContract::getId, Function.identity()));

        // 按 fused 顺序组装 DTO（保持 RRF 排序）
        List<SearchResultDto> result = new ArrayList<>();
        for (FusedResult fr : fused) {
            if (result.size() >= limit) {
                break;
            }
            SmartContract sc = recordMap.get(fr.getId());
            if (sc != null) {
                SearchResultDto searchResultDto = new SearchResultDto();
                BeanUtils.copyProperties(sc, searchResultDto);
                // 直接使用 fr 的 rrfScore
                searchResultDto.setRrfScore(fr.getRrfScore());
                result.add(searchResultDto);
            }
        }

        return result;
    }

}
