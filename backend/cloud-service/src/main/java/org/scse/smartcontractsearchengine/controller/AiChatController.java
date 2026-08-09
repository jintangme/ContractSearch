package org.scse.smartcontractsearchengine.controller;

import org.scse.smartcontractsearchengine.common.result.ResponseResult;
import org.scse.smartcontractsearchengine.model.dto.AiChatRequest;
import org.scse.smartcontractsearchengine.model.dto.AiChatResponse;
import org.scse.smartcontractsearchengine.service.AiChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {
    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public ResponseResult<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        AiChatResponse response = aiChatService.chat(request);
        if (response.isSuccess()) {
            return ResponseResult.success(response);
        }
        return ResponseResult.fail(response.getMessage(), response);
    }
}
