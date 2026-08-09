package org.scse.smartcontractsearchengine.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_attachment")
public class AiChatAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private String fileName;
    private String fileType;
    private String sourceType;
    private String contentText;
    private LocalDateTime createdAt;
}
