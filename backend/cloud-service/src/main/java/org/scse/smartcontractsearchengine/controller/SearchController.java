package org.scse.smartcontractsearchengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.exception.EmbeddingException;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;
import org.scse.smartcontractsearchengine.model.dto.SmartContractSearchInputDto;
import org.scse.smartcontractsearchengine.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/search")
@Tag(name = "搜索controller", description = "搜索引擎相关功能")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @PostMapping
    @Operation(summary = "搜索", description = "输入描述返回搜索结果列表")
    public ResponseResult<List<ContractCardVo>> search(@RequestBody SmartContractSearchInputDto dto) {
        log.info("搜索请求：{}", dto);
        try {
            List<ContractCardVo> searchResult = searchService.search(dto);
            if (searchResult == null) return ResponseResult.fail("请求参数错误");
            return ResponseResult.success("查询成功！", searchResult);
        } catch (EmbeddingException e) {
            log.error("嵌入向量生成失败：{}", e.getMessage());
            return ResponseResult.fail("嵌入服务不可用，请稍后重试");
        } catch (IllegalArgumentException e) {
            log.error("参数错误：{}", e.getMessage());
            return ResponseResult.fail("请求参数错误：" + e.getMessage());
        } catch (Exception e) {
            log.error("查询失败！错误：", e);
            return ResponseResult.fail("查询失败，搜索服务异常！");
        }
    }

//    @PostMapping("/inFavorite")
//    @Operation(summary = "在收藏中搜索", description = "输入描述返回搜索结果列表")
//    public ResponseResult<SmartContractSearchReturnVo> searchInFavorite(@RequestBody SmartContractSearchInputDto dto) {
//        log.info("搜索请求：{}", dto);
//        Long userId = UserContext.getUserId();
//        if (userId == null) return ResponseResult.fail("请先登录！");
//        try {
//            SmartContractSearchReturnVo searchResult = searchService.searchInFavorite(dto, userId);
//            if (searchResult == null) return ResponseResult.fail("请求参数错误");
//            return ResponseResult.success("查询成功！", searchResult);
//        } catch (EmbeddingException e) {
//            log.error("嵌入向量生成失败：{}", e.getMessage());
//            return ResponseResult.fail("嵌入服务不可用，请稍后重试");
//        } catch (IllegalArgumentException e) {
//            log.error("参数错误：{}", e.getMessage());
//            return ResponseResult.fail("请求参数错误：" + e.getMessage());
//        } catch (Exception e) {
//            log.error("查询失败！错误：", e);
//            return ResponseResult.fail("查询失败，搜索服务异常！");
//        }
//    }

}
