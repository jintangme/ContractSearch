package org.scse.smartcontractsearchengine.model.vo;

import lombok.Data;

@Data
public class FavoriteFolderVo {

    private Long id;

    private String name;

    private Boolean isDefault;

    private Integer contractCount;
}
