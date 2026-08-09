package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.dto.PasswordUpdateDto;
import org.scse.smartcontractsearchengine.model.dto.ProfileUpdateDto;
import org.scse.smartcontractsearchengine.model.entity.User;
import org.scse.smartcontractsearchengine.model.vo.UserVo;

public interface UserService {
    /**
     * 根据用户 ID 获取用户信息
     * @param userId 用户 ID
     * @return 用户信息
     */
    User getUserById(Long userId);

    /**
     * 将 User 转换为 UserVo
     * @param user 用户实体
     * @return 用户 VO
     */
    UserVo convertToUserVo(User user);

    /**
     * 更新用户资料
     * @param userId 用户 ID
     * @param dto 用户资料更新 DTO
     * @return 是否更新成功
     */
    boolean updateProfile(Long userId, ProfileUpdateDto dto);

    /**
     * 删除当前用户上传过的头像
     * @param userId 用户 ID
     * @param avatarUrl 头像资源地址
     * @return 是否删除成功
     */
    boolean deleteUploadedAvatar(Long userId, String avatarUrl);

    /**
     * 更新用户密码
     * @param userId 用户 ID
     * @param dto 用户密码更新 DTO
     * @return 是否更新成功
     */
    boolean updatePassword(Long userId, PasswordUpdateDto dto);
}
