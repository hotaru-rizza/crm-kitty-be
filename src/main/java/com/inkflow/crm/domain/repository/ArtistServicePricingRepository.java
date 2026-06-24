package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.ArtistServicePricing;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArtistServicePricingRepository extends JpaRepository<ArtistServicePricing, UUID> {

    @EntityGraph(attributePaths = {"service"})
    List<ArtistServicePricing> findByStaffId(UUID staffId);

    Optional<ArtistServicePricing> findByStaffIdAndServiceId(UUID staffId, UUID serviceId);

    @Modifying
    void deleteByStaffId(UUID staffId);
}
