package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.consumer.dto.ConsumerAttachmentDto;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingListItemDto;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingResultDto;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingRequest;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.request.dto.CreateRequestMessageRequest;
import com.inkflow.crm.module.request.dto.RequestMessageDto;
import com.inkflow.crm.module.request.service.RequestMessageService;
import com.inkflow.crm.module.notification.event.NewRequestEvent;
import com.inkflow.crm.module.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@BypassTenantFilter
@RequiredArgsConstructor
@Slf4j
public class ConsumerBookingService {

    private static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    private final RequestRepository requestRepository;
    private final StaffRepository staffRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RequestMessageService requestMessageService;
    private final FileStorageService fileStorageService;

    @Transactional
    public ConsumerBookingResultDto submitBookingRequest(ConsumerUser consumer, ConsumerBookingRequest body) {
        Staff artist = staffRepository.findPublicArtistById(body.artistId())
                .orElseThrow(() -> new ApiException(ErrorCode.STAFF_NOT_FOUND, "Artist not found"));

        Request request = buildRequest(body, artist, consumer);
        request = requestRepository.save(request);
        requestMessageService.seedInitialThread(request);

        log.info("New booking request {} from consumer for artist {}", request.getId(), artist.getFullName());

        eventPublisher.publishEvent(new NewRequestEvent(
                request.getId(),
                artist.getTenantId(),
                artist.getId(),
                body.clientName(),
                body.idea()
        ));

        return new ConsumerBookingResultDto(
                request.getId(),
                RequestStatus.NEW.getValue(),
                artist.getFullName()
        );
    }

    @Transactional(readOnly = true)
    public List<ConsumerBookingListItemDto> getMyRequests(ConsumerUser consumer) {
        ApiResponses.requireConsumer(consumer);

        return requestRepository.findByConsumerUserIdOrderByCreatedAtDesc(consumer.getId())
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequestMessageDto> getRequestMessages(ConsumerUser consumer, UUID requestId) {
        return requestMessageService.getMessagesForConsumer(consumer, requestId);
    }

    @Transactional
    public RequestMessageDto sendRequestMessage(
            ConsumerUser consumer,
            UUID requestId,
            CreateRequestMessageRequest body) {
        return requestMessageService.sendConsumerMessage(consumer, requestId, body);
    }

    @Transactional(readOnly = true)
    public ConsumerAttachmentDto uploadRequestAttachment(
            ConsumerUser consumer,
            UUID requestId,
            MultipartFile file) throws IOException {
        Request request = requestMessageService.requireConsumerOwnedRequest(consumer, requestId);
        validateAttachment(file);

        String url = fileStorageService.uploadFileForTenant(
                request.getTenantId(),
                FileStorageService.REQUEST_MESSAGES_FOLDER,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize()
        );

        log.info("Consumer request attachment uploaded: requestId={} consumerId={} size={}",
                requestId, consumer.getId(), file.getSize());

        return new ConsumerAttachmentDto(url);
    }

    private void validateAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "File is required");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "File too large. Max size is 5MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Only image uploads are supported");
        }
    }

    private Request buildRequest(ConsumerBookingRequest body, Staff artist, ConsumerUser consumer) {
        Request request = Request.builder()
                .tenantId(artist.getTenantId())
                .source(RequestSource.APP)
                .clientName(body.clientName())
                .phone(body.phone())
                .instagram(body.instagram())
                .message(buildMessage(body))
                .status(RequestStatus.NEW)
                .assignedStaff(artist)
                .consumerUserId(consumer != null ? consumer.getId() : null)
                .tattooTiming(body.timing())
                .tattooSize(body.size())
                .bodyZones(body.bodyZones() != null ? String.join(",", body.bodyZones()) : null)
                .isCoverUp(body.isCoverUp())
                .idea(body.idea())
                .referenceUrls(body.references() != null ? String.join("|", body.references()) : null)
                .city(body.city())
                .contactMethod(body.contactMethod())
                .contactValue(body.contactValue())
                .build();

        if (artist.getLocations() != null && !artist.getLocations().isEmpty()) {
            request.setLocation(artist.getLocations().iterator().next());
        }

        return request;
    }

    private ConsumerBookingListItemDto toListItem(Request request) {
        String artistName = request.getAssignedStaff() != null
                ? request.getAssignedStaff().getFullName()
                : "—";

        return new ConsumerBookingListItemDto(
                request.getId(),
                artistName,
                request.getStatus().getValue(),
                request.getIdea() != null ? request.getIdea() : "",
                request.getCity() != null ? request.getCity() : "",
                request.getCreatedAt()
        );
    }

    private String buildMessage(ConsumerBookingRequest body) {
        StringBuilder sb = new StringBuilder();

        if (body.idea() != null && !body.idea().isBlank()) {
            sb.append("Ідея: ").append(body.idea());
        }
        if (body.timing() != null) {
            sb.append("\nТерміни: ").append(body.timing());
        }
        if (body.size() != null) {
            sb.append("\nРозмір: ").append(body.size());
        }
        if (body.bodyZones() != null && !body.bodyZones().isEmpty()) {
            sb.append("\nМісце: ").append(String.join(", ", body.bodyZones()));
        }
        if (Boolean.TRUE.equals(body.isCoverUp())) {
            sb.append("\n(Перекриття)");
        }

        return sb.toString().trim();
    }
}
