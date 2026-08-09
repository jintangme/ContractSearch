package org.scse.smartcontractsearchengine.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scse.smartcontractsearchengine.common.enums.LoginResult;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginVo {
    private LoginResult loginResult;
    private Long id;
    private String username;
    private String token;
    private String message;
    private String nickname;
    private String email;
    private String avatarType;
    private String avatarUrl;
    private String avatarPresetKey;
    private String avatarHistory;
}
