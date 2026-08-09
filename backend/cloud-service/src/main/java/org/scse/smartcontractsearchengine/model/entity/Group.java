package org.scse.smartcontractsearchengine.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("group_table")
public class Group {

    private Long id;

    private String groupTitle;

    private String groupDescription;

    private Timestamp updateTime;

}
