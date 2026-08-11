package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingRequest;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingResultDto;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.notification.event.NewRequestEvent;
import com.inkflow.crm.module.request.service.RequestMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerBookingServiceTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RequestMessageService requestMessageService;

    @InjectMocks
    private ConsumerBookingService consumerBookingService;

    @Test
    void submitBookingRequest_createsRequestAndPublishesEvent() {
        UUID artistId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();

        Staff artist = Staff.builder()
                .id(artistId)
                .tenantId(tenantId)
                .firstName("Alex")
                .lastName("Ink")
                .build();

        ConsumerUser consumer = new ConsumerUser();
        consumer.setId(consumerId);
        consumer.setEmail("maria@example.com");
        consumer.setName("Maria");

        ConsumerBookingRequest body = new ConsumerBookingRequest(
                artistId,
                "Maria",
                "next month",
                "medium",
                List.of("arm"),
                false,
                "Rose tattoo",
                List.of("https://ref/1.jpg"),
                "Kyiv",
                null,
                null,
                null,
                null
        );

        when(staffRepository.findPublicArtistById(artistId)).thenReturn(Optional.of(artist));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        ConsumerBookingResultDto result = consumerBookingService.submitBookingRequest(consumer, body);

        assertEquals(RequestStatus.NEW.getValue(), result.status());
        assertEquals("Alex Ink", result.artistName());

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(requestCaptor.capture());

        Request saved = requestCaptor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(RequestSource.APP, saved.getSource());
        assertEquals(consumerId, saved.getConsumerUserId());
        assertEquals("Maria", saved.getClientName());
        assertEquals("maria@example.com", saved.getEmail());

        verify(eventPublisher).publishEvent(any(NewRequestEvent.class));
    }

    @Test
    void submitBookingRequest_rejectsUnknownArtist() {
        UUID artistId = UUID.randomUUID();
        when(staffRepository.findPublicArtistById(artistId)).thenReturn(Optional.empty());

        ConsumerUser consumer = new ConsumerUser();
        consumer.setId(UUID.randomUUID());
        consumer.setEmail("maria@example.com");

        ConsumerBookingRequest body = new ConsumerBookingRequest(
                artistId, "Maria", null, null, null, null, "idea", null, "Kyiv", null, null, null, null);

        assertThrows(ApiException.class,
                () -> consumerBookingService.submitBookingRequest(consumer, body));
    }

    @Test
    void getMyRequests_requiresAuthenticatedConsumer() {
        assertThrows(ApiException.class, () -> consumerBookingService.getMyRequests(null));
    }

    @Test
    void submitBookingRequest_buildsMessageWithCoverUpAndZones() {
        UUID artistId = UUID.randomUUID();
        Staff artist = Staff.builder()
                .id(artistId)
                .tenantId(UUID.randomUUID())
                .firstName("Alex")
                .lastName("Ink")
                .build();

        ConsumerBookingRequest body = new ConsumerBookingRequest(
                artistId,
                "Maria",
                "next month",
                "medium",
                List.of("arm", "shoulder"),
                true,
                "Rose tattoo",
                null,
                "Kyiv",
                null,
                null,
                null,
                null
        );

        ConsumerUser consumer = new ConsumerUser();
        consumer.setId(UUID.randomUUID());
        consumer.setEmail("maria@example.com");
        consumer.setName("Maria");

        when(staffRepository.findPublicArtistById(artistId)).thenReturn(Optional.of(artist));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        consumerBookingService.submitBookingRequest(consumer, body);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(requestCaptor.capture());

        String message = requestCaptor.getValue().getMessage();
        assertTrue(message.contains("Ідея: Rose tattoo"));
        assertTrue(message.contains("Терміни: next month"));
        assertTrue(message.contains("Розмір: medium"));
        assertTrue(message.contains("Місце: arm, shoulder"));
        assertTrue(message.contains("(Перекриття)"));
    }

    @Test
    void submitBookingRequest_assignsArtistLocationWhenPresent() {
        UUID artistId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        Location location = Location.builder().id(locationId).name("Studio").address("Kyiv").build();

        Staff artist = Staff.builder()
                .id(artistId)
                .tenantId(UUID.randomUUID())
                .firstName("Alex")
                .lastName("Ink")
                .locations(new HashSet<>(List.of(location)))
                .build();

        ConsumerBookingRequest body = new ConsumerBookingRequest(
                artistId, "Maria", null, null, null, null, "idea", null, "Kyiv", null, null, null, null);

        ConsumerUser consumer = new ConsumerUser();
        consumer.setId(UUID.randomUUID());
        consumer.setEmail("maria@example.com");
        consumer.setName("Maria");

        when(staffRepository.findPublicArtistById(artistId)).thenReturn(Optional.of(artist));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        consumerBookingService.submitBookingRequest(consumer, body);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(requestCaptor.capture());

        assertNotNull(requestCaptor.getValue().getLocation());
        assertEquals(locationId, requestCaptor.getValue().getLocation().getId());
    }

    @Test
    void getMyRequests_mapsConsumerBookings() {
        UUID consumerId = UUID.randomUUID();
        ConsumerUser consumer = new ConsumerUser();
        consumer.setId(consumerId);

        Staff artist = Staff.builder().firstName("Alex").lastName("Ink").build();
        Request request = Request.builder()
                .id(UUID.randomUUID())
                .status(RequestStatus.NEW)
                .source(RequestSource.APP)
                .assignedStaff(artist)
                .idea("Dragon")
                .city("Kyiv")
                .createdAt(java.time.Instant.now())
                .build();

        when(requestRepository.findByConsumerUserIdOrderByCreatedAtDesc(consumerId))
                .thenReturn(List.of(request));

        var items = consumerBookingService.getMyRequests(consumer);

        assertEquals(1, items.size());
        assertEquals("Alex Ink", items.getFirst().artistName());
        assertEquals("Dragon", items.getFirst().idea());
    }
}
