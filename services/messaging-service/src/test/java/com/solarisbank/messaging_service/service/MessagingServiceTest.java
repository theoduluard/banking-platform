package com.solarisbank.messaging_service.service;

import com.solarisbank.messaging_service.dto.CreateRequestRequest;
import com.solarisbank.messaging_service.dto.MessageResponse;
import com.solarisbank.messaging_service.dto.SendMessageRequest;
import com.solarisbank.messaging_service.dto.SupportRequestResponse;
import com.solarisbank.messaging_service.exception.BusinessException;
import com.solarisbank.messaging_service.model.Message;
import com.solarisbank.messaging_service.model.SupportRequest;
import com.solarisbank.messaging_service.model.SupportRequestReply;
import com.solarisbank.messaging_service.repository.MessageRepository;
import com.solarisbank.messaging_service.repository.SupportRequestReplyRepository;
import com.solarisbank.messaging_service.repository.SupportRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

    @Mock private MessageRepository             messageRepository;
    @Mock private SupportRequestRepository      requestRepository;
    @Mock private SupportRequestReplyRepository replyRepository;

    @InjectMocks
    private MessagingService messagingService;

    private UUID userId;
    private UUID messageId;
    private UUID requestId;
    private Message savedMessage;
    private SupportRequest openRequest;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        messageId = UUID.randomUUID();
        requestId = UUID.randomUUID();

        savedMessage = Message.builder()
                .id(messageId)
                .userId(userId)
                .subject("Bienvenue")
                .body("Votre compte a été approuvé.")
                .type(Message.Type.APPROVAL)
                .createdAt(LocalDateTime.now())
                .build();

        openRequest = SupportRequest.builder()
                .id(requestId)
                .userId(userId)
                .type(SupportRequest.Type.OTHER)
                .subject("Problème de connexion")
                .body("Je n'arrive pas à me connecter.")
                .status(SupportRequest.Status.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── sendMessage ────────────────────────────────────────────────────────────

    @Test
    void sendMessage_shouldPersistAndReturnResponse() {
        SendMessageRequest req = new SendMessageRequest();
        req.setUserId(userId);
        req.setSubject("Bienvenue");
        req.setBody("Votre compte a été approuvé.");
        req.setType(Message.Type.APPROVAL);

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        MessageResponse response = messagingService.sendMessage(req);

        assertThat(response.getId()).isEqualTo(messageId);
        assertThat(response.getSubject()).isEqualTo("Bienvenue");
        assertThat(response.getUserId()).isEqualTo(userId);
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void sendMessage_shouldDefaultToInfoType_whenTypeIsNull() {
        SendMessageRequest req = new SendMessageRequest();
        req.setUserId(userId);
        req.setSubject("Info");
        req.setBody("Contenu.");
        req.setType(null);

        Message infoMessage = Message.builder()
                .id(UUID.randomUUID()).userId(userId)
                .subject("Info").body("Contenu.")
                .type(Message.Type.INFO)
                .createdAt(LocalDateTime.now()).build();

        when(messageRepository.save(any(Message.class))).thenReturn(infoMessage);

        MessageResponse response = messagingService.sendMessage(req);

        assertThat(response.getType()).isEqualTo(Message.Type.INFO);
    }

    // ── getMyMessages ──────────────────────────────────────────────────────────

    @Test
    void getMyMessages_shouldReturnPagedMessages() {
        Page<Message> page = new PageImpl<>(List.of(savedMessage), PageRequest.of(0, 20), 1);
        when(messageRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<MessageResponse> result = messagingService.getMyMessages(userId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(messageId);
    }

    // ── getUnreadCount ─────────────────────────────────────────────────────────

    @Test
    void getUnreadCount_shouldReturnCount() {
        when(messageRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(3L);

        assertThat(messagingService.getUnreadCount(userId)).isEqualTo(3L);
    }

    // ── markAsRead ─────────────────────────────────────────────────────────────

    @Test
    void markAsRead_shouldSetReadFlag_whenUserIsOwner() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(savedMessage));
        when(messageRepository.save(savedMessage)).thenReturn(savedMessage);

        MessageResponse response = messagingService.markAsRead(messageId, userId);

        assertThat(response).isNotNull();
        assertThat(savedMessage.isRead()).isTrue();
        verify(messageRepository).save(savedMessage);
    }

    @Test
    void markAsRead_shouldThrowForbidden_whenUserIsNotOwner() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(savedMessage));

        assertThatThrownBy(() -> messagingService.markAsRead(messageId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void markAsRead_shouldThrowNotFound_whenMessageDoesNotExist() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messagingService.markAsRead(messageId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Message not found");
    }

    // ── createRequest ──────────────────────────────────────────────────────────

    @Test
    void createRequest_shouldPersistAndReturnResponse() {
        CreateRequestRequest req = new CreateRequestRequest();
        req.setType(SupportRequest.Type.OTHER);
        req.setSubject("Problème de connexion");
        req.setBody("Je n'arrive pas à me connecter.");

        when(requestRepository.save(any(SupportRequest.class))).thenReturn(openRequest);

        SupportRequestResponse response = messagingService.createRequest(userId, req);

        assertThat(response.getId()).isEqualTo(requestId);
        assertThat(response.getStatus()).isEqualTo(SupportRequest.Status.OPEN);
        verify(requestRepository).save(any(SupportRequest.class));
    }

    // ── getMyRequests ──────────────────────────────────────────────────────────

    @Test
    void getMyRequests_shouldReturnUserRequests() {
        when(requestRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(openRequest));

        List<SupportRequestResponse> result = messagingService.getMyRequests(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUserId()).isEqualTo(userId);
    }

    // ── getRequestDetail ───────────────────────────────────────────────────────

    @Test
    void getRequestDetail_shouldThrowForbidden_whenUserIsNotOwner() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(openRequest));

        assertThatThrownBy(() -> messagingService.getRequestDetail(requestId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void getRequestDetail_shouldThrowNotFound_whenRequestDoesNotExist() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messagingService.getRequestDetail(requestId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Request not found");
    }

    // ── addClientReply ─────────────────────────────────────────────────────────

    @Test
    void addClientReply_shouldThrow_whenRequestIsClosed() {
        openRequest.setStatus(SupportRequest.Status.RESOLVED);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(openRequest));

        com.solarisbank.messaging_service.dto.AddReplyRequest req =
                new com.solarisbank.messaging_service.dto.AddReplyRequest();
        req.setBody("Toujours le même problème.");

        assertThatThrownBy(() -> messagingService.addClientReply(requestId, userId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed request");

        verify(replyRepository, never()).save(any(SupportRequestReply.class));
    }
}
