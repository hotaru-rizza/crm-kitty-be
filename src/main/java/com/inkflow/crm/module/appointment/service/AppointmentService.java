package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.GalleryPhoto;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.GalleryStage;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.AppointmentSpecifications;
import com.inkflow.crm.domain.repository.GalleryPhotoRepository;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.module.appointment.dto.*;
import com.inkflow.crm.module.appointment.mapper.AppointmentMapper;
import com.inkflow.crm.module.project.service.ProjectProgressSyncService;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final InkflowProperties inkflowProperties;
    private final AppointmentRepository appointmentRepository;
    private final GalleryPhotoRepository galleryPhotoRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final RolePermissionService rolePermissionService;
    private final AppointmentSideEffectService appointmentSideEffectService;
    private final AppointmentEntityResolver entityResolver;
    private final AppointmentMapper appointmentMapper;
    private final ProjectProgressSyncService projectProgressSyncService;

    @Transactional(readOnly = true)
    public PageResult<AppointmentDto> getAllAppointments(PageRequest pageRequest, AppointmentFilterRequest filter) {
        Page<Appointment> page = findFiltered(pageRequest, filter);
        List<AppointmentDto> data = page.getContent().stream().map(appointmentMapper::toDto).toList();
        return new PageResult<>(data, PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> getClientHistory(UUID clientId, PageRequest pageRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Appointment> page = appointmentRepository
                .findByTenantIdAndClientIdAndDeletedAtIsNullOrderByStartTimeDesc(tenantId, clientId, pageRequest.toPageable());
        return page.getContent().stream().map(appointmentMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentDetailDto getAppointmentById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return appointmentMapper.toDetailDto(entityResolver.requireAppointment(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> getCalendar(CalendarQueryRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<UUID> artistIds = resolveArtistIds(tenantId, request.getArtistIds());

        Specification<Appointment> spec = Specification
                .where(AppointmentSpecifications.belongsToTenant(tenantId))
                .and(AppointmentSpecifications.notDeleted())
                .and(AppointmentSpecifications.startTimeAfter(request.getFrom()))
                .and(AppointmentSpecifications.startTimeBefore(request.getTo()))
                .and(AppointmentSpecifications.withArtists(artistIds))
                .and(AppointmentSpecifications.withLocation(request.getLocationId()))
                .and(AppointmentSpecifications.withService(request.getServiceId()))
                .and(AppointmentSpecifications.withStatuses(request.getStatuses()));

        return appointmentRepository.findAll(spec, Pageable.unpaged(Sort.by("startTime")))
                .getContent()
                .stream()
                .map(appointmentMapper::toDto)
                .toList();
    }

    @Transactional
    public AppointmentDto createAppointment(CreateAppointmentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Client client = entityResolver.requireClient(tenantId, request.getClientId());
        Staff artist = entityResolver.requireStaff(tenantId, request.getArtistId());
        Service service = entityResolver.requireService(tenantId, request.getServiceId());
        Location location = entityResolver.requireLocation(tenantId, request.getLocationId());

        validateTimeSlot(artist.getId(), request.getStartTime(), request.getEndTime(), null);
        validateArtistAvailable(tenantId, artist.getId(), request.getStartTime());

        Project project = request.getProjectId() != null
                ? entityResolver.requireProject(tenantId, request.getProjectId())
                : null;

        Appointment appointment = buildAppointment(tenantId, request, client, artist, service, location, project);
        appointment = appointmentRepository.save(appointment);

        log.info("Appointment created: tenantId={} appointmentId={}", tenantId, appointment.getId());
        appointmentSideEffectService.afterCreate(appointment);
        return appointmentMapper.toDto(appointment);
    }

    @Transactional
    public AppointmentDto updateAppointment(UUID id, UpdateAppointmentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Appointment appointment = entityResolver.requireAppointment(tenantId, id);
        UUID previousProjectId = appointment.getProject() != null ? appointment.getProject().getId() : null;

        applyRelationUpdates(tenantId, appointment, request);

        AppointmentStatus previousStatus = appointment.getStatus();
        boolean startTimeChanged = request.getStartTime() != null
                && !request.getStartTime().equals(appointment.getStartTime());

        applyScheduleUpdate(appointment, request, id);
        applyPricingUpdate(appointment, request);
        applyStatusUpdate(appointment, request);

        appointment = appointmentRepository.save(appointment);

        log.info("Appointment updated: tenantId={} appointmentId={}", tenantId, id);
        appointmentSideEffectService.afterUpdate(
                appointment,
                new AppointmentUpdateContext(previousStatus, request.getStatus(), startTimeChanged)
        );
        syncLinkedProjects(previousProjectId, appointment.getProject() != null ? appointment.getProject().getId() : null);
        return appointmentMapper.toDto(appointment);
    }

    @Transactional
    public void deleteAppointment(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Appointment appointment = entityResolver.requireAppointment(tenantId, id);
        UUID projectId = appointment.getProject() != null ? appointment.getProject().getId() : null;

        appointmentSideEffectService.afterDelete(appointment, id);
        appointment.softDelete();
        appointmentRepository.save(appointment);

        if (projectId != null) {
            projectProgressSyncService.syncProject(projectId);
        }

        log.info("Appointment deleted: tenantId={} appointmentId={}", tenantId, id);
    }

    @Transactional
    public AppointmentDetailDto.PhotoDto addPhoto(UUID appointmentId, String url, String stage) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Appointment appointment = entityResolver.requireAppointment(tenantId, appointmentId);

        GalleryPhoto photo = GalleryPhoto.builder()
                .tenantId(tenantId)
                .appointment(appointment)
                .url(url)
                .stage(GalleryStage.fromValue(stage))
                .uploadedBy(SecurityUtils.getCurrentUserId())
                .build();

        photo = galleryPhotoRepository.save(photo);
        log.info("Appointment photo added: tenantId={} appointmentId={} photoId={}", tenantId, appointmentId, photo.getId());
        return appointmentMapper.toPhotoDto(photo);
    }

    @Transactional
    public void deletePhoto(UUID appointmentId, UUID photoId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        entityResolver.requireAppointment(tenantId, appointmentId);

        GalleryPhoto photo = galleryPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPOINTMENT_NOT_FOUND, "Photo not found: " + photoId));

        galleryPhotoRepository.delete(photo);
        log.info("Appointment photo deleted: tenantId={} appointmentId={} photoId={}", tenantId, appointmentId, photoId);
    }

    private Page<Appointment> findFiltered(PageRequest pageRequest, AppointmentFilterRequest filter) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Instant from = filter.from() != null ? Instant.parse(filter.from()) : null;
        Instant to = filter.to() != null ? Instant.parse(filter.to()) : null;
        List<UUID> artistIds = resolveArtistIds(tenantId, filter.artistIds());

        Specification<Appointment> spec = Specification
                .where(AppointmentSpecifications.belongsToTenant(tenantId))
                .and(AppointmentSpecifications.notDeleted())
                .and(AppointmentSpecifications.withLocation(filter.locationId()))
                .and(AppointmentSpecifications.withArtists(artistIds))
                .and(AppointmentSpecifications.withService(filter.serviceId()))
                .and(filter.statuses() != null && !filter.statuses().isEmpty()
                        ? AppointmentSpecifications.withStatuses(filter.statuses())
                        : AppointmentSpecifications.withStatus(filter.status()))
                .and(AppointmentSpecifications.startTimeAfter(from))
                .and(AppointmentSpecifications.startTimeBefore(to));

        return appointmentRepository.findAll(spec, pageRequest.toPageable());
    }

    private List<UUID> resolveArtistIds(UUID tenantId, List<UUID> requestedIds) {
        if (rolePermissionService.hasPermission(tenantId, SecurityUtils.getCurrentUserRole(), Permission.CALENDAR_VIEW_ALL.getValue())) {
            return requestedIds;
        }
        return List.of(SecurityUtils.getCurrentUserId());
    }

    private void validateTimeSlot(UUID artistId, Instant start, Instant end, UUID excludeId) {
        boolean conflict = excludeId == null
                ? appointmentRepository.existsConflictingAppointment(artistId, start, end)
                : appointmentRepository.existsConflictingAppointmentExcluding(artistId, start, end, excludeId);

        if (conflict) {
            throw BusinessRuleException.timeSlotConflict();
        }
    }

    private void validateArtistAvailable(UUID tenantId, UUID artistId, Instant startTime) {
        LocalDate date = startTime.atZone(inkflowProperties.defaultZoneId()).toLocalDate();
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findActiveLeaveForDate(tenantId, artistId, date);

        if (!activeLeaves.isEmpty()) {
            throw new BusinessRuleException("Майстер відсутній у цей день (відпустка/вихідний)");
        }
    }

    private Appointment buildAppointment(
            UUID tenantId,
            CreateAppointmentRequest request,
            Client client,
            Staff artist,
            Service service,
            Location location,
            Project project) {
        BigDecimal prepayment = request.getPrepayment() != null ? request.getPrepayment() : BigDecimal.ZERO;
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;

        return Appointment.builder()
                .tenantId(tenantId)
                .client(client)
                .artist(artist)
                .service(service)
                .location(location)
                .project(project)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(AppointmentStatus.SCHEDULED)
                .price(request.getPrice())
                .prepayment(prepayment)
                .discount(discount)
                .finalPrice(request.getPrice().subtract(discount))
                .notes(request.getNotes())
                .sketchImage(request.getSketchImage())
                .build();
    }

    private void applyRelationUpdates(UUID tenantId, Appointment appointment, UpdateAppointmentRequest request) {
        if (request.getArtistId() != null) {
            appointment.setArtist(entityResolver.requireStaff(tenantId, request.getArtistId()));
        }
        if (request.getServiceId() != null) {
            appointment.setService(entityResolver.requireService(tenantId, request.getServiceId()));
        }
        if (request.getLocationId() != null) {
            appointment.setLocation(entityResolver.requireLocation(tenantId, request.getLocationId()));
        }
        if (Boolean.TRUE.equals(request.getClearProjectId())) {
            appointment.setProject(null);
        } else if (request.getProjectId() != null) {
            appointment.setProject(entityResolver.requireProject(tenantId, request.getProjectId()));
        }
    }

    private void applyScheduleUpdate(Appointment appointment, UpdateAppointmentRequest request, UUID appointmentId) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            return;
        }

        validateTimeSlot(appointment.getArtist().getId(), request.getStartTime(), request.getEndTime(), appointmentId);
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
    }

    private void applyPricingUpdate(Appointment appointment, UpdateAppointmentRequest request) {
        if (request.getPrice() != null) {
            appointment.setPrice(request.getPrice());
        }
        if (request.getPrepayment() != null) {
            appointment.setPrepayment(request.getPrepayment());
        }
        if (request.getDiscount() != null) {
            appointment.setDiscount(request.getDiscount());
        }
        if (request.getNotes() != null) {
            appointment.setNotes(request.getNotes());
        }
        if (request.getSketchImage() != null) {
            appointment.setSketchImage(request.getSketchImage());
        }
        appointment.calculateFinalPrice();
    }

    private void applyStatusUpdate(Appointment appointment, UpdateAppointmentRequest request) {
        if (request.getStatus() == null) {
            return;
        }

        AppointmentStatus newStatus = AppointmentStatus.fromValue(request.getStatus());
        if (newStatus == AppointmentStatus.CANCELLED) {
            appointment.cancel(request.getCancellationReason());
            return;
        }
        if (newStatus == AppointmentStatus.COMPLETED) {
            appointment.markAsCompleted();
            return;
        }
        appointment.setStatus(newStatus);
    }

    private void syncLinkedProjects(UUID previousProjectId, UUID currentProjectId) {
        if (previousProjectId != null) {
            projectProgressSyncService.syncProject(previousProjectId);
        }
        if (currentProjectId != null && !currentProjectId.equals(previousProjectId)) {
            projectProgressSyncService.syncProject(currentProjectId);
        }
    }
}
