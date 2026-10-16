package com.vietnam.pji.controller.agentic;

import com.vietnam.pji.dto.request.CreateChatSessionRequestDTO;
import com.vietnam.pji.dto.request.SendChatMessageRequestDTO;
import com.vietnam.pji.dto.response.PaginationResultDTO;
import com.vietnam.pji.dto.response.ResponseData;
import com.vietnam.pji.model.agentic.AiChatMessage;
import com.vietnam.pji.model.agentic.AiChatSession;
import com.vietnam.pji.services.agent.AiChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "Conversational AI chat sessions scoped to an episode")
public class AiChatController {

        private final AiChatService aiChatService;

        @PostMapping("/ai-chat/sessions")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create a new AI chat session")
        public ResponseData<AiChatSession> createSession(@RequestBody CreateChatSessionRequestDTO request) {
                return new ResponseData<>(HttpStatus.CREATED.value(), "Chat session created successfully",
                                aiChatService.createSession(request));
        }

        @PostMapping("/ai-chat/sessions/{sessionId}/messages")
        @Operation(summary = "Send a message to AI chat and get response")
        public ResponseData<AiChatMessage> sendMessage(
                        @PathVariable Long sessionId,
                        @RequestBody SendChatMessageRequestDTO request) {
                return new ResponseData<>(HttpStatus.OK.value(), "Message sent successfully",
                                aiChatService.sendMessage(sessionId, request));
        }

        @GetMapping("/ai-chat/sessions/{sessionId}/messages")
        @Operation(summary = "Get messages of a chat session")
        public ResponseData<PaginationResultDTO> getMessages(
                        @PathVariable Long sessionId, Pageable pageable) {
                return new ResponseData<>(HttpStatus.OK.value(), "Fetch messages successfully",
                                aiChatService.getMessages(sessionId, pageable));
        }

        @GetMapping({"/admissions/{episodeId}/ai-chat/sessions", "/episodes/{episodeId}/ai-chat/sessions"})
        @Operation(summary = "Get chat sessions for an episode")
        public ResponseData<PaginationResultDTO> getSessionsByEpisode(
                        @PathVariable Long episodeId, Pageable pageable) {
                return new ResponseData<>(HttpStatus.OK.value(), "Fetch sessions successfully",
                                aiChatService.getSessionsByEpisode(episodeId, pageable));
        }
}
