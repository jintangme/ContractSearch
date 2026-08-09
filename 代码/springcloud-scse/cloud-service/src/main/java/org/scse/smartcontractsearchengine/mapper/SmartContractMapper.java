package org.scse.smartcontractsearchengine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.scse.smartcontractsearchengine.model.dto.Bm25Result;
import org.scse.smartcontractsearchengine.model.dto.SmartContractBaseDataDto;
import org.scse.smartcontractsearchengine.model.dto.VectorResult;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;

import java.util.List;

@Mapper
public interface SmartContractMapper extends BaseMapper<SmartContract> {
    /*
        继承 BaseMapper 后自动获得的方法包括：
        insert(SmartContract entity) - 插入记录
        deleteById(Serializable id) - 根据 ID 删除
        updateById(SmartContract entity) - 根据 ID 更新
        selectById(Serializable id) - 根据 ID 查询
        selectList(Wrapper<SmartContract> queryWrapper) - 查询列表
    */

    Integer isAddressExists(@Param("address") String address);

    @Select("SELECT id FROM smart_contract WHERE address = #{address} AND is_deleted = false LIMIT 1")
    Long findIdByAddress(@Param("address") String address);

    @Select("SELECT id FROM smart_contract WHERE source_code = #{sourceCode} AND is_deleted = false LIMIT 1")
    Long findActiveIdBySourceCode(@Param("sourceCode") String sourceCode);

    @Select("SELECT id, contract_name, source_code FROM smart_contract " +
            "WHERE contract_name = #{contractName} AND is_deleted = false LIMIT 50")
    List<SmartContract> findActiveContractsByName(@Param("contractName") String contractName);

    @Delete("DELETE FROM smart_contract WHERE id = #{id} AND uploader_id = #{userId}")
    int physicallyDeleteUploadedContract(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM smart_contract WHERE is_effective = false")
    List<SmartContract> findInEffectiveContracts();

    @Select("SELECT * FROM smart_contract WHERE is_effective = false LIMIT #{limit}")
    List<SmartContract> findInEffectiveContracts(int limit);

    // 向量搜索：使用 <=> 操作符计算余弦距离
    @Select("SELECT id, description_embedding <=> #{embedding}::vector AS distance " +
            "FROM smart_contract " +
            "WHERE description IS NOT NULL AND description != '' " +
            "AND is_effective = true AND is_deleted = false " +
            "ORDER BY distance ASC " +
            "LIMIT #{limit}")
    List<VectorResult> descriptionVectorSearch(@Param("embedding") float[] embedding, @Param("limit") int limit);

    // BM25 全文搜索：使用 ParadeDB 的 ||| 操作符和 pdb.score(id) 函数
    @Select("SELECT id, pdb.score(id) AS score " +
            "FROM smart_contract " +
            "WHERE description ||| #{query} " +
            "AND is_effective = true AND is_deleted = false " +
            "ORDER BY score DESC " +
            "LIMIT #{limit}")
    List<Bm25Result> descriptionTextSearch(@Param("query") String query, @Param("limit") int limit);

//    // 向量搜索：在收藏中搜索（使用子查询）
//    @Select("SELECT id, description_embedding <=> #{embedding}::vector AS distance " +
//            "FROM smart_contract " +
//            "WHERE id  IN (SELECT contract_id FROM user_favorite WHERE user_id = #{userId} AND is_deleted = false) " +
//            "AND description IS NOT NULL AND description != '' " +
//            "AND is_effective = true AND is_deleted = false " +
//            "ORDER BY distance ASC " +
//            "LIMIT #{limit}")
//    List<VectorResult> descriptionVectorSearchInFav(@Param("embedding") float[] embedding, @Param("limit") int limit, @Param("userId")Long userId);
    
//    @Select("SELECT id, pdb.score(id) AS score " +
//            "FROM smart_contract " +
//            "WHERE id IN (SELECT contract_id FROM user_favorite WHERE user_id = #{userId} AND is_deleted = false) " +
//            "AND description @@@ #{query} " +
//            "AND is_effective = true AND is_deleted = false " +
//            "ORDER BY score DESC " +
//            "LIMIT #{limit}")
//    List<Bm25Result> descriptionTextSearchInFav(@Param("query") String query, @Param("limit") int limit, @Param("userId") Long userId);

    // BM25 全文搜索：使用 ParadeDB 的 @@@ 操作符和 pdb.score(id) 函数
    @Select("SELECT id, pdb.score(id) AS score " +
            "FROM smart_contract " +
            "WHERE source_code ||| #{query} " +
            "AND is_effective = true AND is_deleted = false " +
            "ORDER BY score DESC " +
            "LIMIT #{limit}")
    List<Bm25Result> codeTextSearch(@Param("query") String query, @Param("limit") int limit);
    
//    // 向量搜索：使用 <=> 操作符计算余弦距离
//    @Select("SELECT id, code_embedding <=> #{embedding}::vector AS distance " +
//            "FROM smart_contract " +
//            "WHERE is_effective = true AND is_deleted = false " +
//            "ORDER BY distance ASC " +
//            "LIMIT #{limit}")
//    List<VectorResult> codeVectorSearch(@Param("embedding") float[] embedding, @Param("limit") int limit);
    
//    // BM25 全文搜索 在收藏中搜索
//    @Select("SELECT id, pdb.score(id) AS score " +
//            "FROM smart_contract " +
//            "WHERE id IN (SELECT contract_id FROM user_favorite WHERE user_id = #{userId} AND is_deleted = false) " +
//            "AND source_code @@@ #{query} " +
//            "AND is_effective = true AND is_deleted = false " +
//            "ORDER BY score DESC " +
//            "LIMIT #{limit}")
//    List<Bm25Result> codeTextSearchInFav(@Param("query") String query, @Param("limit") int limit, @Param("userId") Long userId);
//
//    // 向量搜索 在收藏中搜索（使用子查询）
//    @Select("SELECT id, code_embedding <=> #{embedding}::vector AS distance " +
//            "FROM smart_contract " +
//            "WHERE id IN (SELECT contract_id FROM user_favorite WHERE user_id = #{userId} AND is_deleted = false) " +
//            "AND is_effective = true AND is_deleted = false " +
//            "ORDER BY distance ASC " +
//            "LIMIT #{limit}")
//    List<VectorResult> codeVectorSearchInFav(@Param("embedding") float[] embedding, @Param("limit") int limit, @Param("userId") Long userId);

}
