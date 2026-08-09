package org.scse.smartcontractsearchengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.scse.smartcontractsearchengine.exception.FavoriteException;
import org.scse.smartcontractsearchengine.mapper.FavoriteFolderMapper;
import org.scse.smartcontractsearchengine.mapper.UserFavoriteMapper;
import org.scse.smartcontractsearchengine.model.entity.FavoriteFolder;
import org.scse.smartcontractsearchengine.model.entity.UserFavorite;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;
import org.scse.smartcontractsearchengine.model.vo.FavoriteFolderVo;
import org.scse.smartcontractsearchengine.service.FavoriteService;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final String DEFAULT_FOLDER_NAME = "默认文件夹";

    private final UserFavoriteMapper userFavoriteMapper;
    private final FavoriteFolderMapper favoriteFolderMapper;
    private final JdbcTemplate jdbcTemplate;

    public FavoriteServiceImpl(UserFavoriteMapper userFavoriteMapper,
                               FavoriteFolderMapper favoriteFolderMapper,
                               JdbcTemplate jdbcTemplate) {
        this.userFavoriteMapper = userFavoriteMapper;
        this.favoriteFolderMapper = favoriteFolderMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initFavoriteFolderSchema() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS favorite_folder (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        name VARCHAR(64) NOT NULL,
                        is_default BOOLEAN NOT NULL DEFAULT false,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_favorite_folder_user_name ON favorite_folder(user_id, name)");
            jdbcTemplate.execute("ALTER TABLE user_favorite ADD COLUMN IF NOT EXISTS folder_id BIGINT");
        } catch (Exception e) {
            log.warn("初始化收藏夹文件夹结构失败，收藏基础功能可能受影响", e);
        }
    }

    @Override
    public String addFavorite(Long userId, Long contractId) throws FavoriteException {
        return addFavorite(userId, contractId, null, null);
    }

    @Override
    public String addFavorite(Long userId, Long contractId, Long folderId, String newFolderName) throws FavoriteException {
        try {
            FavoriteFolder folder = resolveTargetFolder(userId, folderId, newFolderName);
            userFavoriteMapper.insert(UserFavorite.builder()
                    .userId(userId)
                    .contractId(contractId)
                    .folderId(folder.getId())
                    .build());
            return "收藏成功";
        } catch (DataAccessException e) {
            handleFavoriteDataAccessException(userId, contractId, e);
            return "收藏失败";
        }
    }

    @Override
    public String removeFavorite(Long userId, Long contractId) {
        try {
            int affected = userFavoriteMapper.deleteByUserIdAndContractId(userId, contractId);
            return affected > 0 ? "取消收藏成功" : "收藏记录不存在";
        } catch (DataAccessException e) {
            log.error("取消收藏合约出现未知错误,用户id:{} 合约id:{} ", userId, contractId, e);
            return "取消收藏失败";
        }
    }

    @Override
    public List<ContractCardVo> getFavoriteContracts(Long userId) {
        FavoriteFolder defaultFolder = ensureDefaultFolder(userId);
        assignNullFavoritesToDefault(userId, defaultFolder.getId());
        return userFavoriteMapper.findByUserId(userId);
    }

    @Override
    public List<FavoriteFolderVo> getFavoriteFolders(Long userId) {
        FavoriteFolder defaultFolder = ensureDefaultFolder(userId);
        assignNullFavoritesToDefault(userId, defaultFolder.getId());

        List<FavoriteFolder> folders = favoriteFolderMapper.selectList(
                new QueryWrapper<FavoriteFolder>()
                        .eq("user_id", userId)
                        .orderByDesc("is_default")
                        .orderByAsc("id")
        );

        List<FavoriteFolderVo> result = new ArrayList<>();
        for (FavoriteFolder folder : folders) {
            result.add(toFolderVo(folder));
        }
        return result;
    }

    @Override
    public FavoriteFolderVo createFavoriteFolder(Long userId, String name) throws FavoriteException {
        return toFolderVo(createOrGetFolder(userId, normalizeFolderName(name), false));
    }

    @Override
    public FavoriteFolderVo renameFavoriteFolder(Long userId, Long folderId, String name) throws FavoriteException {
        FavoriteFolder folder = requireOwnedFolder(userId, folderId);
        String normalizedName = normalizeFolderName(name);

        FavoriteFolder duplicated = findFolderByName(userId, normalizedName);
        if (duplicated != null && !duplicated.getId().equals(folderId)) {
            throw new FavoriteException("已存在同名收藏夹");
        }

        folder.setName(normalizedName);
        folder.setUpdatedAt(LocalDateTime.now());
        favoriteFolderMapper.updateById(folder);
        return toFolderVo(folder);
    }

    @Override
    public void deleteFavoriteFolder(Long userId, Long folderId) throws FavoriteException {
        FavoriteFolder folder = requireOwnedFolder(userId, folderId);
        if (Boolean.TRUE.equals(folder.getIsDefault())) {
            throw new FavoriteException("默认文件夹不能删除");
        }
        userFavoriteMapper.deleteByUserIdAndFolderId(userId, folderId);
        favoriteFolderMapper.deleteById(folderId);
    }

    @Override
    public List<ContractCardVo> getFavoriteContractsByFolder(Long userId, Long folderId) throws FavoriteException {
        requireOwnedFolder(userId, folderId);
        return userFavoriteMapper.findByUserIdAndFolderId(userId, folderId);
    }

    private FavoriteFolder resolveTargetFolder(Long userId, Long folderId, String newFolderName) throws FavoriteException {
        if (newFolderName != null && !newFolderName.trim().isEmpty()) {
            return createOrGetFolder(userId, normalizeFolderName(newFolderName), false);
        }
        if (folderId != null) {
            return requireOwnedFolder(userId, folderId);
        }
        return ensureDefaultFolder(userId);
    }

    private FavoriteFolder ensureDefaultFolder(Long userId) {
        FavoriteFolder folder = favoriteFolderMapper.selectOne(
                new QueryWrapper<FavoriteFolder>()
                        .eq("user_id", userId)
                        .eq("is_default", true)
                        .last("LIMIT 1")
        );
        if (folder != null) return folder;
        return createOrGetFolder(userId, DEFAULT_FOLDER_NAME, true);
    }

    private FavoriteFolder createOrGetFolder(Long userId, String name, boolean isDefault) {
        FavoriteFolder existing = findFolderByName(userId, name);
        if (existing != null) {
            if (isDefault && !Boolean.TRUE.equals(existing.getIsDefault())) {
                existing.setIsDefault(true);
                existing.setUpdatedAt(LocalDateTime.now());
                favoriteFolderMapper.updateById(existing);
            }
            return existing;
        }

        FavoriteFolder folder = FavoriteFolder.builder()
                .userId(userId)
                .name(name)
                .isDefault(isDefault)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        favoriteFolderMapper.insert(folder);
        return folder;
    }

    private FavoriteFolder findFolderByName(Long userId, String name) {
        return favoriteFolderMapper.selectOne(
                new QueryWrapper<FavoriteFolder>()
                        .eq("user_id", userId)
                        .eq("name", name)
                        .last("LIMIT 1")
        );
    }

    private FavoriteFolder requireOwnedFolder(Long userId, Long folderId) throws FavoriteException {
        if (folderId == null) {
            throw new FavoriteException("收藏夹 ID 不能为空");
        }
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null || !userId.equals(folder.getUserId())) {
            throw new FavoriteException("收藏夹不存在");
        }
        return folder;
    }

    private FavoriteFolderVo toFolderVo(FavoriteFolder folder) {
        FavoriteFolderVo vo = new FavoriteFolderVo();
        BeanUtils.copyProperties(folder, vo);
        vo.setContractCount(userFavoriteMapper.countByFolderId(folder.getId()));
        return vo;
    }

    private void assignNullFavoritesToDefault(Long userId, Long defaultFolderId) {
        try {
            jdbcTemplate.update(
                    "UPDATE user_favorite SET folder_id = ? WHERE user_id = ? AND folder_id IS NULL",
                    defaultFolderId,
                    userId
            );
        } catch (Exception e) {
            log.warn("迁移旧收藏到默认文件夹失败,userId={}", userId, e);
        }
    }

    private String normalizeFolderName(String name) throws FavoriteException {
        if (name == null || name.trim().isEmpty()) {
            throw new FavoriteException("请输入收藏夹名称");
        }
        String normalized = name.trim();
        if (normalized.length() > 32) {
            throw new FavoriteException("收藏夹名称不超过 32 个字符");
        }
        return normalized;
    }

    private void handleFavoriteDataAccessException(Long userId, Long contractId, DataAccessException e) throws FavoriteException {
        Throwable cause = e.getCause();
        if (cause instanceof PSQLException psqlEx) {
            String sqlState = psqlEx.getSQLState();
            if ("23505".equals(sqlState)) {
                throw new FavoriteException("用户已收藏");
            } else if ("23503".equals(sqlState)) {
                ServerErrorMessage serverError = psqlEx.getServerErrorMessage();
                String constraint = serverError.getConstraint();
                if ("fk_user_favorite_user_id".equals(constraint)) {
                    throw new FavoriteException("用户不存在");
                } else if ("fk_user_favorite_contract_id".equals(constraint)) {
                    throw new FavoriteException("合约不存在");
                }
            }
        }
        log.error("尝试收藏合约出现未知错误,用户id:{} 合约id:{} ", userId, contractId, e);
        throw new FavoriteException("收藏失败");
    }
}
