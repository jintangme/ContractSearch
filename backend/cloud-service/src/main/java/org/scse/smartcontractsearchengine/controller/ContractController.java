package org.scse.smartcontractsearchengine.controller;

import org.scse.smartcontractsearchengine.common.enums.HttpStatusEnum;
import org.scse.smartcontractsearchengine.context.UserContext;
import org.scse.smartcontractsearchengine.dto.AiExplainResponse;
import org.scse.smartcontractsearchengine.exception.FavoriteException;
import org.scse.smartcontractsearchengine.model.dto.FavoriteFolderRequest;
import org.scse.smartcontractsearchengine.model.dto.FavoriteRequest;
import org.scse.smartcontractsearchengine.model.dto.ContractUploadRequest;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;
import org.scse.smartcontractsearchengine.model.vo.ContractDetailVo;
import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.model.vo.FavoriteFolderVo;
import org.scse.smartcontractsearchengine.service.AiExplainService;
import org.scse.smartcontractsearchengine.service.ContractUploadService;
import org.scse.smartcontractsearchengine.service.FavoriteService;
import org.scse.smartcontractsearchengine.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final FavoriteService favoriteService;
    private final SearchService searchService;
    private final AiExplainService aiExplainService;
    private final ContractUploadService contractUploadService;

    public ContractController(FavoriteService favoriteService,
                              SearchService searchService,
                              AiExplainService aiExplainService,
                              ContractUploadService contractUploadService) {
        this.favoriteService = favoriteService;
        this.searchService = searchService;
        this.aiExplainService = aiExplainService;
        this.contractUploadService = contractUploadService;
    }

    @GetMapping
    public ResponseResult<List<ContractCardVo>> getAllContracts() {
        return ResponseResult.success(searchService.getAllContracts());
    }

    @PostMapping("/uploads")
    public ResponseResult<ContractCardVo> uploadReviewedContract(@RequestBody ContractUploadRequest request) {
        try {
            return ResponseResult.success(contractUploadService.uploadContract(request));
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @GetMapping("/uploads/mine")
    public ResponseResult<List<ContractCardVo>> getMyUploadedContracts() {
        try {
            return ResponseResult.success(contractUploadService.getMyUploadedContracts());
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @DeleteMapping("/uploads/{id}")
    public ResponseResult<Void> deleteMyUploadedContract(@PathVariable Long id) {
        try {
            contractUploadService.deleteMyUploadedContract(id);
            return ResponseResult.success();
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseResult<ContractDetailVo> getContractDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean regenerateAbi) {
        ContractDetailVo contract = searchService.getContractById(id, regenerateAbi);
        if (contract == null) {
            return ResponseResult.fail(HttpStatusEnum.NOT_FOUND.getCode(), "未找到该合约");
        }
        return ResponseResult.success(contract);
    }

    @GetMapping("/{id}/ai-explain")
    public ResponseResult<AiExplainResponse> getAiExplain(
            @PathVariable Long id,
            @RequestParam(defaultValue = "deep") String mode,
            @RequestParam(required = false) String model
    ) {
        try {
            AiExplainResponse response = aiExplainService.explainContract(id, mode, model);
            return ResponseResult.success(response);
        } catch (IllegalArgumentException e) {
            return ResponseResult.fail(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        } catch (Exception e) {
            return ResponseResult.fail("AI 解释生成失败");
        }
    }

    @PutMapping("/{id}/favorite")
    public ResponseResult<Void> favoriteContract(@PathVariable Long id,
                                                 @RequestBody(required = false) FavoriteRequest request) {
        Long userId = UserContext.getUserId();
        try {
            favoriteService.addFavorite(
                    userId,
                    id,
                    request == null ? null : request.getFolderId(),
                    request == null ? null : request.getNewFolderName()
            );
        } catch (FavoriteException e) {
            return ResponseResult.fail(e.getMessage());
        }
        return ResponseResult.success();
    }

    @DeleteMapping("/{id}/favorite")
    public ResponseResult<Void> unfavoriteContract(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        try {
            favoriteService.removeFavorite(userId, id);
        } catch (FavoriteException e) {
            return ResponseResult.fail(e.getMessage());
        }
        return ResponseResult.success();
    }

    @GetMapping("/favorites")
    public ResponseResult<List<ContractCardVo>> getFavoriteContracts() {
        Long userId = UserContext.getUserId();
        return ResponseResult.success(favoriteService.getFavoriteContracts(userId));
    }

    @GetMapping("/favorite-folders")
    public ResponseResult<List<FavoriteFolderVo>> getFavoriteFolders() {
        Long userId = UserContext.getUserId();
        return ResponseResult.success(favoriteService.getFavoriteFolders(userId));
    }

    @PostMapping("/favorite-folders")
    public ResponseResult<FavoriteFolderVo> createFavoriteFolder(@RequestBody FavoriteFolderRequest request) {
        Long userId = UserContext.getUserId();
        try {
            return ResponseResult.success(favoriteService.createFavoriteFolder(userId, request == null ? null : request.getName()));
        } catch (FavoriteException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @PutMapping("/favorite-folders/{folderId}")
    public ResponseResult<FavoriteFolderVo> renameFavoriteFolder(@PathVariable Long folderId,
                                                                 @RequestBody FavoriteFolderRequest request) {
        Long userId = UserContext.getUserId();
        try {
            return ResponseResult.success(favoriteService.renameFavoriteFolder(userId, folderId, request == null ? null : request.getName()));
        } catch (FavoriteException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @DeleteMapping("/favorite-folders/{folderId}")
    public ResponseResult<Void> deleteFavoriteFolder(@PathVariable Long folderId) {
        Long userId = UserContext.getUserId();
        try {
            favoriteService.deleteFavoriteFolder(userId, folderId);
            return ResponseResult.success();
        } catch (FavoriteException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @GetMapping("/favorite-folders/{folderId}/contracts")
    public ResponseResult<List<ContractCardVo>> getFavoriteContractsByFolder(@PathVariable Long folderId) {
        Long userId = UserContext.getUserId();
        try {
            return ResponseResult.success(favoriteService.getFavoriteContractsByFolder(userId, folderId));
        } catch (FavoriteException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }
}
