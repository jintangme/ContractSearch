package org.scse.smartcontractsearchengine.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("user_table")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String avatarType;
    private String avatarUrl;
    private String avatarPresetKey;
    private String avatarHistory;
    private OffsetDateTime creatTime;
    private OffsetDateTime updateTime;
    private Boolean isDeleted;
}
