package org.scse.smartcontractsearchengine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;
import org.scse.smartcontractsearchengine.model.entity.GroupContract;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;

import java.util.List;

@Mapper
public interface GroupContractMapper extends BaseMapper<GroupContract> {
    @Select("SELECT sc.id, sc.address, sc.contract_name, sc.category, sc.description " +
            "FROM group_contract gc " +
            "JOIN smart_contract sc " +
            "ON gc.contract_id = sc.id " +
            "WHERE group_id = #{groupId}")
    @ResultType(ContractCardVo.class)
    List<ContractCardVo> findContractsByGroupId(@Param("groupId") Long groupId);

    @Insert("INSERT INTO group_contract (group_id, contract_id, order_num) " +
            "VALUES (#{groupId}, #{contractId}, #{orderNum}) " +
            "ON CONFLICT (group_id, order_num) " +
            "DO UPDATE SET contract_id = EXCLUDED.contract_id")
    int upsertByGroupIdAndOrderNum(@Param("groupId") Long groupId,
                                   @Param("contractId") Long contractId,
                                   @Param("orderNum") int orderNum);
}
