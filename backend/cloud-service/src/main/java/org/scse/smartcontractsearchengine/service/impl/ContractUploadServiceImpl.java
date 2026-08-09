package org.scse.smartcontractsearchengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.context.UserContext;
import org.scse.smartcontractsearchengine.mapper.SmartContractMapper;
import org.scse.smartcontractsearchengine.mapper.UserFavoriteMapper;
import org.scse.smartcontractsearchengine.model.dto.ContractUploadRequest;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;
import org.scse.smartcontractsearchengine.service.ContractUploadService;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ContractUploadServiceImpl implements ContractUploadService {

    private final SmartContractMapper smartContractMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SolidityAbiCompilerService solidityAbiCompilerService;

    public ContractUploadServiceImpl(SmartContractMapper smartContractMapper,
                                     UserFavoriteMapper userFavoriteMapper,
                                     JdbcTemplate jdbcTemplate,
                                     SolidityAbiCompilerService solidityAbiCompilerService) {
        this.smartContractMapper = smartContractMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.solidityAbiCompilerService = solidityAbiCompilerService;
    }

    @PostConstruct
    public void ensureUploaderColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE smart_contract ADD COLUMN IF NOT EXISTS uploader_id BIGINT");
            jdbcTemplate.execute("ALTER TABLE smart_contract ADD COLUMN IF NOT EXISTS abi_status VARCHAR(32)");
            jdbcTemplate.execute("ALTER TABLE smart_contract ADD COLUMN IF NOT EXISTS abi_error_message TEXT");
            jdbcTemplate.execute("ALTER TABLE smart_contract ADD COLUMN IF NOT EXISTS compiler_version VARCHAR(64)");
        } catch (Exception e) {
            log.warn("初始化用户上传字段失败，上传或 ABI 状态功能可能不可用", e);
        }
    }

    @Override
    public ContractCardVo uploadContract(ContractUploadRequest request) {
        Long userId = currentUserId();
        if (request == null || isBlank(request.getSourceCode())) {
            throw new RuntimeException("请先提供需要上传的合约源码");
        }

        String sourceCode = request.getSourceCode().trim();
        String contractName = firstNonBlank(
                request.getContractName(),
                detectContractName(sourceCode),
                "未命名合约"
        );

        ensureNotDuplicate(contractName, sourceCode);
        SolidityAbiCompilerService.AbiCompileResult abiResult =
                solidityAbiCompilerService.compile(sourceCode, contractName);
        if ("generated".equals(abiResult.getStatus())
                && !isBlank(abiResult.getContractName())
                && "未命名合约".equals(contractName)) {
            contractName = abiResult.getContractName();
        }

        SmartContract contract = new SmartContract();
        contract.setAddress(generatePseudoAddress(userId, contractName, sourceCode));
        contract.setContractName(contractName);
        contract.setSourceCode(sourceCode);
        contract.setAbi(firstNonBlank(abiResult.getAbi(), ""));
        contract.setAbiStatus(firstNonBlank(abiResult.getStatus(), "unavailable"));
        contract.setAbiErrorMessage(firstNonBlank(abiResult.getErrorMessage(), ""));
        contract.setCompilerVersion(firstNonBlank(abiResult.getCompilerVersion(), ""));
        contract.setBytecode("");
        contract.setCategory(firstNonBlank(request.getCategory(), "uploaded"));
        contract.setDescription(firstNonBlank(
                request.getDescription(),
                contractName + " 为用户通过智能预审后上传的候选合约。"
        ));
        contract.setIsDeleted(false);
        contract.setIsEffective(true);
        contract.setUploaderId(userId);

        smartContractMapper.insert(contract);
        return toCard(contract);
    }

    @Override
    public List<ContractCardVo> getMyUploadedContracts() {
        Long userId = currentUserId();
        List<SmartContract> contracts = smartContractMapper.selectList(
                new QueryWrapper<SmartContract>()
                        .eq("uploader_id", userId)
                        .eq("is_deleted", false)
                        .orderByDesc("id")
        );

        List<ContractCardVo> result = new ArrayList<>();
        for (SmartContract contract : contracts) {
            result.add(toCard(contract));
        }
        return result;
    }

    @Override
    public void deleteMyUploadedContract(Long contractId) {
        Long userId = currentUserId();
        if (contractId == null) {
            throw new RuntimeException("合约 ID 不能为空");
        }

        SmartContract contract = smartContractMapper.selectById(contractId);
        if (contract == null || Boolean.TRUE.equals(contract.getIsDeleted())) {
            throw new RuntimeException("未找到该上传合约");
        }
        if (!userId.equals(contract.getUploaderId())) {
            throw new RuntimeException("只能删除自己上传的合约");
        }

        userFavoriteMapper.deleteByContractId(contractId);
        int affected = smartContractMapper.physicallyDeleteUploadedContract(contractId, userId);
        if (affected <= 0) {
            throw new RuntimeException("删除上传合约失败");
        }
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("请先登录后再操作");
        }
        return userId;
    }

    private ContractCardVo toCard(SmartContract contract) {
        ContractCardVo vo = new ContractCardVo();
        BeanUtils.copyProperties(contract, vo);
        return vo;
    }

    private String detectContractName(String sourceCode) {
        Matcher matcher = Pattern
                .compile("\\b(?:abstract\\s+contract|contract|interface|library)\\s+([A-Za-z_][A-Za-z0-9_]*)")
                .matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void ensureNotDuplicate(String contractName, String sourceCode) {
        Long exactDuplicateId = smartContractMapper.findActiveIdBySourceCode(sourceCode);
        if (exactDuplicateId != null) {
            throw new RuntimeException("数据库中已存在相同源码的合约，不能重复上传");
        }

        String normalizedSource = normalizeSource(sourceCode);
        List<SmartContract> sameNameContracts = smartContractMapper.findActiveContractsByName(contractName);
        for (SmartContract existing : sameNameContracts) {
            if (normalizedSource.equals(normalizeSource(existing.getSourceCode()))) {
                throw new RuntimeException("数据库中已存在同名且源码一致的合约，不能重复上传");
            }
        }
    }

    private String normalizeSource(String sourceCode) {
        if (sourceCode == null) return "";
        return sourceCode.replaceAll("\\s+", "");
    }

    private String generatePseudoAddress(Long userId, String contractName, String sourceCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((userId + ":" + contractName + ":" + sourceCode + ":" + System.nanoTime())
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("0x");
            for (byte b : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
                if (builder.length() >= 42) break;
            }
            return builder.substring(0, 42);
        } catch (Exception e) {
            return "0x" + Long.toHexString(System.nanoTime()).repeat(4).substring(0, 40);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return "";
    }
}
