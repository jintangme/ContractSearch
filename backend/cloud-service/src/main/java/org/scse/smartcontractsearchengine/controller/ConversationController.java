package org.scse.smartcontractsearchengine.controller;

import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.context.UserContext;
import org.scse.smartcontractsearchengine.model.dto.AttachmentParseResponse;
import org.scse.smartcontractsearchengine.model.dto.ConversationChatRequest;
import org.scse.smartcontractsearchengine.model.dto.ConversationChatResponse;
import org.scse.smartcontractsearchengine.model.vo.ConversationSessionVo;
import org.scse.smartcontractsearchengine.service.ConversationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/chat")
    public ResponseResult<ConversationChatResponse> chat(@RequestBody ConversationChatRequest request) {
        try {
            return ResponseResult.success(conversationService.chat(UserContext.getUserId(), request));
        } catch (RuntimeException e) {
            String errorCode = resolveErrorCode(e.getMessage());
            String safeMessage = "ATTACHMENT_CONTENT_MISSING".equals(errorCode) ? "附件内容缺失" : "生成失败，请重试";
            ConversationChatResponse response = ConversationChatResponse.builder()
                    .success(false)
                    .errorCode(errorCode)
                    .message(safeMessage)
                    .requestedModel(request == null ? null : request.getModel())
                    .build();
            return ResponseResult.fail(safeMessage, response);
        }
    }

    @GetMapping("/sessions")
    public ResponseResult<List<ConversationSessionVo>> sessions() {
        try {
            return ResponseResult.success(conversationService.listSessions(UserContext.getUserId()));
        } catch (RuntimeException e) {
            return ResponseResult.fail("加载失败，请稍后重试");
        }
    }

    @GetMapping("/sessions/{id}")
    public ResponseResult<ConversationSessionVo> session(@PathVariable Long id) {
        try {
            return ResponseResult.success(conversationService.getSession(UserContext.getUserId(), id));
        } catch (RuntimeException e) {
            return ResponseResult.fail("加载失败，请稍后重试");
        }
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseResult<Void> deleteSession(@PathVariable Long id) {
        try {
            conversationService.deleteSession(UserContext.getUserId(), id);
            return ResponseResult.success();
        } catch (RuntimeException e) {
            return ResponseResult.fail("删除失败，请重试");
        }
    }

    @PostMapping("/attachments/parse")
    public ResponseResult<AttachmentParseResponse> parseAttachment(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseResult.success(conversationService.parseAttachment(file));
        } catch (RuntimeException e) {
            return ResponseResult.fail(cleanMessage(e.getMessage()));
        }
    }

    private String cleanMessage(String message) {
        if (message == null || message.isBlank()) return "请求失败，请稍后重试";
        if (message.contains("Exception") || message.contains("SQL") || message.contains("org.")) {
            return "请求失败，请稍后重试";
        }
        return message;
    }

    private String resolveErrorCode(String message) {
        if (message != null && message.contains("不支持的 AI 模型")) {
            return "UNSUPPORTED_MODEL";
        }
        if (message != null && message.contains("消息不能为空")) {
            return "EMPTY_MESSAGE";
        }
        if (message != null && message.contains("附件内容缺失")) {
            return "ATTACHMENT_CONTENT_MISSING";
        }
        return "AI_PROVIDER_ERROR";
    }
}
