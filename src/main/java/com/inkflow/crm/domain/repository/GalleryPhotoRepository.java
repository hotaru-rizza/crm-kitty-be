package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.GalleryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, UUID> {
    List<GalleryPhoto> findByAppointmentId(UUID appointmentId);
    List<GalleryPhoto> findByProjectId(UUID projectId);
    List<GalleryPhoto> findByTenantId(UUID tenantId);
}
