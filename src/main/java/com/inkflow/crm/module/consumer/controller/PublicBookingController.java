package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.consumer.dto.PublicBookingRequest;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.notification.event.NewRequestEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/public/consumer/requests")
@RequiredArgsConstructor
public class PublicBookingController {

    private final RequestRepository requestRepository;
    private final StaffRepository staffRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping
    public ResponseEntity<?> submitBookingRequest(
            @AuthenticationPrincipal ConsumerUser consumer,
            @Valid @RequestBody PublicBookingRequest body) {

        Staff artist = staffRepository.findPublicArtistById(body.artistId())
                .orElse(null);

        if (artist == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Artist not found"));
        }

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

        request = requestRepository.save(request);
        log.info("New booking request {} from consumer for artist {}", request.getId(), artist.getFullName());

        eventPublisher.publishEvent(new NewRequestEvent(
                request.getId(),
                artist.getTenantId(),
                artist.getId(),
                body.clientName(),
                body.idea()
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "id", request.getId(),
                        "status", "new",
                        "artistName", artist.getFullName()
                ));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyRequests(@AuthenticationPrincipal ConsumerUser consumer) {
        if (consumer == null) return ResponseEntity.status(401).build();

        var requests = requestRepository.findByConsumerUserIdOrderByCreatedAtDesc(consumer.getId());
        var dtos = requests.stream().map(r -> Map.of(
                "id", r.getId().toString(),
                "artistName", r.getAssignedStaff() != null ? r.getAssignedStaff().getFullName() : "—",
                "status", r.getStatus().getValue(),
                "idea", r.getIdea() != null ? r.getIdea() : "",
                "city", r.getCity() != null ? r.getCity() : "",
                "createdAt", r.getCreatedAt().toString()
        )).toList();

        return ResponseEntity.ok(dtos);
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
