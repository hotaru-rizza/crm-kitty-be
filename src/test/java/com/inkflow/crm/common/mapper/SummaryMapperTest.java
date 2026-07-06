package com.inkflow.crm.common.mapper;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.staff.dto.StaffSummaryDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SummaryMapperTest {

    private final SummaryMapper mapper = new SummaryMapper();

    @Test
    void toStaffSummary_marksDeletedStaff() {
        Staff staff = Staff.builder()
                .id(UUID.randomUUID())
                .firstName("Mykyta")
                .lastName("Horskyi")
                .role(UserRole.ARTIST)
                .calendarColor("#6366f1")
                .accountStatus(AccountStatus.ACTIVE)
                .deletedAt(Instant.now())
                .build();

        StaffSummaryDto summary = mapper.toStaffSummary(staff);

        assertNotNull(summary);
        assertTrue(summary.isDeleted());
        assertEquals("active", summary.getAccountStatus());
    }

    @Test
    void toStaffSummary_returnsNullForMissingStaff() {
        assertNull(mapper.toStaffSummary(null));
    }
}
