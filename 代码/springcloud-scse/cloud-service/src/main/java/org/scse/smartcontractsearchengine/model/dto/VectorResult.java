package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

// 用于接收向量搜索结果
@Data
public class VectorResult {
    private Long id;
    private Double distance;        // 余弦距离
    private Integer rank;
}