package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.dto.LoginDto;
import org.scse.smartcontractsearchengine.model.dto.RegisterDto;
import org.scse.smartcontractsearchengine.model.vo.LoginVo;
import org.scse.smartcontractsearchengine.model.vo.RegisterVo;

public interface AuthService {
    RegisterVo register(RegisterDto registerDto);
    LoginVo login(LoginDto loginDto);
}
