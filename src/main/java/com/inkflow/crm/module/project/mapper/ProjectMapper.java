package com.inkflow.crm.module.project.mapper;

import com.inkflow.crm.common.mapper.SummaryMapper;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.GalleryPhoto;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.module.project.dto.ProjectDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectMapper {

    private final SummaryMapper summaryMapper;

    public ProjectDto toListDto(Project project) {
        return toDto(project, null, null);
    }

    public ProjectDto toDto(Project project, List<ProjectDto.PhotoDto> photos, List<ProjectDto.SessionDto> sessions) {
        return ProjectDto.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .client(summaryMapper.toClientSummary(project.getClient()))
                .artist(summaryMapper.toStaffSummary(project.getArtist()))
                .status(project.getStatus().getValue())
                .estimatedCost(project.getEstimatedCost())
                .totalPaid(project.getTotalPaid())
                .totalSessions(project.getTotalSessions())
                .completedSessions(project.getCompletedSessions())
                .sketchImage(project.getSketchImage())
                .createdAt(project.getCreatedAt())
                .photos(photos)
                .sessions(sessions)
                .build();
    }

    public ProjectDto.PhotoDto toPhotoDto(GalleryPhoto photo) {
        return ProjectDto.PhotoDto.builder()
                .id(photo.getId())
                .url(photo.getUrl())
                .stage(photo.getStage().getValue())
                .uploadedAt(photo.getUploadedAt())
                .build();
    }

    public ProjectDto.SessionDto toSessionDto(Appointment appointment) {
        return ProjectDto.SessionDto.builder()
                .id(appointment.getId())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus().getValue())
                .serviceId(appointment.getService() != null ? appointment.getService().getId() : null)
                .serviceName(appointment.getService() != null ? appointment.getService().getTitle() : null)
                .serviceColor(appointment.getService() != null ? appointment.getService().getColor() : null)
                .price(appointment.getPrice())
                .finalPrice(appointment.getFinalPrice())
                .notes(appointment.getNotes())
                .photosCount(appointment.getPhotos() != null ? appointment.getPhotos().size() : 0)
                .build();
    }

    public List<ProjectDto.SessionDto> toSessionDtos(Project project) {
        return project.getAppointments().stream()
                .filter(a -> a.getDeletedAt() == null)
                .sorted(Comparator.comparing(Appointment::getStartTime))
                .map(this::toSessionDto)
                .toList();
    }
}
