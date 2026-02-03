package com.inkflow.crm.module.request.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.enums.ClientStatus;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.module.client.dto.ClientDto;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.request.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    @Transactional(readOnly = true)
    public List<RequestDto> getAllRequests(PageRequest pageRequest, String status, String source, java.time.Instant from, java.time.Instant to, UUID locationId) {
        Page<Request> page = getRequestsPage(pageRequest, status, source, from, to, locationId);
        return page.getContent().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public PaginationDto getPagination(PageRequest pageRequest, String status, String source, java.time.Instant from, java.time.Instant to, UUID locationId) {
        Page<Request> page = getRequestsPage(pageRequest, status, source, from, to, locationId);
        return PaginationDto.from(page);
    }

    private Page<Request> getRequestsPage(PageRequest pageRequest, String status, String source, java.time.Instant from, java.time.Instant to, UUID locationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        RequestStatus requestStatus = status != null ? RequestStatus.fromValue(status) : null;
        RequestSource requestSource = source != null ? RequestSource.fromValue(source) : null;
        return requestRepository.findWithFilters(tenantId, requestStatus, requestSource, from, to, locationId, pageRequest.toPageable());
    }

    @Transactional(readOnly = true)
    public RequestDto getRequestById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Request request = requestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.request(id.toString()));
        return mapToDto(request);
    }

    @Transactional
    public RequestDto createRequest(CreateRequestRequest createRequest) {
        UUID tenantId = createRequest.getTenantId();
        if (tenantId == null) {
            tenantId = SecurityUtils.getCurrentTenantId();
        }

        Request request = Request.builder()
                .tenantId(tenantId)
                .source(RequestSource.fromValue(createRequest.getSource()))
                .clientName(createRequest.getClientName())
                .clientNickname(createRequest.getClientNickname())
                .message(createRequest.getMessage())
                .phone(createRequest.getPhone())
                .instagram(createRequest.getInstagram())
                .status(RequestStatus.NEW)
                .build();

        request = requestRepository.save(request);
        return mapToDto(request);
    }

    @Transactional
    public RequestDto updateRequestStatus(UUID id, UpdateRequestStatusRequest updateRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Request request = requestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.request(id.toString()));

        RequestStatus newStatus = RequestStatus.fromValue(updateRequest.getStatus());

        switch (newStatus) {
            case REPLIED -> request.markAsReplied();
            case SPAM -> request.markAsSpam();
            default -> request.setStatus(newStatus);
        }

        request = requestRepository.save(request);
        return mapToDto(request);
    }

    @Transactional
    public ClientDto convertToClient(UUID requestId, ConvertRequestRequest convertRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Request request = requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.request(requestId.toString()));

        if (request.getStatus() == RequestStatus.CONVERTED) {
            throw new BusinessRuleException("Request has already been converted");
        }

        String normalizedPhone = convertRequest.getPhone().replaceAll("[^0-9+]", "");
        if (clientRepository.existsByPhoneAndTenantIdAndDeletedAtIsNull(normalizedPhone, tenantId)) {
            throw BusinessRuleException.phoneAlreadyExists(normalizedPhone);
        }

        Client client = Client.builder()
                .tenantId(tenantId)
                .firstName(convertRequest.getFirstName())
                .lastName(convertRequest.getLastName())
                .phone(normalizedPhone)
                .email(convertRequest.getEmail())
                .instagram(convertRequest.getInstagram())
                .telegram(convertRequest.getTelegram())
                .source(request.getSource())
                .status(ClientStatus.ACTIVE)
                .totalVisits(0)
                .cancelledVisits(0)
                .ltv(BigDecimal.ZERO)
                .build();

        client = clientRepository.save(client);

        request.markAsConverted(client);
        requestRepository.save(request);

        return clientMapper.toDto(client);
    }

    @Transactional
    public void deleteRequest(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Request request = requestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.request(id.toString()));
        requestRepository.delete(request);
    }

    private RequestDto mapToDto(Request request) {
        return RequestDto.builder()
                .id(request.getId())
                .source(request.getSource().getValue())
                .clientName(request.getClientName())
                .clientNickname(request.getClientNickname())
                .message(request.getMessage())
                .phone(request.getPhone())
                .instagram(request.getInstagram())
                .status(request.getStatus().getValue())
                .convertedClientId(request.getConvertedClientId())
                .createdAt(request.getCreatedAt())
                .repliedAt(request.getRepliedAt())
                .convertedAt(request.getConvertedAt())
                .build();
    }
}
