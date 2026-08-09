package org.scse.smartcontractsearchengine.service.impl;

import org.scse.smartcontractsearchengine.common.enums.LoginResult;
import org.scse.smartcontractsearchengine.common.enums.RegisterResult;
import org.scse.smartcontractsearchengine.mapper.UserMapper;
import org.scse.smartcontractsearchengine.model.dto.LoginDto;
import org.scse.smartcontractsearchengine.model.dto.RegisterDto;
import org.scse.smartcontractsearchengine.model.entity.User;
import org.scse.smartcontractsearchengine.model.vo.LoginVo;
import org.scse.smartcontractsearchengine.model.vo.RegisterVo;
import org.scse.smartcontractsearchengine.service.AuthService;
import org.scse.smartcontractsearchengine.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterVo register(RegisterDto registerDto) {
        if(userMapper.findByUsername(registerDto.getUsername()) != null){
            return new RegisterVo(RegisterResult.USERNAME_EXIST, null, null, "用户名已存在");
        }

        User user = User.builder()
                .username(registerDto.getUsername())
                .password(passwordEncoder.encode(registerDto.getPassword()))
                .nickname(registerDto.getNickname())
                .email(registerDto.getEmail())
                .isDeleted(false)
                .build();

        userMapper.insert(user);

        return RegisterVo.builder()
                .registerResult(RegisterResult.SUCCESS)
                .username(user.getUsername())
                .token(jwtUtil.generateToken(user))
                .message("注册成功")
                .build();
    }

    @Override
    public LoginVo login(LoginDto loginDto) {
        User user = userMapper.findByUsername(loginDto.getUsername());
        if(user != null && passwordEncoder.matches(loginDto.getPassword(), user.getPassword())){
            return LoginVo.builder()
                    .loginResult(LoginResult.SUCCESS)
                    .id(user.getId())
                    .username(user.getUsername())
                    .token(jwtUtil.generateToken(user))
                    .message("登录成功")
                    .nickname(user.getNickname())
                    .email(user.getEmail())
                    .avatarType(user.getAvatarType())
                    .avatarUrl(user.getAvatarUrl())
                    .avatarPresetKey(user.getAvatarPresetKey())
                    .avatarHistory(user.getAvatarHistory())
                    .build();
        }
        return LoginVo.builder()
                .loginResult(LoginResult.ERROR_USERNAME_OR_PASSWORD)
                .message("用户名或密码错误")
                .build();
    }
}
