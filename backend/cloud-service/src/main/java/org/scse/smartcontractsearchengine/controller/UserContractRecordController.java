package org.scse.smartcontractsearchengine.controller;

import org.springframework.web.bind.annotation.*;
import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.context.UserContext;
import org.scse.smartcontractsearchengine.model.dto.UserContractRecordRequest;
import org.scse.smartcontractsearchengine.model.vo.UserContractRecordVo;
import org.scse.smartcontractsearchengine.service.UserContractRecordService;
import org.scse.smartcontractsearchengine.service.impl.UserContractRecordServiceImpl;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class UserContractRecordController {

    private final UserContractRecordService service;

    public UserContractRecordController(UserContractRecordService service) {
        this.service = service;
    }

    @PostMapping("/drafts")
    public ResponseResult<UserContractRecordVo> createDraft(@RequestBody UserContractRecordRequest request) {
        try {
            return ResponseResult.success(service.create(UserContext.getUserId(), UserContractRecordServiceImpl.STATUS_DRAFT, request));
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @GetMapping("/drafts")
    public ResponseResult<List<UserContractRecordVo>> listDrafts() {
        try {
            return ResponseResult.success(service.list(UserContext.getUserId(), UserContractRecordServiceImpl.STATUS_DRAFT));
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @GetMapping("/drafts/{id}")
    public ResponseResult<UserContractRecordVo> getDraft(@PathVariable Long id) {
        try {
            return ResponseResult.success(service.get(UserContext.getUserId(), id, UserContractRecordServiceImpl.STATUS_DRAFT));
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @DeleteMapping("/drafts/{id}")
    public ResponseResult<Void> deleteDraft(@PathVariable Long id) {
        try {
            service.delete(UserContext.getUserId(), id, UserContractRecordServiceImpl.STATUS_DRAFT);
            return ResponseResult.success();
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @PostMapping("/submissions")
    public ResponseResult<UserContractRecordVo> createSubmission(@RequestBody UserContractRecordRequest request) {
        try {
            return ResponseResult.success(service.create(UserContext.getUserId(), UserContractRecordServiceImpl.STATUS_PENDING_REVIEW, request));
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @GetMapping("/submissions")
    public ResponseResult<List<UserContractRecordVo>> listSubmissions() {
        try {
            return ResponseResult.success(service.list(UserContext.getUserId(), UserContractRecordServiceImpl.STATUS_PENDING_REVIEW));
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @GetMapping("/submissions/{id}")
    public ResponseResult<UserContractRecordVo> getSubmission(@PathVariable Long id) {
        try {
            return ResponseResult.success(service.get(UserContext.getUserId(), id, UserContractRecordServiceImpl.STATUS_PENDING_REVIEW));
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @DeleteMapping("/submissions/{id}")
    public ResponseResult<Void> deleteSubmission(@PathVariable Long id) {
        try {
            service.delete(UserContext.getUserId(), id, UserContractRecordServiceImpl.STATUS_PENDING_REVIEW);
            return ResponseResult.success();
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }
}
