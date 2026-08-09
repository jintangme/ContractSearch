package org.scse.smartcontractsearchengine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.scse.smartcontractsearchengine.model.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    User findByUsername(String username);

    User findById(Long id);

    // void insert(User user);

}