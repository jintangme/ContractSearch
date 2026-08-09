package org.scse.smartcontractsearchengine.service.impl;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserAvatarStorageService {

    private static final Pattern DATA_URL_PATTERN = Pattern.compile("^data:image/(png|jpe?g|webp);base64,(.+)$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private final Path avatarDir = Paths.get(resolveAvatarDir()).toAbsolutePath().normalize();

    public String saveAvatar(Long userId, String imageData) {
        if (userId == null || !StringUtils.hasText(imageData)) {
            throw new IllegalArgumentException("头像数据不能为空");
        }

        AvatarData avatarData = parseAvatarData(imageData);
        try {
            Files.createDirectories(avatarDir);
            String filename = "user-" + userId + "-" + System.currentTimeMillis() + "." + avatarData.extension();
            Path target = avatarDir.resolve(filename).normalize();
            if (!target.startsWith(avatarDir)) {
                throw new IllegalArgumentException("头像路径不合法");
            }
            Files.write(target, avatarData.bytes());
            return "/api/user/avatars/" + filename;
        } catch (IOException ex) {
            throw new IllegalStateException("头像保存失败", ex);
        }
    }

    private AvatarData parseAvatarData(String imageData) {
        Matcher matcher = DATA_URL_PATTERN.matcher(imageData.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("仅支持 PNG、JPG、JPEG、WEBP 头像");
        }

        String extension = normalizeExtension(matcher.group(1));
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("头像数据格式不正确");
        }

        if (bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("头像大小不能超过 2MB");
        }

        return new AvatarData(extension, bytes);
    }

    public Resource loadAvatar(String filename) {
        if (!StringUtils.hasText(filename) || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return null;
        }
        try {
            Path file = avatarDir.resolve(filename).normalize();
            if (!file.startsWith(avatarDir) || !Files.exists(file)) {
                return null;
            }
            return new UrlResource(file.toUri());
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    public boolean deleteUserAvatar(Long userId, String avatarUrl) {
        String filename = extractFilename(avatarUrl);
        if (userId == null || !StringUtils.hasText(filename) || !filename.startsWith("user-" + userId + "-")) {
            return false;
        }
        try {
            Path file = avatarDir.resolve(filename).normalize();
            if (!file.startsWith(avatarDir)) {
                return false;
            }
            return Files.deleteIfExists(file);
        } catch (IOException ex) {
            return false;
        }
    }

    private String extractFilename(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return "";
        }
        String trimmed = avatarUrl.trim();
        int index = trimmed.lastIndexOf('/');
        String filename = index >= 0 ? trimmed.substring(index + 1) : trimmed;
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return "";
        }
        return filename;
    }

    private String normalizeExtension(String value) {
        String ext = value.toLowerCase(Locale.ROOT);
        return ext.equals("jpeg") ? "jpg" : ext;
    }

    private String resolveAvatarDir() {
        String configured = System.getProperty("SCSE_AVATAR_DIR");
        if (!StringUtils.hasText(configured)) {
            configured = System.getenv("SCSE_AVATAR_DIR");
        }
        return StringUtils.hasText(configured)
                ? configured
                : Paths.get(System.getProperty("user.dir"), "data", "avatars").toString();
    }

    private record AvatarData(String extension, byte[] bytes) {
    }
}
