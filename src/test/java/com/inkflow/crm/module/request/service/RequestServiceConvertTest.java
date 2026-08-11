package com.inkflow.crm.module.request.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.client.dto.ClientDto;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import com.inkflow.crm.module.request.dto.ConvertRequestRequest;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceConvertTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientMapper clientMapper;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private ConsumerUserRepository consumerUserRepository;

    @InjectMocks
    private RequestService requestService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void convertToClient_createsClientAndMarksRequestConverted() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        authenticate(tenantId);

        Request request = Request.builder()
                .id(requestId)
                .tenantId(tenantId)
                .source(RequestSource.INSTAGRAM)
                .clientName("Jane")
                .status(RequestStatus.NEW)
                .build();

        when(requestRepository.findVisibleById(requestId)).thenReturn(Optional.of(request));
        when(clientRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("jane.doe@example.com"))
                .thenReturn(Optional.empty());
        when(clientRepository.existsByPhoneAndDeletedAtIsNull("+380991234567")).thenReturn(false);
        when(clientRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("jane.doe@example.com"))
                .thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client client = invocation.getArgument(0);
            client.setId(UUID.randomUUID());
            return client;
        });
        when(requestRepository.save(request)).thenReturn(request);
        when(clientMapper.toDto(any(Client.class))).thenReturn(ClientDto.builder().firstName("Jane").build());

        ConvertRequestRequest convertRequest = ConvertRequestRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("+380991234567")
                .build();

        requestService.convertToClient(requestId, convertRequest);

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());
        Client savedClient = clientCaptor.getValue();
        assertEquals("Jane", savedClient.getFirstName());
        assertEquals("+380991234567", savedClient.getPhone());
        assertEquals(false, savedClient.isBlacklisted());
        assertEquals(BigDecimal.ZERO, savedClient.getLtv());

        assertEquals(RequestStatus.CONVERTED, request.getStatus());
        assertNotNull(request.getConvertedAt());
        assertEquals(savedClient, request.getConvertedClient());
    }

    @Test
    void convertToClient_rejectsAlreadyConvertedRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        authenticate(tenantId);

        Request request = Request.builder()
                .id(requestId)
                .tenantId(tenantId)
                .status(RequestStatus.CONVERTED)
                .build();

        when(requestRepository.findVisibleById(requestId)).thenReturn(Optional.of(request));

        ConvertRequestRequest convertRequest = ConvertRequestRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("+380991234567")
                .build();

        assertThrows(BusinessRuleException.class,
                () -> requestService.convertToClient(requestId, convertRequest));
    }

    @Test
    void convertToClient_rejectsDuplicatePhone() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        authenticate(tenantId);

        Request request = Request.builder()
                .id(requestId)
                .tenantId(tenantId)
                .status(RequestStatus.NEW)
                .build();

        when(requestRepository.findVisibleById(requestId)).thenReturn(Optional.of(request));
        when(clientRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("jane.doe@example.com"))
                .thenReturn(Optional.empty());
        when(clientRepository.existsByPhoneAndDeletedAtIsNull(eq("+380991234567")))
                .thenReturn(true);

        ConvertRequestRequest convertRequest = ConvertRequestRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("+380991234567")
                .build();

        assertThrows(BusinessRuleException.class,
                () -> requestService.convertToClient(requestId, convertRequest));
    }

    @Test
    void convertToClient_usesRequestEmailAndAllowsEmptyLastName() {
        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        authenticate(tenantId);

        Request request = Request.builder()
                .id(requestId)
                .tenantId(tenantId)
                .source(RequestSource.APP)
                .clientName("Jane")
                .email("jane@example.com")
                .status(RequestStatus.NEW)
                .build();

        when(requestRepository.findVisibleById(requestId)).thenReturn(Optional.of(request));
        when(clientRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("jane@example.com"))
                .thenReturn(Optional.empty());
        when(clientRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("jane@example.com"))
                .thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client client = invocation.getArgument(0);
            client.setId(UUID.randomUUID());
            return client;
        });
        when(requestRepository.save(request)).thenReturn(request);
        when(clientMapper.toDto(any(Client.class))).thenReturn(ClientDto.builder().firstName("Jane").build());

        ConvertRequestRequest convertRequest = ConvertRequestRequest.builder()
                .firstName("Jane")
                .build();

        requestService.convertToClient(requestId, convertRequest);

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());
        Client savedClient = clientCaptor.getValue();
        assertEquals("Jane", savedClient.getFirstName());
        assertEquals("", savedClient.getLastName());
        assertEquals("jane@example.com", savedClient.getEmail());
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
