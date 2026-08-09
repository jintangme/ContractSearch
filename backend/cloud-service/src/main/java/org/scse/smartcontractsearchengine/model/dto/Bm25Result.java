package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

// 用于接收 BM25 搜索结果
@Data
public class Bm25Result {
    private Long id;
    private Double score;          // BM25 原始分数
    private Integer rank;           // 排名（可选，可直接用 score 排序）
}
