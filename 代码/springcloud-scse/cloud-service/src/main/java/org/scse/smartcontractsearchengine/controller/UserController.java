package org.scse.smartcontractsearchengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.context.UserContext;
import org.scse.smartcontractsearchengine.model.dto.AvatarDeleteDto;
import org.scse.smartcontractsearchengine.model.dto.PasswordUpdateDto;
import org.scse.smartcontractsearchengine.model.dto.ProfileUpdateDto;
import org.scse.smartcontractsearchengine.model.vo.UserVo;
import org.scse.smartcontractsearchengine.service.impl.UserAvatarStorageService;
import org.scse.smartcontractsearchengine.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户controller", description = "提供登录注册、用户信息查询等功能，暂不做")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private UserAvatarStorageService avatarStorageService;

    @GetMapping("/profile")
    @Operation(summary = "获取用户信息", description = "当没有参数传入时，根据 UserContext 中的 userid 获取当前用户；当 query 参数为用户 id 时，返回该用户的数据")
    public ResponseResult<UserVo> info() {
        Long userId = UserContext.getUserId();

        if (userId == null) {
            return ResponseResult.fail("未找到用户 ID，请先登录");
        }

        UserVo userVo = userService.convertToUserVo(userService.getUserById(userId));
        if (userVo == null) {
            return ResponseResult.fail("用户不存在");
        }

        return ResponseResult.success(userVo);
    }

    @PutMapping("/profile")
    public ResponseResult<String> putProfile(@RequestBody ProfileUpdateDto dto) {
        boolean success = userService.updateProfile(UserContext.getUserId(), dto);
        if (!success) {
            return ResponseResult.fail("更新用户资料失败");
        }
        return ResponseResult.success("更新用户资料成功");
    }

    @PutMapping("/password")
    public ResponseResult<String> putPassword(@RequestBody PasswordUpdateDto dto) {
        boolean success = userService.updatePassword(UserContext.getUserId(), dto);
        if (!success) {
            return ResponseResult.fail("更新用户密码失败");
        }
        return ResponseResult.success("更新用户密码成功");
    }

    @DeleteMapping("/avatars")
    public ResponseResult<String> deleteUploadedAvatar(@RequestBody AvatarDeleteDto dto) {
        boolean success = userService.deleteUploadedAvatar(UserContext.getUserId(), dto == null ? null : dto.getAvatarUrl());
        if (!success) {
            return ResponseResult.fail("删除头像失败");
        }
        return ResponseResult.success("删除头像成功");
    }

    @GetMapping("/avatars/{filename:.+}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        Resource resource = avatarStorageService.loadAvatar(filename);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(resolveMediaType(filename))
                .body(resource);
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_PNG;
    }
}
