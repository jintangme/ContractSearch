package org.scse.smartcontractsearchengine.service.impl;

import jakarta.annotation.PostConstruct;
import org.scse.smartcontractsearchengine.mapper.UserMapper;
import org.scse.smartcontractsearchengine.model.dto.PasswordUpdateDto;
import org.scse.smartcontractsearchengine.model.dto.ProfileUpdateDto;
import org.scse.smartcontractsearchengine.model.entity.User;
import org.scse.smartcontractsearchengine.model.vo.UserVo;
import org.scse.smartcontractsearchengine.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserAvatarStorageService avatarStorageService;

    @PostConstruct
    public void ensureAvatarColumns() {
        jdbcTemplate.execute("ALTER TABLE user_table ADD COLUMN IF NOT EXISTS avatar_type VARCHAR(32)");
        jdbcTemplate.execute("ALTER TABLE user_table ADD COLUMN IF NOT EXISTS avatar_url TEXT");
        jdbcTemplate.execute("ALTER TABLE user_table ADD COLUMN IF NOT EXISTS avatar_preset_key VARCHAR(64)");
        jdbcTemplate.execute("ALTER TABLE user_table ADD COLUMN IF NOT EXISTS avatar_history TEXT");
    }

    @Override
    public User getUserById(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.findById(userId);
    }

    @Override
    public UserVo convertToUserVo(User user) {
        if (user == null) {
            return null;
        }
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        return userVo;
    }

    @Override
    public boolean updateProfile(Long userId, ProfileUpdateDto dto) {
        if (userId == null || dto == null) {
            return false;
        }
        User currentUser = userMapper.findById(userId);
        if (currentUser == null) {
            return false;
        }
        User user = new User();
        if (dto.getUsername() != null) {
            String username = dto.getUsername().trim();
            if (username.isEmpty() || username.length() > 32) {
                return false;
            }
            User existing = userMapper.findByUsername(username);
            if (existing != null && !userId.equals(existing.getId())) {
                return false;
            }
            user.setUsername(username);
        }
        if (dto.getNickname() != null) {
            String nickname = dto.getNickname().trim();
            if (nickname.isEmpty() || nickname.length() > 32) {
                return false;
            }
            user.setNickname(nickname);
        }
        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim();
            if (email.isEmpty() || email.length() > 64) {
                return false;
            }
            user.setEmail(email);
        }
        if (dto.getAvatarType() != null) {
            String avatarType = dto.getAvatarType().trim();
            if ("preset".equals(avatarType)) {
                String presetKey = dto.getAvatarPresetKey();
                if (!StringUtils.hasText(presetKey) || presetKey.trim().length() > 64) {
                    return false;
                }
                user.setAvatarType("preset");
                user.setAvatarPresetKey(presetKey.trim());
                user.setAvatarUrl("");
            } else if ("upload".equals(avatarType)) {
                if (StringUtils.hasText(dto.getAvatarImageData())) {
                    try {
                        String avatarUrl = avatarStorageService.saveAvatar(userId, dto.getAvatarImageData());
                        user.setAvatarType("upload");
                        user.setAvatarUrl(avatarUrl);
                        user.setAvatarPresetKey("");
                        user.setAvatarHistory(appendAvatarHistory(currentUser.getAvatarHistory(), avatarUrl));
                    } catch (RuntimeException ex) {
                        return false;
                    }
                } else if (StringUtils.hasText(dto.getAvatarUrl()) && avatarExistsInHistory(currentUser.getAvatarHistory(), dto.getAvatarUrl().trim())) {
                    user.setAvatarType("upload");
                    user.setAvatarUrl(dto.getAvatarUrl().trim());
                    user.setAvatarPresetKey("");
                } else if (StringUtils.hasText(currentUser.getAvatarUrl())) {
                    user.setAvatarType("upload");
                    user.setAvatarUrl(currentUser.getAvatarUrl());
                    user.setAvatarPresetKey("");
                } else {
                    return false;
                }
            } else if ("letter".equals(avatarType)) {
                user.setAvatarType("letter");
                user.setAvatarUrl("");
                user.setAvatarPresetKey("");
            } else {
                return false;
            }
        }
        user.setId(userId);
        return userMapper.updateById(user) > 0;
    }

    private String appendAvatarHistory(String currentHistory, String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return currentHistory;
        }
        String normalizedUrl = avatarUrl.trim();
        StringBuilder builder = new StringBuilder();
        builder.append(normalizedUrl);
        if (StringUtils.hasText(currentHistory)) {
            for (String item : currentHistory.split("\\R")) {
                String value = item.trim();
                if (StringUtils.hasText(value) && !normalizedUrl.equals(value)) {
                    builder.append('\n').append(value);
                }
            }
        }
        return builder.toString();
    }

    private boolean avatarExistsInHistory(String currentHistory, String avatarUrl) {
        if (!StringUtils.hasText(currentHistory) || !StringUtils.hasText(avatarUrl)) {
            return false;
        }
        String normalizedUrl = avatarUrl.trim();
        for (String item : currentHistory.split("\\R")) {
            if (normalizedUrl.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean deleteUploadedAvatar(Long userId, String avatarUrl) {
        if (userId == null || !StringUtils.hasText(avatarUrl)) {
            return false;
        }
        User currentUser = userMapper.findById(userId);
        if (currentUser == null) {
            return false;
        }
        String normalizedUrl = avatarUrl.trim();
        if (!avatarExistsInHistory(currentUser.getAvatarHistory(), normalizedUrl)) {
            return false;
        }

        User user = new User();
        user.setId(userId);
        user.setAvatarHistory(removeAvatarFromHistory(currentUser.getAvatarHistory(), normalizedUrl));
        if (normalizedUrl.equals(currentUser.getAvatarUrl())) {
            user.setAvatarType("letter");
            user.setAvatarUrl("");
            user.setAvatarPresetKey("");
        }
        boolean updated = userMapper.updateById(user) > 0;
        if (updated) {
            avatarStorageService.deleteUserAvatar(userId, normalizedUrl);
        }
        return updated;
    }

    private String removeAvatarFromHistory(String currentHistory, String avatarUrl) {
        if (!StringUtils.hasText(currentHistory)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String item : currentHistory.split("\\R")) {
            String value = item.trim();
            if (StringUtils.hasText(value) && !avatarUrl.equals(value)) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(value);
            }
        }
        return builder.toString();
    }

    @Override
    public boolean updatePassword(Long userId, PasswordUpdateDto dto) {
        if (userId == null || dto == null) {
            return false;
        }
        String oldPassword = dto.getOldPassword();
        String newPassword = dto.getNewPassword();
        if (oldPassword == null || newPassword == null) {
            return false;
        }
        User user = userMapper.findById(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        return userMapper.updateById(user) > 0;
    }
}
