package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.model.dto.AttachmentParseResponse;
import org.scse.smartcontractsearchengine.model.dto.ConversationChatRequest;
import org.scse.smartcontractsearchengine.model.dto.ConversationChatResponse;
import org.scse.smartcontractsearchengine.model.vo.ConversationSessionVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConversationService {
    ConversationChatResponse chat(Long userId, ConversationChatRequest request);
    List<ConversationSessionVo> listSessions(Long userId);
    ConversationSessionVo getSession(Long userId, Long sessionId);
    void deleteSession(Long userId, Long sessionId);
    AttachmentParseResponse parseAttachment(MultipartFile file);
}
