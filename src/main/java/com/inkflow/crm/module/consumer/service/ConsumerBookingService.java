package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingListItemDto;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingResultDto;
import com.inkflow.crm.module.consumer.dto.PublicBookingRequest;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.notification.event.NewRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsumerBookingService {

    private final RequestRepository requestRepository;
    private final StaffRepository staffRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ConsumerBookingResultDto submitBookingRequest(ConsumerUser consumer, PublicBookingRequest body) {
        Staff artist = staffRepository.findPublicArtistById(body.artistId())
                .orElseThrow(() -> new ApiException(ErrorCode.STAFF_NOT_FOUND, "Artist not found"));

        Request request = buildRequest(body, artist, consumer);
        request = requestRepository.save(request);

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
        if (consumer == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        return requestRepository.findByConsumerUserIdOrderByCreatedAtDesc(consumer.getId())
                .stream()
                .map(this::toListItem)
                .toList();
    }

    private Request buildRequest(PublicBookingRequest body, Staff artist, ConsumerUser consumer) {
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

    private String buildMessage(PublicBookingRequest body) {
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
