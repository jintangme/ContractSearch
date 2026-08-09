package org.scse.smartcontractsearchengine.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.scse.smartcontractsearchengine.common.enums.RegisterResult;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterVo {
    private RegisterResult registerResult;

    private String username;

    private String token;

    private String message;
}
