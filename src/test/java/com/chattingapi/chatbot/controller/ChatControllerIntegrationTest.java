package com.chattingapi.chatbot.controller;

import com.chattingapi.chatbot.config.RequestTraceFilter;
import com.chattingapi.chatbot.entity.Conversation;
import com.chattingapi.chatbot.entity.User;
import com.chattingapi.chatbot.exception.ErrorCode;
import com.chattingapi.chatbot.exception.UpstreamException;
import com.chattingapi.chatbot.repository.ConversationRepository;
import com.chattingapi.chatbot.repository.MessageRepository;
import com.chattingapi.chatbot.repository.UserRepository;
import com.chattingapi.chatbot.service.ApiKeyHasher;
import com.chattingapi.chatbot.service.OpenAIService;
import com.chattingapi.chatbot.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ChatControllerIntegrationTest {

    private static final String USER_API_KEY = "test-user-api-key";
    private static final String OTHER_USER_API_KEY = "other-user-api-key";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestTraceFilter requestTraceFilter;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Autowired
    private RateLimitService rateLimitService;

    @MockitoBean
    private OpenAIService openAIService;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestTraceFilter)
                .build();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
        rateLimitService.clearAll();

        String hashed = apiKeyHasher.hash(USER_API_KEY);
        userRepository.save(User.create(hashed));
        String otherHashed = apiKeyHasher.hash(OTHER_USER_API_KEY);
        userRepository.save(User.create(otherHashed));
    }

    @Test
    void getConversations_requiresApiKey() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getConversations_invalidApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/conversations")
                        .header("X-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getConversations_paging_works() throws Exception {
        User user = userRepository.findByApiKey(apiKeyHasher.hash(USER_API_KEY)).orElseThrow();
        conversationRepository.save(Conversation.create(user, "c1"));
        conversationRepository.save(Conversation.create(user, "c2"));
        conversationRepository.save(Conversation.create(user, "c3"));

        mockMvc.perform(get("/api/conversations")
                        .header("X-API-Key", USER_API_KEY)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void getMessages_invalidConversationId_returns400() throws Exception {
        mockMvc.perform(get("/api/conversations/{id}/messages", 0)
                        .header("X-API-Key", USER_API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void getConversations_invalidPageSize_returns400() throws Exception {
        mockMvc.perform(get("/api/conversations")
                        .header("X-API-Key", USER_API_KEY)
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void getConversation_notOwnedByUser_returns404() throws Exception {
        User otherUser = userRepository.findByApiKey(apiKeyHasher.hash(OTHER_USER_API_KEY)).orElseThrow();
        Conversation c = conversationRepository.save(Conversation.create(otherUser, "other"));

        mockMvc.perform(get("/api/conversations/{id}", c.getId())
                        .header("X-API-Key", USER_API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void deleteConversation_notOwnedByUser_returns404() throws Exception {
        User otherUser = userRepository.findByApiKey(apiKeyHasher.hash(OTHER_USER_API_KEY)).orElseThrow();
        Conversation c = conversationRepository.save(Conversation.create(otherUser, "other"));

        mockMvc.perform(delete("/api/conversations/{id}", c.getId())
                        .header("X-API-Key", USER_API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void chat_whenUpstreamRateLimited_returns429() throws Exception {
        when(openAIService.chat(anyList())).thenThrow(
                new UpstreamException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, "OpenAI quota exceeded")
        );

        String body = """
                {
                  "message": "hello"
                }
                """;

        mockMvc.perform(post("/api/chat/completions")
                        .header("X-API-Key", USER_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void chat_whenUpstreamNetworkError_returns502() throws Exception {
        when(openAIService.chat(anyList())).thenThrow(new UpstreamException("OpenAI API network error"));

        String body = """
                {
                  "message": "hello"
                }
                """;

        mockMvc.perform(post("/api/chat/completions")
                        .header("X-API-Key", USER_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_ERROR"));
    }

    @Test
    void health_returnsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void requestTraceHeader_isReturned() throws Exception {
        mockMvc.perform(get("/health").header("X-Request-Id", "trace-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-abc-123"));
    }

    @Test
    void getConversations_rateLimitExceeded_returns429() throws Exception {
        mockMvc.perform(get("/api/conversations").header("X-API-Key", USER_API_KEY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/conversations").header("X-API-Key", USER_API_KEY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/conversations").header("X-API-Key", USER_API_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations").header("X-API-Key", USER_API_KEY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }
}
