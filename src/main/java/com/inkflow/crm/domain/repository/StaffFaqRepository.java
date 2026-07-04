package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.StaffFaq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StaffFaqRepository extends JpaRepository<StaffFaq, UUID> {
    List<StaffFaq> findByStaffIdOrderBySortOrderAsc(UUID staffId);

    List<StaffFaq> findByStaffIdInOrderByStaffIdAscSortOrderAsc(Collection<UUID> staffIds);
}
