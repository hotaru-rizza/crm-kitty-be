package com.inkflow.crm.module.request.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.request.dto.UpdateRequestStatusRequest;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceStatusTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientMapper clientMapper;

    @InjectMocks
    private RequestService requestService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateRequestStatus_changesStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        authenticate(tenantId);

        Request request = Request.builder()
                .id(requestId)
                .tenantId(tenantId)
                .source(RequestSource.WEBSITE)
                .status(RequestStatus.NEW)
                .build();

        when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                .thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);

        UpdateRequestStatusRequest update = UpdateRequestStatusRequest.builder()
                .status("replied")
                .build();

        requestService.updateRequestStatus(requestId, update);

        assertEquals(RequestStatus.REPLIED, request.getStatus());
        verify(requestRepository).save(request);
    }

    @Test
    void updateRequestStatus_markAsRepliedSetsRepliedAt() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        authenticate(tenantId);

        Request request = Request.builder()
                .id(requestId)
                .tenantId(tenantId)
                .source(RequestSource.WEBSITE)
                .status(RequestStatus.NEW)
                .build();

        when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                .thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);

        requestService.updateRequestStatus(requestId, UpdateRequestStatusRequest.builder()
                .status("replied")
                .build());

        assertEquals(RequestStatus.REPLIED, request.getStatus());
        assertNotNull(request.getRepliedAt());
    }

    @Test
    void updateRequestStatus_markAsSpamSetsSpamStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        authenticate(tenantId);

        Request request = Request.builder()
                .id(requestId)
                .tenantId(tenantId)
                .source(RequestSource.WEBSITE)
                .status(RequestStatus.NEW)
                .build();

        when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                .thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);

        requestService.updateRequestStatus(requestId, UpdateRequestStatusRequest.builder()
                .status("spam")
                .build());

        assertEquals(RequestStatus.SPAM, request.getStatus());
    }

    @Test
    void updateRequestStatus_rejectsForeignTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        authenticate(tenantId);

        when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                .thenReturn(Optional.empty());

        UpdateRequestStatusRequest update = UpdateRequestStatusRequest.builder()
                .status("spam")
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> requestService.updateRequestStatus(requestId, update));
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
