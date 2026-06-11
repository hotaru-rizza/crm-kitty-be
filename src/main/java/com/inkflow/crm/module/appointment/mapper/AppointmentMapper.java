package com.inkflow.crm.module.appointment.mapper;

import com.inkflow.crm.common.mapper.SummaryMapper;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.GalleryPhoto;
import com.inkflow.crm.module.appointment.dto.AppointmentDetailDto;
import com.inkflow.crm.module.appointment.dto.AppointmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AppointmentMapper {

    private final SummaryMapper summaryMapper;

    public AppointmentDto toDto(Appointment appointment) {
        return AppointmentDto.builder()
                .id(appointment.getId())
                .client(summaryMapper.toClientSummary(appointment.getClient()))
                .artist(summaryMapper.toStaffSummary(appointment.getArtist()))
                .service(toServiceSummary(appointment))
                .location(toLocationSummary(appointment))
                .projectId(appointment.getProject() != null ? appointment.getProject().getId() : null)
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus().getValue())
                .price(appointment.getPrice())
                .finalPrice(appointment.getFinalPrice())
                .sketchImage(appointment.getSketchImage())
                .createdAt(appointment.getCreatedAt())
                .build();
    }

    public AppointmentDetailDto toDetailDto(Appointment appointment) {
        return AppointmentDetailDto.builder()
                .id(appointment.getId())
                .client(summaryMapper.toClientSummary(appointment.getClient()))
                .artist(summaryMapper.toStaffSummary(appointment.getArtist()))
                .service(toServiceSummary(appointment))
                .location(toLocationSummary(appointment))
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
                .cancellationReason(appointment.getCancellationReason())
                .cancelledAt(appointment.getCancelledAt())
                .photos(toPhotoDtos(appointment.getPhotos()))
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }

    public AppointmentDetailDto.PhotoDto toPhotoDto(GalleryPhoto photo) {
        return AppointmentDetailDto.PhotoDto.builder()
                .id(photo.getId())
                .url(photo.getUrl())
                .stage(photo.getStage().getValue())
                .build();
    }

    private List<AppointmentDetailDto.PhotoDto> toPhotoDtos(List<GalleryPhoto> photos) {
        return photos.stream().map(this::toPhotoDto).toList();
    }

    private AppointmentDto.ServiceSummaryDto toServiceSummary(Appointment appointment) {
        return AppointmentDto.ServiceSummaryDto.builder()
                .id(appointment.getService().getId())
                .title(appointment.getService().getTitle())
                .color(appointment.getService().getColor())
                .build();
    }

    private AppointmentDto.LocationSummaryDto toLocationSummary(Appointment appointment) {
        return AppointmentDto.LocationSummaryDto.builder()
                .id(appointment.getLocation().getId())
                .name(appointment.getLocation().getName())
                .color(appointment.getLocation().getColor())
                .build();
    }
}
