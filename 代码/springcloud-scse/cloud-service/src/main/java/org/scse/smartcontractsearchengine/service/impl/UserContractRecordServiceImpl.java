package org.scse.smartcontractsearchengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.scse.smartcontractsearchengine.mapper.UserContractRecordMapper;
import org.scse.smartcontractsearchengine.model.dto.UserContractRecordRequest;
import org.scse.smartcontractsearchengine.model.entity.UserContractRecord;
import org.scse.smartcontractsearchengine.model.vo.UserContractRecordVo;
import org.scse.smartcontractsearchengine.service.UserContractRecordService;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserContractRecordServiceImpl implements UserContractRecordService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PENDING_REVIEW = "pending_review";

    private final UserContractRecordMapper mapper;
    private final ObjectMapper objectMapper;

    public UserContractRecordServiceImpl(UserContractRecordMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserContractRecordVo create(Long userId, String status, UserContractRecordRequest request) {
        requireUser(userId);
        requireStatus(status);
        if (request == null || !StringUtils.hasText(request.getSourceCode())) {
            throw new RuntimeException("源码不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        UserContractRecord record = UserContractRecord.builder()
                .userId(userId)
                .contractName(StringUtils.hasText(request.getContractName()) ? request.getContractName().trim() : "未命名合约")
                .sourceCode(request.getSourceCode())
                .precheckConclusion(nullToEmpty(request.getPrecheckConclusion()))
                .riskLevel(nullToEmpty(request.getRiskLevel()))
                .issueCount(request.getIssueCount() == null ? 0 : Math.max(0, request.getIssueCount()))
                .issues(writeIssues(request.getIssues()))
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            mapper.insert(record);
            return toVo(record);
        } catch (RuntimeException e) {
            throw new RuntimeException("保存失败");
        }
    }

    @Override
    public List<UserContractRecordVo> list(Long userId, String status) {
        requireUser(userId);
        requireStatus(status);
        try {
            return mapper.selectList(new LambdaQueryWrapper<UserContractRecord>()
                            .eq(UserContractRecord::getUserId, userId)
                            .eq(UserContractRecord::getStatus, status)
                            .orderByDesc(UserContractRecord::getUpdatedAt)
                            .orderByDesc(UserContractRecord::getId))
                    .stream()
                    .map(this::toVo)
                    .toList();
        } catch (RuntimeException e) {
            throw new RuntimeException("数据加载失败，请稍后重试");
        }
    }

    @Override
    public UserContractRecordVo get(Long userId, Long id, String status) {
        UserContractRecord record = requireOwnedRecord(userId, id, status);
        return toVo(record);
    }

    @Override
    public void delete(Long userId, Long id, String status) {
        requireOwnedRecord(userId, id, status);
        try {
            mapper.deleteById(id);
        } catch (RuntimeException e) {
            throw new RuntimeException("操作失败");
        }
    }

    private UserContractRecord requireOwnedRecord(Long userId, Long id, String status) {
        requireUser(userId);
        requireStatus(status);
        if (id == null) {
            throw new RuntimeException("记录不存在");
        }
        UserContractRecord record;
        try {
            record = mapper.selectOne(new LambdaQueryWrapper<UserContractRecord>()
                    .eq(UserContractRecord::getId, id)
                    .eq(UserContractRecord::getUserId, userId)
                    .eq(UserContractRecord::getStatus, status)
                    .last("LIMIT 1"));
        } catch (RuntimeException e) {
            throw new RuntimeException("数据加载失败，请稍后重试");
        }
        if (record == null) {
            throw new RuntimeException("记录不存在");
        }
        return record;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
    }

    private void requireStatus(String status) {
        if (!STATUS_DRAFT.equals(status) && !STATUS_PENDING_REVIEW.equals(status)) {
            throw new RuntimeException("状态不合法");
        }
    }

    private String writeIssues(Object issues) {
        if (issues == null) return "[]";
        if (issues instanceof String value) return value;
        try {
            return objectMapper.writeValueAsString(issues);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private Object readIssues(String issues) {
        if (!StringUtils.hasText(issues)) return List.of();
        try {
            return objectMapper.readValue(issues, Object.class);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private UserContractRecordVo toVo(UserContractRecord record) {
        return UserContractRecordVo.builder()
                .id(record.getId())
                .contractName(record.getContractName())
                .sourceCode(record.getSourceCode())
                .precheckConclusion(record.getPrecheckConclusion())
                .riskLevel(record.getRiskLevel())
                .issueCount(record.getIssueCount())
                .issues(readIssues(record.getIssues()))
                .status(record.getStatus())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
