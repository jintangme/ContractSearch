package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

// 融合后的结果
@Data
public class FusedResult {

    private Long id;

    private Double rrfScore = 0.0;

    private Double bm25Score;

    private Double vectorDistance;

    private Integer bm25Rank;

    private Integer vectorRank;

}
