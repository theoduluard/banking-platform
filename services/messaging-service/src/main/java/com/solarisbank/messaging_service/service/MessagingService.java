package com.solarisbank.messaging_service.service;

import com.solarisbank.messaging_service.dto.*;
import com.solarisbank.messaging_service.exception.BusinessException;
import com.solarisbank.messaging_service.model.Message;
import com.solarisbank.messaging_service.model.SupportRequest;
import com.solarisbank.messaging_service.model.SupportRequestReply;
import com.solarisbank.messaging_service.repository.MessageRepository;
import com.solarisbank.messaging_service.repository.SupportRequestRepository;
import com.solarisbank.messaging_service.repository.SupportRequestReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessagingService {

    private final MessageRepository             messageRepository;
    private final SupportRequestRepository      requestRepository;
    private final SupportRequestReplyRepository replyRepository;

    // ── Messages ───────────────────────────────────────────────────────────────

    /** Create a message programmatically (admin manual or system auto). */
    @Transactional
    public MessageResponse sendMessage(SendMessageRequest req) {
        Message msg = Message.builder()
                .userId(req.getUserId())
                .subject(req.getSubject())
                .body(req.getBody())
                .type(req.getType() != null ? req.getType() : Message.Type.INFO)
                .attachmentBase64(req.getAttachmentBase64())
                .attachmentContentType(req.getAttachmentContentType())
                .attachmentFilename(req.getAttachmentFilename())
                .build();
        return toMessageResponse(messageRepository.save(msg));
    }

    /** Auto-message triggered by Kafka events. */
    @Transactional
    public void sendSystemMessage(UUID userId, String subject, String body, Message.Type type) {
        Message msg = Message.builder()
                .userId(userId)
                .subject(subject)
                .body(body)
                .type(type)
                .build();
        messageRepository.save(msg);
    }

    public Page<MessageResponse> getMyMessages(UUID userId, int page, int size) {
        return messageRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::toMessageResponse);
    }

    public long getUnreadCount(UUID userId) {
        return messageRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public MessageResponse markAsRead(UUID messageId, UUID userId) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("Message not found", HttpStatus.NOT_FOUND));
        if (!msg.getUserId().equals(userId)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
        msg.setRead(true);
        return toMessageResponse(messageRepository.save(msg));
    }

    public Page<MessageResponse> getAllMessages(int page, int size) {
        return messageRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toMessageResponse);
    }

    // ── Support requests ───────────────────────────────────────────────────────

    @Transactional
    public SupportRequestResponse createRequest(UUID userId, CreateRequestRequest req) {
        SupportRequest sr = SupportRequest.builder()
                .userId(userId)
                .type(req.getType())
                .subject(req.getSubject())
                .body(req.getBody())
                .attachmentBase64(req.getAttachmentBase64())
                .attachmentContentType(req.getAttachmentContentType())
                .attachmentFilename(req.getAttachmentFilename())
                .build();
        return toRequestResponse(requestRepository.save(sr));
    }

    public List<SupportRequestResponse> getMyRequests(UUID userId) {
        return requestRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toRequestResponse).toList();
    }

    public SupportRequestDetailResponse getRequestDetail(UUID requestId, UUID userId) {
        SupportRequest sr = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found", HttpStatus.NOT_FOUND));
        if (!sr.getUserId().equals(userId)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
        return toDetailResponse(sr);
    }

    @Transactional
    public SupportRequestDetailResponse addClientReply(UUID requestId, UUID userId, AddReplyRequest req) {
        SupportRequest sr = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found", HttpStatus.NOT_FOUND));
        if (!sr.getUserId().equals(userId)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
        if (sr.getStatus() == SupportRequest.Status.RESOLVED
                || sr.getStatus() == SupportRequest.Status.REJECTED) {
            throw new BusinessException("Cannot reply to a closed request", HttpStatus.BAD_REQUEST);
        }

        SupportRequestReply reply = SupportRequestReply.builder()
                .requestId(requestId)
                .authorType(SupportRequestReply.AuthorType.CLIENT)
                .authorId(userId)
                .body(req.getBody())
                .attachmentBase64(req.getAttachmentBase64())
                .attachmentContentType(req.getAttachmentContentType())
                .attachmentFilename(req.getAttachmentFilename())
                .build();
        replyRepository.save(reply);

        // Re-open if it was IN_PROGRESS (client responded)
        if (sr.getStatus() == SupportRequest.Status.IN_PROGRESS) {
            sr.setStatus(SupportRequest.Status.OPEN);
            requestRepository.save(sr);
        }

        return toDetailResponse(requestRepository.findById(requestId).orElseThrow());
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    public Page<SupportRequestResponse> getAllRequests(int page, int size, SupportRequest.Status status) {
        PageRequest pr = PageRequest.of(page, size);
        Page<SupportRequest> results = (status != null)
                ? requestRepository.findByStatusOrderByCreatedAtDesc(status, pr)
                : requestRepository.findAllByOrderByCreatedAtDesc(pr);
        return results.map(this::toRequestResponse);
    }

    public SupportRequestDetailResponse getRequestDetailAdmin(UUID requestId) {
        SupportRequest sr = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found", HttpStatus.NOT_FOUND));
        return toDetailResponse(sr);
    }

    @Transactional
    public SupportRequestDetailResponse adminReply(UUID requestId, UUID adminId, AdminReplyRequest req) {
        SupportRequest sr = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found", HttpStatus.NOT_FOUND));

        SupportRequestReply reply = SupportRequestReply.builder()
                .requestId(requestId)
                .authorType(SupportRequestReply.AuthorType.ADMIN)
                .authorId(adminId)
                .body(req.getBody())
                .attachmentBase64(req.getAttachmentBase64())
                .attachmentContentType(req.getAttachmentContentType())
                .attachmentFilename(req.getAttachmentFilename())
                .build();
        replyRepository.save(reply);

        if (req.getNewStatus() != null) {
            sr.setStatus(req.getNewStatus());
        } else if (sr.getStatus() == SupportRequest.Status.OPEN) {
            sr.setStatus(SupportRequest.Status.IN_PROGRESS);
        }
        requestRepository.save(sr);

        return toDetailResponse(requestRepository.findById(requestId).orElseThrow());
    }

    public long countOpenRequests() {
        return requestRepository.countByStatus(SupportRequest.Status.OPEN);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .userId(m.getUserId())
                .subject(m.getSubject())
                .body(m.getBody())
                .type(m.getType())
                .isRead(m.isRead())
                .attachmentBase64(m.getAttachmentBase64())
                .attachmentContentType(m.getAttachmentContentType())
                .attachmentFilename(m.getAttachmentFilename())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private SupportRequestResponse toRequestResponse(SupportRequest sr) {
        return SupportRequestResponse.builder()
                .id(sr.getId())
                .userId(sr.getUserId())
                .type(sr.getType())
                .subject(sr.getSubject())
                .body(sr.getBody())
                .status(sr.getStatus())
                .hasAttachment(sr.getAttachmentBase64() != null)
                .createdAt(sr.getCreatedAt())
                .updatedAt(sr.getUpdatedAt())
                .build();
    }

    private SupportRequestDetailResponse toDetailResponse(SupportRequest sr) {
        List<SupportRequestDetailResponse.ReplyResponse> replies =
                replyRepository.findByRequestIdOrderByCreatedAtAsc(sr.getId())
                        .stream()
                        .map(r -> SupportRequestDetailResponse.ReplyResponse.builder()
                                .id(r.getId())
                                .authorType(r.getAuthorType())
                                .authorId(r.getAuthorId())
                                .body(r.getBody())
                                .attachmentBase64(r.getAttachmentBase64())
                                .attachmentContentType(r.getAttachmentContentType())
                                .attachmentFilename(r.getAttachmentFilename())
                                .createdAt(r.getCreatedAt())
                                .build())
                        .toList();

        return SupportRequestDetailResponse.builder()
                .id(sr.getId())
                .userId(sr.getUserId())
                .type(sr.getType())
                .subject(sr.getSubject())
                .body(sr.getBody())
                .status(sr.getStatus())
                .attachmentBase64(sr.getAttachmentBase64())
                .attachmentContentType(sr.getAttachmentContentType())
                .attachmentFilename(sr.getAttachmentFilename())
                .createdAt(sr.getCreatedAt())
                .updatedAt(sr.getUpdatedAt())
                .replies(replies)
                .build();
    }
}
