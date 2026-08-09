package org.scse.smartcontractsearchengine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;
import org.scse.smartcontractsearchengine.model.entity.UserFavorite;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;

import java.util.List;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 根据用户 ID 和合约 ID 删除收藏（取消收藏）
     */
    int deleteByUserIdAndContractId(@Param("userId") Long userId, @Param("contractId") Long contractId);

    @Delete("DELETE FROM user_favorite WHERE contract_id = #{contractId}")
    int deleteByContractId(@Param("contractId") Long contractId);

    @Delete("DELETE FROM user_favorite WHERE user_id = #{userId} AND folder_id = #{folderId}")
    int deleteByUserIdAndFolderId(@Param("userId") Long userId, @Param("folderId") Long folderId);

    @Select("SELECT COUNT(*) FROM user_favorite WHERE folder_id = #{folderId}")
    int countByFolderId(@Param("folderId") Long folderId);

    /**
     * 查询用户是否已收藏该合约
     */
    @Select("SELECT * FROM user_favorite WHERE user_id = #{userId} AND contract_id = #{contractId}")
    UserFavorite findByUserIdAndContractId(@Param("userId") Long userId, @Param("contractId") Long contractId);

    /**
     * 统计用户的收藏数量
     */
    @Select("SELECT COUNT(*) FROM user_favorite WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    /**
     * 统计合约被收藏的次数
     */
    @Select("SELECT COUNT(*) FROM user_favorite WHERE contract_id = #{contractId}")
    int countByContractId(@Param("contractId") Long contractId);

    /**
     * 获取用户收藏的合约列表
     */
    @Select("SELECT sc.id, sc.address, sc.contract_name, sc.category, sc.description " +
            "FROM user_favorite uf " +
            "JOIN smart_contract sc " +
            "ON uf.contract_id = sc.id " +
            "WHERE uf.user_id = #{userId} " +
            "AND sc.is_deleted = false")
    @ResultType(ContractCardVo.class)
    List<ContractCardVo> findByUserId(@Param("userId") Long userId);

    @Select("SELECT sc.id, sc.address, sc.contract_name, sc.category, sc.description " +
            "FROM user_favorite uf " +
            "JOIN smart_contract sc " +
            "ON uf.contract_id = sc.id " +
            "WHERE uf.user_id = #{userId} " +
            "AND uf.folder_id = #{folderId} " +
            "AND sc.is_deleted = false")
    @ResultType(ContractCardVo.class)
    List<ContractCardVo> findByUserIdAndFolderId(@Param("userId") Long userId, @Param("folderId") Long folderId);
}
