package org.scse.smartcontractsearchengine.model.dto;

import lombok.Data;

@Data
public class ProfileUpdateDto {

    private String username;

    private String nickname;

    private String email;

    private String password;

    private String avatarType;

    private String avatarPresetKey;

    private String avatarUrl;

    private String avatarImageData;
}
