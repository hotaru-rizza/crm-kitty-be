package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.GalleryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, UUID> {

    Optional<GalleryPhoto> findByIdAndAppointmentId(UUID id, UUID appointmentId);

    Optional<GalleryPhoto> findByIdAndProjectId(UUID id, UUID projectId);

    List<GalleryPhoto> findByAppointmentId(UUID appointmentId);

    List<GalleryPhoto> findByProjectId(UUID projectId);
}
