package com.inkflow.crm.module.request.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.common.util.PhoneUtils;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.RequestSpecifications;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.client.dto.ClientDto;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.notification.event.NewRequestEvent;
import com.inkflow.crm.module.request.dto.*;
import com.inkflow.crm.security.LocationScope;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestService {

    private final RequestRepository requestRepository;
    private final ClientRepository clientRepository;
    private final LocationRepository locationRepository;
    private final StaffRepository staffRepository;
    private final ClientMapper clientMapper;
    private final AuditRecorder auditRecorder;
    private final RequestMessageService requestMessageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResult<RequestDto> getAllRequests(PageRequest pageRequest, RequestFilterRequest filter) {
        Page<Request> page = getRequestsPage(pageRequest, filter);
        List<RequestDto> data = page.getContent().stream().map(this::toDto).toList();
        return new PageResult<>(data, PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public RequestDto getRequestById(UUID id) {
        return toDto(requireRequest(SecurityUtils.getCurrentTenantId(), id));
    }

    @Transactional
    public RequestDto createRequest(CreateRequestRequest createRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Client linkedClient = resolveOptionalClient(createRequest.getClientId());
        Staff assignedStaff = resolveOptionalStaff(createRequest.getAssignedStaffId());

        String clientName = firstNonBlank(createRequest.getClientName(), linkedClient != null ? linkedClient.getFullName() : null);
        if (clientName == null) {
            throw new BusinessRuleException("Client name or existing client is required");
        }

        String phone = firstNonBlank(createRequest.getPhone(), linkedClient != null ? linkedClient.getPhone() : null);
        String email = normalizeEmail(firstNonBlank(createRequest.getEmail(), linkedClient != null ? linkedClient.getEmail() : null));
        String instagram = firstNonBlank(createRequest.getInstagram(), linkedClient != null ? linkedClient.getInstagram() : null);

        Request request = Request.builder()
                .tenantId(tenantId)
                .source(RequestSource.fromValue(createRequest.getSource()))
                .clientName(clientName)
                .clientNickname(createRequest.getClientNickname())
                .message(createRequest.getMessage())
                .phone(phone)
                .email(email)
                .instagram(instagram)
                .sketchUrl(createRequest.getSketchUrl())
                .assignedStaff(assignedStaff)
                .tattooTiming(blankToNull(createRequest.getTattooTiming()))
                .tattooSize(blankToNull(createRequest.getTattooSize()))
                .bodyZones(joinBodyZones(createRequest.getBodyZones()))
                .isCoverUp(createRequest.getIsCoverUp())
                .idea(blankToNull(createRequest.getIdea()))
                .referenceUrls(joinReferenceUrls(createRequest.getReferences()))
                .city(blankToNull(createRequest.getCity()))
                .contactMethod(blankToNull(createRequest.getContactMethod()))
                .contactValue(blankToNull(createRequest.getContactValue()))
                .location(resolveLocation(assignedStaff))
                .status(RequestStatus.NEW)
                .build();

        request = requestRepository.save(request);
        requestMessageService.seedInitialThread(request);

        if (assignedStaff != null) {
            eventPublisher.publishEvent(new NewRequestEvent(
                    request.getId(),
                    tenantId,
                    assignedStaff.getId(),
                    request.getClientName(),
                    request.getIdea()
            ));
        }

        log.info("Request created: tenantId={} requestId={} assignedStaffId={} clientId={}",
                tenantId, request.getId(),
                assignedStaff != null ? assignedStaff.getId() : null,
                linkedClient != null ? linkedClient.getId() : null);
        auditRecorder.record(
                AuditAction.CREATE,
                AuditEntityType.REQUEST,
                request.getId().toString(),
                request.getClientName()
        );
        return toDto(request);
    }

    @Transactional
    public RequestDto updateAssignment(UUID id, UpdateRequestAssignmentRequest updateRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Request request = requireRequest(tenantId, id);
        Staff assignedStaff = resolveOptionalStaff(updateRequest.getAssignedStaffId());

        request.setAssignedStaff(assignedStaff);
        if (request.getLocation() == null && assignedStaff != null) {
            request.setLocation(resolveLocation(assignedStaff));
        }

        request = requestRepository.save(request);
        log.info("Request assignment updated: tenantId={} requestId={} assignedStaffId={}",
                tenantId, id, assignedStaff != null ? assignedStaff.getId() : null);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.REQUEST,
                id.toString(),
                request.getClientName(),
                null,
                assignedStaff != null ? assignedStaff.getFullName() : "unassigned"
        );
        return toDto(request);
    }

    @Transactional
    public RequestDto updateRequestStatus(UUID id, UpdateRequestStatusRequest updateRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Request request = requireRequest(tenantId, id);
        RequestStatus previousStatus = request.getStatus();
        RequestStatus newStatus = RequestStatus.fromValue(updateRequest.getStatus());

        switch (newStatus) {
            case REPLIED -> request.markAsReplied();
            case SPAM -> request.markAsSpam();
            default -> request.setStatus(newStatus);
        }

        request = requestRepository.save(request);
        log.info("Request status updated: tenantId={} requestId={} status={}", tenantId, id, newStatus.getValue());
        auditRecorder.record(
                AuditAction.STATUS_CHANGE,
                AuditEntityType.REQUEST,
                id.toString(),
                request.getClientName(),
                null,
                previousStatus.getValue() + " → " + newStatus.getValue()
        );
        return toDto(request);
    }

    @Transactional
    public ClientDto convertToClient(UUID requestId, ConvertRequestRequest convertRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Request request = requireRequest(tenantId, requestId);

        if (request.getStatus() == RequestStatus.CONVERTED) {
            throw new BusinessRuleException("Request has already been converted");
        }

        String email = resolveConversionEmail(request, convertRequest);
        if (email == null) {
            throw new BusinessRuleException("Email is required");
        }

        Client existingByEmail = clientRepository
                .findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElse(null);
        if (existingByEmail != null) {
            if (existingByEmail.isBlacklisted()) {
                throw BusinessRuleException.clientBlacklisted();
            }
            request.markAsConverted(existingByEmail);
            requestRepository.save(request);
            log.info("Request linked to existing client by email: tenantId={} requestId={} clientId={}",
                    tenantId, requestId, existingByEmail.getId());
            auditRecorder.record(
                    AuditAction.CONVERT,
                    AuditEntityType.REQUEST,
                    requestId.toString(),
                    request.getClientName(),
                    existingByEmail.getId()
            );
            return clientMapper.toDto(existingByEmail);
        }

        String normalizedPhone = resolveConversionPhone(request, convertRequest);
        if (normalizedPhone != null
                && clientRepository.existsByPhoneAndDeletedAtIsNull(normalizedPhone)) {
            throw BusinessRuleException.phoneAlreadyExists(normalizedPhone);
        }

        if (clientRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw BusinessRuleException.emailAlreadyExists(email);
        }

        Client client = Client.builder()
                .tenantId(tenantId)
                .firstName(convertRequest.getFirstName())
                .lastName(convertRequest.getLastName())
                .phone(normalizedPhone)
                .email(email)
                .instagram(convertRequest.getInstagram())
                .telegram(convertRequest.getTelegram())
                .source(request.getSource())
                .totalVisits(0)
                .cancelledVisits(0)
                .ltv(BigDecimal.ZERO)
                .build();

        client = clientRepository.save(client);
        request.markAsConverted(client);
        requestRepository.save(request);

        log.info("Request converted to client: tenantId={} requestId={} clientId={}", tenantId, requestId, client.getId());
        auditRecorder.record(
                AuditAction.CONVERT,
                AuditEntityType.REQUEST,
                requestId.toString(),
                request.getClientName(),
                client.getId()
        );
        auditRecorder.record(
                AuditAction.CREATE,
                AuditEntityType.CLIENT,
                client.getId().toString(),
                client.getFullName(),
                client.getId()
        );
        return clientMapper.toDto(client);
    }

    @Transactional
    public void deleteRequest(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Request request = requireRequest(tenantId, id);
        String label = request.getClientName();
        requestRepository.delete(request);
        log.info("Request deleted: tenantId={} requestId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.DELETE,
                AuditEntityType.REQUEST,
                id.toString(),
                label
        );
    }

    private Page<Request> getRequestsPage(PageRequest pageRequest, RequestFilterRequest filter) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        RequestFilterRequest effectiveFilter = filter != null ? filter : new RequestFilterRequest();

        RequestStatus requestStatus = effectiveFilter.getStatus() != null
                ? RequestStatus.fromValue(effectiveFilter.getStatus())
                : null;
        List<RequestSource> requestSources = effectiveFilter.getSource() != null && !effectiveFilter.getSource().isEmpty()
                ? effectiveFilter.getSource().stream().map(RequestSource::fromValue).toList()
                : null;
        UUID effectiveLocationId = LocationScope.resolveFilter(effectiveFilter.getLocationId()).orElse(null);

        Specification<Request> spec = Specification.where(RequestSpecifications.statusIs(requestStatus))
                .and(RequestSpecifications.sourceIn(requestSources))
                .and(RequestSpecifications.createdBetween(effectiveFilter.getFrom(), effectiveFilter.getTo()))
                .and(RequestSpecifications.locationIs(effectiveLocationId))
                .and(RequestSpecifications.searchLike(effectiveFilter.getSearch()))
                .and(RequestSpecifications.cityEquals(effectiveFilter.getCity()))
                .and(RequestSpecifications.tattooSizeEquals(effectiveFilter.getTattooSize()))
                .and(RequestSpecifications.tattooTimingEquals(effectiveFilter.getTattooTiming()))
                .and(RequestSpecifications.isCoverUpEquals(effectiveFilter.getIsCoverUp()))
                .and(RequestSpecifications.hasSketch(effectiveFilter.getHasSketch()))
                .and(RequestSpecifications.hasReferences(effectiveFilter.getHasReferences()))
                .and(RequestSpecifications.bodyZoneContains(effectiveFilter.getBodyZone()))
                .and(RequestSpecifications.assignedStaffIs(effectiveFilter.getStaffId()));

        return requestRepository.findAll(spec, pageRequest.toPageable());
    }

    private Request requireRequest(UUID tenantId, UUID id) {
        return requestRepository.findVisibleById(id)
                .orElseThrow(() -> ResourceNotFoundException.request(id.toString()));
    }

    private RequestDto toDto(Request request) {
        MatchedClient matchedClient = resolveMatchedClient(request);

        return RequestDto.builder()
                .id(request.getId())
                .source(request.getSource().getValue())
                .clientName(request.getClientName())
                .clientNickname(request.getClientNickname())
                .message(request.getMessage())
                .phone(request.getPhone())
                .email(request.getEmail())
                .instagram(request.getInstagram())
                .status(request.getStatus().getValue())
                .convertedClientId(request.getConvertedClientId())
                .matchedClientId(matchedClient.id())
                .matchedClientName(matchedClient.name())
                .matchedClientAvatar(matchedClient.avatar())
                .matchedClientBlacklisted(matchedClient.blacklisted())
                .createdAt(request.getCreatedAt())
                .repliedAt(request.getRepliedAt())
                .convertedAt(request.getConvertedAt())
                .sketchUrl(request.getSketchUrl())
                .idea(request.getIdea())
                .city(request.getCity())
                .tattooSize(request.getTattooSize())
                .tattooTiming(request.getTattooTiming())
                .isCoverUp(request.getIsCoverUp())
                .bodyZones(parseBodyZones(request.getBodyZones()))
                .referenceUrls(parseReferenceUrls(request.getReferenceUrls()))
                .contactMethod(request.getContactMethod())
                .contactValue(request.getContactValue())
                .assignedStaffId(request.getAssignedStaff() != null ? request.getAssignedStaff().getId() : null)
                .assignedStaffName(request.getAssignedStaff() != null ? request.getAssignedStaff().getFullName() : null)
                .build();
    }

    private MatchedClient resolveMatchedClient(Request request) {
        if (request.getConvertedClient() != null) {
            Client client = request.getConvertedClient();
            return new MatchedClient(client.getId(), client.getFullName(), client.isBlacklisted(), client.getAvatar());
        }

        String email = normalizeEmail(request.getEmail());
        if (email == null) {
            return MatchedClient.empty();
        }

        return clientRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .map(client -> new MatchedClient(client.getId(), client.getFullName(), client.isBlacklisted(), client.getAvatar()))
                .orElse(MatchedClient.empty());
    }

    private String resolveConversionEmail(Request request, ConvertRequestRequest convertRequest) {
        String email = normalizeEmail(convertRequest.getEmail());
        if (email != null) {
            return email;
        }
        return normalizeEmail(request.getEmail());
    }

    private String resolveConversionPhone(Request request, ConvertRequestRequest convertRequest) {
        String rawPhone = convertRequest.getPhone();
        if (rawPhone == null || rawPhone.isBlank()) {
            rawPhone = request.getPhone();
        }
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String normalized = PhoneUtils.normalize(rawPhone);
        return normalized.isBlank() ? null : normalized;
    }

    private Location resolveLocation(Staff assignedStaff) {
        if (assignedStaff != null && assignedStaff.getLocations() != null && !assignedStaff.getLocations().isEmpty()) {
            return assignedStaff.getLocations().iterator().next();
        }
        return resolveDefaultLocation();
    }

    private Location resolveDefaultLocation() {
        return locationRepository.findByIsActiveAndDeletedAtIsNull(true).stream()
                .findFirst()
                .orElseGet(() -> locationRepository.findByDeletedAtIsNull().stream()
                        .findFirst()
                        .orElse(null));
    }

    private Staff resolveOptionalStaff(UUID staffId) {
        if (staffId == null) {
            return null;
        }
        return staffRepository.findByIdAndDeletedAtIsNull(staffId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));
    }

    private Client resolveOptionalClient(UUID clientId) {
        if (clientId == null) {
            return null;
        }
        Client client = clientRepository.findByIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> ResourceNotFoundException.client(clientId.toString()));
        if (client.isBlacklisted()) {
            throw BusinessRuleException.clientBlacklisted();
        }
        return client;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        String normalizedPrimary = blankToNull(primary);
        if (normalizedPrimary != null) {
            return normalizedPrimary;
        }
        return blankToNull(fallback);
    }

    private String joinBodyZones(List<String> bodyZones) {
        if (bodyZones == null || bodyZones.isEmpty()) {
            return null;
        }
        List<String> values = bodyZones.stream()
                .map(this::blankToNull)
                .filter(value -> value != null)
                .toList();
        return values.isEmpty() ? null : String.join(",", values);
    }

    private String joinReferenceUrls(List<String> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        List<String> values = references.stream()
                .map(this::blankToNull)
                .filter(value -> value != null)
                .toList();
        return values.isEmpty() ? null : String.join("|", values);
    }

    private List<String> parseBodyZones(String bodyZones) {
        if (bodyZones == null || bodyZones.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(bodyZones.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private List<String> parseReferenceUrls(String referenceUrls) {
        if (referenceUrls == null || referenceUrls.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(referenceUrls.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private record MatchedClient(UUID id, String name, boolean blacklisted, String avatar) {
        static MatchedClient empty() {
            return new MatchedClient(null, null, false, null);
        }
    }
}
