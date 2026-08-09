package org.scse.smartcontractsearchengine.controller;

import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.model.entity.Group;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;
import org.scse.smartcontractsearchengine.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class BrowseController {

    private final SearchService searchService;

    public BrowseController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseResult<List<Group>> getGroups() {
        List<Group> groups = searchService.getGroupList();
        return ResponseResult.success(groups);
    }

    @GetMapping("/{groupId}/contracts")
    public ResponseResult<List<ContractCardVo>> getGroupContracts(
            @PathVariable Long groupId
    ) {
        List<ContractCardVo> result = searchService.getContractsByGroup(groupId);
        return ResponseResult.success(result);
    }


}
