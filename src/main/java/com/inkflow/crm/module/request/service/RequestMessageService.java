package com.inkflow.crm.module.request.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.RequestMessage;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.RequestMessageSenderType;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.RequestMessageRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.notification.event.ClientRequestMessageEvent;
import com.inkflow.crm.module.request.dto.CreateRequestMessageRequest;
import com.inkflow.crm.module.request.dto.RequestMessageDto;
import com.inkflow.crm.module.request.mapper.RequestMessageMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestMessageService {

    private static final String REQUEST_CREATED_MESSAGE = "Заявка створена";
    private static final String IMAGE_PREVIEW = "Фото";

    private final RequestRepository requestRepository;
    private final RequestMessageRepository requestMessageRepository;
    private final StaffRepository staffRepository;
    private final RequestMessageMapper requestMessageMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<RequestMessageDto> getMessagesForStaff(UUID requestId) {
        Request request = requireTenantRequest(SecurityUtils.getCurrentTenantId(), requestId);
        return toDtoList(requestMessageRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()));
    }

    @Transactional
    public RequestMessageDto sendStaffMessage(UUID requestId, CreateRequestMessageRequest body) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID staffId = SecurityUtils.getCurrentUserId();
        Request request = requireTenantRequest(tenantId, requestId);
        Staff staff = staffRepository.findByIdAndDeletedAtIsNull(staffId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        MessageContent content = resolveMessageContent(body);

        RequestMessage message = saveMessage(
                tenantId,
                request.getId(),
                RequestMessageSenderType.STAFF,
                staff.getId(),
                staff.getFullName(),
                content.body(),
                content.imageUrl(),
                Instant.now()
        );

        if (request.getStatus() == RequestStatus.NEW) {
            request.markAsReplied();
            requestRepository.save(request);
        }

        log.info("Request message sent by staff: tenantId={} requestId={} messageId={} hasImage={}",
                tenantId, requestId, message.getId(), content.imageUrl() != null);

        return requestMessageMapper.toDto(message);
    }

    @Transactional(readOnly = true)
    public List<RequestMessageDto> getMessagesForConsumer(ConsumerUser consumer, UUID requestId) {
        Request request = requireConsumerRequest(consumer, requestId);
        return toDtoList(requestMessageRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()));
    }

    @Transactional
    public RequestMessageDto sendConsumerMessage(ConsumerUser consumer, UUID requestId, CreateRequestMessageRequest body) {
        Request request = requireConsumerRequest(consumer, requestId);
        MessageContent content = resolveMessageContent(body);

        RequestMessage message = saveMessage(
                request.getTenantId(),
                request.getId(),
                RequestMessageSenderType.CLIENT,
                null,
                request.getClientName(),
                content.body(),
                content.imageUrl(),
                Instant.now()
        );

        publishClientMessageNotification(request, content.notificationPreview());

        log.info("Request message sent by consumer: requestId={} consumerId={} messageId={} hasImage={}",
                requestId, consumer.getId(), message.getId(), content.imageUrl() != null);

        return requestMessageMapper.toDto(message);
    }

    @Transactional(readOnly = true)
    public Request requireConsumerOwnedRequest(ConsumerUser consumer, UUID requestId) {
        return requireConsumerRequest(consumer, requestId);
    }

    private void publishClientMessageNotification(Request request, String preview) {
        Staff assignedStaff = request.getAssignedStaff();
        if (assignedStaff == null) {
            log.warn("Skipping client-message push: request {} has no assigned staff", request.getId());
            return;
        }

        eventPublisher.publishEvent(new ClientRequestMessageEvent(
                request.getId(),
                request.getTenantId(),
                assignedStaff.getId(),
                request.getClientName(),
                preview
        ));
    }

    @Transactional
    public void seedInitialThread(Request request) {
        if (!requestMessageRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()).isEmpty()) {
            return;
        }

        Instant createdAt = request.getCreatedAt() != null ? request.getCreatedAt() : Instant.now();

        saveMessage(
                request.getTenantId(),
                request.getId(),
                RequestMessageSenderType.SYSTEM,
                null,
                null,
                REQUEST_CREATED_MESSAGE,
                null,
                createdAt
        );

        if (request.getMessage() != null && !request.getMessage().isBlank()) {
            saveMessage(
                    request.getTenantId(),
                    request.getId(),
                    RequestMessageSenderType.CLIENT,
                    null,
                    request.getClientName(),
                    request.getMessage().trim(),
                    null,
                    createdAt.plusMillis(1)
            );
        }
    }

    private Request requireTenantRequest(UUID tenantId, UUID requestId) {
        Request request = requestRepository.findVisibleById(requestId)
                .orElseThrow(() -> ResourceNotFoundException.request(requestId.toString()));

        if (!tenantId.equals(request.getTenantId())) {
            throw ResourceNotFoundException.request(requestId.toString());
        }

        return request;
    }

    private Request requireConsumerRequest(ConsumerUser consumer, UUID requestId) {
        Request request = requestRepository.findVisibleById(requestId)
                .orElseThrow(() -> ResourceNotFoundException.request(requestId.toString()));

        if (consumer == null || request.getConsumerUserId() == null || !request.getConsumerUserId().equals(consumer.getId())) {
            throw ResourceNotFoundException.request(requestId.toString());
        }

        return request;
    }

    private MessageContent resolveMessageContent(CreateRequestMessageRequest request) {
        String body = blankToNull(request.getBody());
        String imageUrl = blankToNull(request.getImageUrl());

        if (body == null && imageUrl == null) {
            throw new BusinessRuleException("Message body or image is required");
        }

        return new MessageContent(body, imageUrl);
    }

    private RequestMessage saveMessage(
            UUID tenantId,
            UUID requestId,
            RequestMessageSenderType senderType,
            UUID senderStaffId,
            String senderName,
            String body,
            String imageUrl,
            Instant createdAt) {
        RequestMessage message = RequestMessage.builder()
                .tenantId(tenantId)
                .requestId(requestId)
                .senderType(senderType)
                .senderStaffId(senderStaffId)
                .senderName(senderName)
                .body(body)
                .imageUrl(imageUrl)
                .createdAt(createdAt)
                .build();

        return requestMessageRepository.save(message);
    }

    private List<RequestMessageDto> toDtoList(List<RequestMessage> messages) {
        return messages.stream().map(requestMessageMapper::toDto).toList();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record MessageContent(String body, String imageUrl) {
        String notificationPreview() {
            if (body != null) {
                return body;
            }
            return IMAGE_PREVIEW;
        }
    }
}
