package org.scse.smartcontractsearchengine.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserVo {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String avatarType;

    private String avatarUrl;

    private String avatarPresetKey;

    private String avatarHistory;
}
