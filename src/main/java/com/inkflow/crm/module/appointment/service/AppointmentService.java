package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.appointment.dto.*;
import com.inkflow.crm.module.client.dto.ClientSummaryDto;
import com.inkflow.crm.module.staff.dto.StaffSummaryDto;
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
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ClientRepository clientRepository;
    private final StaffRepository staffRepository;
    private final ServiceRepository serviceRepository;
    private final LocationRepository locationRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<AppointmentDto> getAllAppointments(PageRequest pageRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Appointment> page = appointmentRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
        return page.getContent().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public PaginationDto getPagination(PageRequest pageRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Appointment> page = appointmentRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
        return PaginationDto.from(page);
    }

    @Transactional(readOnly = true)
    public AppointmentDetailDto getAppointmentById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(id.toString()));
        return mapToDetailDto(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> getCalendar(CalendarQueryRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<Appointment> appointments = appointmentRepository.findForCalendar(
                tenantId, request.getFrom(), request.getTo(), request.getArtistIds());
        return appointments.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public AppointmentDto createAppointment(CreateAppointmentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Client client = clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getClientId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(request.getClientId().toString()));
        Staff artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getArtistId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));
        com.inkflow.crm.domain.entity.Service service = serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getServiceId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.service(request.getServiceId().toString()));
        Location location = locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getLocationId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.location(request.getLocationId().toString()));

        if (appointmentRepository.existsConflictingAppointment(request.getArtistId(), request.getStartTime(), request.getEndTime())) {
            throw BusinessRuleException.timeSlotConflict();
        }

        Project project = null;
        if (request.getProjectId() != null) {
            project = projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getProjectId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.project(request.getProjectId().toString()));
        }

        BigDecimal prepayment = request.getPrepayment() != null ? request.getPrepayment() : BigDecimal.ZERO;
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        BigDecimal finalPrice = request.getPrice().subtract(discount);

        Appointment appointment = Appointment.builder()
                .tenantId(tenantId)
                .client(client)
                .artist(artist)
                .service(service)
                .location(location)
                .project(project)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(AppointmentStatus.NEW)
                .price(request.getPrice())
                .prepayment(prepayment)
                .discount(discount)
                .finalPrice(finalPrice)
                .notes(request.getNotes())
                .sketchImage(request.getSketchImage())
                .waiverSigned(false)
                .build();

        appointment = appointmentRepository.save(appointment);
        return mapToDto(appointment);
    }

    @Transactional
    public AppointmentDto updateAppointment(UUID id, UpdateAppointmentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(id.toString()));

        if (request.getArtistId() != null) {
            Staff artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getArtistId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));
            appointment.setArtist(artist);
        }

        if (request.getServiceId() != null) {
            com.inkflow.crm.domain.entity.Service service = serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getServiceId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.service(request.getServiceId().toString()));
            appointment.setService(service);
        }

        if (request.getLocationId() != null) {
            Location location = locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getLocationId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.location(request.getLocationId().toString()));
            appointment.setLocation(location);
        }

        if (request.getStartTime() != null && request.getEndTime() != null) {
            if (appointmentRepository.existsConflictingAppointmentExcluding(
                    appointment.getArtist().getId(), request.getStartTime(), request.getEndTime(), id)) {
                throw BusinessRuleException.timeSlotConflict();
            }
            appointment.setStartTime(request.getStartTime());
            appointment.setEndTime(request.getEndTime());
        }

        if (request.getPrice() != null) appointment.setPrice(request.getPrice());
        if (request.getPrepayment() != null) appointment.setPrepayment(request.getPrepayment());
        if (request.getDiscount() != null) appointment.setDiscount(request.getDiscount());
        if (request.getNotes() != null) appointment.setNotes(request.getNotes());
        if (request.getSketchImage() != null) appointment.setSketchImage(request.getSketchImage());

        appointment.calculateFinalPrice();

        if (request.getStatus() != null) {
            AppointmentStatus newStatus = AppointmentStatus.fromValue(request.getStatus());
            if (newStatus == AppointmentStatus.CANCELLED) {
                appointment.cancel(request.getCancellationReason());
            } else if (newStatus == AppointmentStatus.DONE) {
                appointment.markAsDone();
            } else {
                appointment.setStatus(newStatus);
            }
        }

        appointment = appointmentRepository.save(appointment);
        return mapToDto(appointment);
    }

    @Transactional
    public void deleteAppointment(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(id.toString()));
        appointment.softDelete();
        appointmentRepository.save(appointment);
    }

    private AppointmentDto mapToDto(Appointment appointment) {
        return AppointmentDto.builder()
                .id(appointment.getId())
                .client(ClientSummaryDto.builder()
                        .id(appointment.getClient().getId())
                        .firstName(appointment.getClient().getFirstName())
                        .lastName(appointment.getClient().getLastName())
                        .phone(appointment.getClient().getPhone())
                        .avatar(appointment.getClient().getAvatar())
                        .hasMedicalConditions(appointment.getClient().hasMedicalConditions())
                        .build())
                .artist(StaffSummaryDto.builder()
                        .id(appointment.getArtist().getId())
                        .firstName(appointment.getArtist().getFirstName())
                        .lastName(appointment.getArtist().getLastName())
                        .avatar(appointment.getArtist().getAvatar())
                        .calendarColor(appointment.getArtist().getCalendarColor())
                        .role(appointment.getArtist().getRole().getValue())
                        .build())
                .service(AppointmentDto.ServiceSummaryDto.builder()
                        .id(appointment.getService().getId())
                        .title(appointment.getService().getTitle())
                        .color(appointment.getService().getColor())
                        .build())
                .location(AppointmentDto.LocationSummaryDto.builder()
                        .id(appointment.getLocation().getId())
                        .name(appointment.getLocation().getName())
                        .color(appointment.getLocation().getColor())
                        .build())
                .projectId(appointment.getProject() != null ? appointment.getProject().getId() : null)
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus().getValue())
                .price(appointment.getPrice())
                .finalPrice(appointment.getFinalPrice())
                .waiverSigned(appointment.getWaiverSigned())
                .createdAt(appointment.getCreatedAt())
                .build();
    }

    private AppointmentDetailDto mapToDetailDto(Appointment appointment) {
        List<AppointmentDetailDto.PhotoDto> photos = appointment.getPhotos().stream()
                .map(p -> AppointmentDetailDto.PhotoDto.builder()
                        .id(p.getId())
                        .url(p.getUrl())
                        .stage(p.getStage().getValue())
                        .build())
                .collect(Collectors.toList());

        return AppointmentDetailDto.builder()
                .id(appointment.getId())
                .client(ClientSummaryDto.builder()
                        .id(appointment.getClient().getId())
                        .firstName(appointment.getClient().getFirstName())
                        .lastName(appointment.getClient().getLastName())
                        .phone(appointment.getClient().getPhone())
                        .avatar(appointment.getClient().getAvatar())
                        .hasMedicalConditions(appointment.getClient().hasMedicalConditions())
                        .build())
                .artist(StaffSummaryDto.builder()
                        .id(appointment.getArtist().getId())
                        .firstName(appointment.getArtist().getFirstName())
                        .lastName(appointment.getArtist().getLastName())
                        .avatar(appointment.getArtist().getAvatar())
                        .calendarColor(appointment.getArtist().getCalendarColor())
                        .role(appointment.getArtist().getRole().getValue())
                        .build())
                .service(AppointmentDto.ServiceSummaryDto.builder()
                        .id(appointment.getService().getId())
                        .title(appointment.getService().getTitle())
                        .color(appointment.getService().getColor())
                        .build())
                .location(AppointmentDto.LocationSummaryDto.builder()
                        .id(appointment.getLocation().getId())
                        .name(appointment.getLocation().getName())
                        .color(appointment.getLocation().getColor())
                        .build())
                .projectId(appointment.getProject() != null ? appointment.getProject().getId() : null)
                .projectTitle(appointment.getProject() != null ? appointment.getProject().getTitle() : null)
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus().getValue())
                .price(appointment.getPrice())
                .prepayment(appointment.getPrepayment())
                .discount(appointment.getDiscount())
                .finalPrice(appointment.getFinalPrice())
                .notes(appointment.getNotes())
                .sketchImage(appointment.getSketchImage())
                .waiverSigned(appointment.getWaiverSigned())
                .cancellationReason(appointment.getCancellationReason())
                .cancelledAt(appointment.getCancelledAt())
                .photos(photos)
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}
