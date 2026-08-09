package org.scse.smartcontractsearchengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.scse.smartcontractsearchengine.common.enums.LoginResult;
import org.scse.smartcontractsearchengine.common.enums.RegisterResult;
import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.model.dto.LoginDto;
import org.scse.smartcontractsearchengine.model.dto.RegisterDto;
import org.scse.smartcontractsearchengine.model.vo.LoginVo;
import org.scse.smartcontractsearchengine.model.vo.RegisterVo;
import org.scse.smartcontractsearchengine.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseResult<LoginVo> login(@RequestBody LoginDto loginDto) {
        System.out.println(loginDto);
        LoginVo loginVo = authService.login(loginDto);
        System.out.println(loginVo);
        if(loginVo.getLoginResult() == LoginResult.SUCCESS){
            return ResponseResult.success("登录成功",loginVo);
        }
        return ResponseResult.fail(loginVo.getMessage());
    }

    @PostMapping("/logout")
    public ResponseResult<String> logout() {
        return ResponseResult.success("登出成功");
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册，传入参数：username 最大值32, password, nickname最大值32, email最大值64,均不可为空")
    public ResponseResult<RegisterVo> register(@RequestBody RegisterDto userRegisterDto){
        System.out.println(userRegisterDto);
        if(userRegisterDto.getUsername().isEmpty()){
            return ResponseResult.fail("用户名不能为空");
        }
        if(userRegisterDto.getUsername().length() > 32){
            return ResponseResult.fail("用户名长度不能超过32");
        }
        if(userRegisterDto.getPassword().isEmpty()){
            return ResponseResult.fail("密码不能为空");
        }
        if(userRegisterDto.getNickname().isEmpty()){
            return ResponseResult.fail("昵称不能为空");
        }
        if(userRegisterDto.getNickname().length() > 32){
            return ResponseResult.fail("昵称长度不能超过32");
        }
        if(userRegisterDto.getEmail().isEmpty()){
            return ResponseResult.fail("邮箱不能为空");
        }
        if(userRegisterDto.getEmail().length() > 64){
            return ResponseResult.fail("邮箱长度不能超过64");
        }
        if(!userRegisterDto.getEmail().matches("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$")){
            return ResponseResult.fail("邮箱格式错误");
        }
        RegisterVo registerVo = authService.register(userRegisterDto);
        if(registerVo.getRegisterResult() == RegisterResult.SUCCESS){
            return ResponseResult.success("注册成功", registerVo);
        }
        else{
            return ResponseResult.fail("注册失败:" + registerVo.getMessage());
        }
    }
}
