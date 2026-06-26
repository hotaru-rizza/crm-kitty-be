package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.GalleryPhotoRepository;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.module.appointment.dto.AppointmentDto;
import com.inkflow.crm.module.appointment.dto.AppointmentUpdateContext;
import com.inkflow.crm.module.appointment.dto.CreateAppointmentRequest;
import com.inkflow.crm.module.appointment.dto.UpdateAppointmentRequest;
import com.inkflow.crm.module.appointment.mapper.AppointmentMapper;
import com.inkflow.crm.module.settings.service.RolePermissionService;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private InkflowProperties inkflowProperties;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private GalleryPhotoRepository galleryPhotoRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private AppointmentSideEffectService appointmentSideEffectService;

    @Mock
    private AppointmentEntityResolver entityResolver;

    @Mock
    private AppointmentMapper appointmentMapper;

    @InjectMocks
    private AppointmentService appointmentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAppointment_persistsAndTriggersSideEffects() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        Client client = client(clientId);
        Staff artist = staff(artistId);
        Service service = service(serviceId);
        Location location = location(locationId);

        when(entityResolver.requireClient(tenantId, clientId)).thenReturn(client);
        when(entityResolver.requireStaff(tenantId, artistId)).thenReturn(artist);
        when(entityResolver.requireService(tenantId, serviceId)).thenReturn(service);
        when(entityResolver.requireLocation(tenantId, locationId)).thenReturn(location);
        when(appointmentRepository.existsConflictingAppointment(artistId, start, end)).thenReturn(false);
        when(inkflowProperties.defaultZoneId()).thenReturn(ZoneId.of("Europe/Kyiv"));
        when(leaveRequestRepository.findActiveLeaveForDate(eq(tenantId), eq(artistId), any())).thenReturn(List.of());

        Appointment saved = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .client(client)
                .artist(artist)
                .service(service)
                .location(location)
                .startTime(start)
                .endTime(end)
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build();

        when(appointmentRepository.save(any(Appointment.class))).thenReturn(saved);
        when(appointmentMapper.toDto(saved)).thenReturn(AppointmentDto.builder().id(saved.getId()).build());

        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .clientId(clientId)
                .artistId(artistId)
                .serviceId(serviceId)
                .locationId(locationId)
                .startTime(start)
                .endTime(end)
                .price(BigDecimal.valueOf(1000))
                .build();

        AppointmentDto result = appointmentService.createAppointment(request);

        assertEquals(saved.getId(), result.getId());
        verify(appointmentSideEffectService).afterCreate(saved);
    }

    @Test
    void createAppointment_rejectsConflictingTimeSlot() {
        UUID tenantId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        when(entityResolver.requireClient(any(), any())).thenReturn(client(UUID.randomUUID()));
        when(entityResolver.requireStaff(tenantId, artistId)).thenReturn(staff(artistId));
        when(entityResolver.requireService(any(), any())).thenReturn(service(UUID.randomUUID()));
        when(entityResolver.requireLocation(any(), any())).thenReturn(location(UUID.randomUUID()));
        when(appointmentRepository.existsConflictingAppointment(artistId, start, end)).thenReturn(true);

        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .clientId(UUID.randomUUID())
                .artistId(artistId)
                .serviceId(UUID.randomUUID())
                .locationId(UUID.randomUUID())
                .startTime(start)
                .endTime(end)
                .price(BigDecimal.valueOf(1000))
                .build();

        assertThrows(BusinessRuleException.class, () -> appointmentService.createAppointment(request));
    }

    @Test
    void createAppointment_rejectsArtistOnLeave() {
        UUID tenantId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        when(entityResolver.requireClient(any(), any())).thenReturn(client(UUID.randomUUID()));
        when(entityResolver.requireStaff(tenantId, artistId)).thenReturn(staff(artistId));
        when(entityResolver.requireService(any(), any())).thenReturn(service(UUID.randomUUID()));
        when(entityResolver.requireLocation(any(), any())).thenReturn(location(UUID.randomUUID()));
        when(appointmentRepository.existsConflictingAppointment(artistId, start, end)).thenReturn(false);
        when(inkflowProperties.defaultZoneId()).thenReturn(ZoneId.of("Europe/Kyiv"));
        when(leaveRequestRepository.findActiveLeaveForDate(eq(tenantId), eq(artistId), any()))
                .thenReturn(List.of(new LeaveRequest()));

        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .clientId(UUID.randomUUID())
                .artistId(artistId)
                .serviceId(UUID.randomUUID())
                .locationId(UUID.randomUUID())
                .startTime(start)
                .endTime(end)
                .price(BigDecimal.valueOf(1000))
                .build();

        assertThrows(BusinessRuleException.class, () -> appointmentService.createAppointment(request));
    }

    @Test
    void updateAppointment_reschedulesWhenNoConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        Instant oldStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant oldEnd = oldStart.plus(1, ChronoUnit.HOURS);
        Instant newStart = oldStart.plus(2, ChronoUnit.HOURS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        Staff artist = staff(artistId);
        Appointment appointment = baseAppointment(appointmentId, tenantId, artist, oldStart, oldEnd, AppointmentStatus.SCHEDULED);

        when(entityResolver.requireAppointment(tenantId, appointmentId)).thenReturn(appointment);
        when(appointmentRepository.existsConflictingAppointmentExcluding(artistId, newStart, newEnd, appointmentId))
                .thenReturn(false);
        when(inkflowProperties.defaultZoneId()).thenReturn(ZoneId.of("Europe/Kyiv"));
        when(leaveRequestRepository.findActiveLeaveForDate(eq(tenantId), eq(artistId), any())).thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentMapper.toDto(appointment)).thenReturn(AppointmentDto.builder().id(appointmentId).build());

        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .startTime(newStart)
                .endTime(newEnd)
                .build();

        appointmentService.updateAppointment(appointmentId, request);

        assertEquals(newStart, appointment.getStartTime());
        assertEquals(newEnd, appointment.getEndTime());

        ArgumentCaptor<AppointmentUpdateContext> contextCaptor = ArgumentCaptor.forClass(AppointmentUpdateContext.class);
        verify(appointmentSideEffectService).afterUpdate(eq(appointment), contextCaptor.capture());
        assertTrue(contextCaptor.getValue().startTimeChanged());
    }

    @Test
    void updateAppointment_rejectsRescheduleConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        Instant oldStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant oldEnd = oldStart.plus(1, ChronoUnit.HOURS);
        Instant newStart = oldStart.plus(2, ChronoUnit.HOURS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        Staff artist = staff(artistId);
        Appointment appointment = baseAppointment(appointmentId, tenantId, artist, oldStart, oldEnd, AppointmentStatus.SCHEDULED);

        when(entityResolver.requireAppointment(tenantId, appointmentId)).thenReturn(appointment);
        when(appointmentRepository.existsConflictingAppointmentExcluding(artistId, newStart, newEnd, appointmentId))
                .thenReturn(true);

        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .startTime(newStart)
                .endTime(newEnd)
                .build();

        assertThrows(BusinessRuleException.class, () -> appointmentService.updateAppointment(appointmentId, request));
    }

    @Test
    void updateAppointment_revalidatesSlotWhenArtistChanged() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID currentArtistId = UUID.randomUUID();
        UUID newArtistId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        Appointment appointment = baseAppointment(
                appointmentId, tenantId, staff(currentArtistId), start, end, AppointmentStatus.SCHEDULED);

        when(entityResolver.requireAppointment(tenantId, appointmentId)).thenReturn(appointment);
        when(entityResolver.requireStaff(tenantId, newArtistId)).thenReturn(staff(newArtistId));
        when(appointmentRepository.existsConflictingAppointmentExcluding(newArtistId, start, end, appointmentId))
                .thenReturn(true);

        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .artistId(newArtistId)
                .build();

        assertThrows(BusinessRuleException.class, () -> appointmentService.updateAppointment(appointmentId, request));
    }

    @Test
    void updateAppointment_cancelsWithReason() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        Appointment appointment = baseAppointment(appointmentId, tenantId, staff(artistId), start, end, AppointmentStatus.SCHEDULED);

        when(entityResolver.requireAppointment(tenantId, appointmentId)).thenReturn(appointment);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentMapper.toDto(appointment)).thenReturn(AppointmentDto.builder().id(appointmentId).build());

        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .status("cancelled")
                .cancellationReason("Client no-show")
                .build();

        appointmentService.updateAppointment(appointmentId, request);

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals("Client no-show", appointment.getCancellationReason());
        assertNotNull(appointment.getCancelledAt());
    }

    @Test
    void updateAppointment_marksAsDone() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        Appointment appointment = baseAppointment(
                appointmentId, tenantId, staff(UUID.randomUUID()), start, end, AppointmentStatus.SCHEDULED);

        when(entityResolver.requireAppointment(tenantId, appointmentId)).thenReturn(appointment);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentMapper.toDto(appointment)).thenReturn(AppointmentDto.builder().id(appointmentId).build());

        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .status("completed")
                .build();

        appointmentService.updateAppointment(appointmentId, request);

        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
    }

    @Test
    void updateAppointment_recalculatesFinalPriceAfterDiscount() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        authenticate(tenantId);

        Appointment appointment = baseAppointment(
                appointmentId, tenantId, staff(UUID.randomUUID()), start, end, AppointmentStatus.SCHEDULED);
        appointment.setPrice(BigDecimal.valueOf(1000));
        appointment.setDiscount(BigDecimal.ZERO);
        appointment.setFinalPrice(BigDecimal.valueOf(1000));

        when(entityResolver.requireAppointment(tenantId, appointmentId)).thenReturn(appointment);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentMapper.toDto(appointment)).thenReturn(AppointmentDto.builder().id(appointmentId).build());

        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .price(BigDecimal.valueOf(1200))
                .discount(BigDecimal.valueOf(200))
                .build();

        appointmentService.updateAppointment(appointmentId, request);

        assertEquals(BigDecimal.valueOf(1200), appointment.getPrice());
        assertEquals(BigDecimal.valueOf(200), appointment.getDiscount());
        assertEquals(BigDecimal.valueOf(1000), appointment.getFinalPrice());
    }

    @Test
    void deleteAppointment_softDeletesAndTriggersSideEffects() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId);

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .status(AppointmentStatus.SCHEDULED)
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .build();

        when(entityResolver.requireAppointment(tenantId, appointmentId)).thenReturn(appointment);

        appointmentService.deleteAppointment(appointmentId);

        verify(appointmentSideEffectService).afterDelete(appointment, appointmentId);
        verify(appointmentRepository).save(appointment);
    }

    private Appointment baseAppointment(
            UUID id,
            UUID tenantId,
            Staff artist,
            Instant start,
            Instant end,
            AppointmentStatus status) {
        return Appointment.builder()
                .id(id)
                .tenantId(tenantId)
                .artist(artist)
                .startTime(start)
                .endTime(end)
                .status(status)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build();
    }

    private Client client(UUID id) {
        return Client.builder().id(id).build();
    }

    private Staff staff(UUID id) {
        return Staff.builder().id(id).build();
    }

    private Service service(UUID id) {
        return Service.builder().id(id).title("Session").build();
    }

    private Location location(UUID id) {
        return Location.builder().id(id).build();
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
