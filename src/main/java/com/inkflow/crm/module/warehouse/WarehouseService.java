package com.inkflow.crm.module.warehouse;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Warehouse;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.WarehouseRepository;
import com.inkflow.crm.module.warehouse.dto.CreateWarehouseRequest;
import com.inkflow.crm.module.warehouse.dto.WarehouseDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public List<WarehouseDto> getAll() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return warehouseRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public WarehouseDto create(CreateWarehouseRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = null;
        if (req.getStaffId() != null) {
            staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(req.getStaffId(), tenantId).orElse(null);
        }
        Warehouse w = Warehouse.builder()
                .tenantId(tenantId)
                .name(req.getName())
                .notes(req.getNotes())
                .assignedStaff(staff)
                .build();
        return toDto(warehouseRepository.save(w));
    }

    @Transactional
    public WarehouseDto update(UUID id, CreateWarehouseRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Warehouse w = warehouseRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Warehouse not found"));
        if (req.getName() != null) w.setName(req.getName());
        if (req.getNotes() != null) w.setNotes(req.getNotes());
        if (req.getStaffId() != null) {
            Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(req.getStaffId(), tenantId).orElse(null);
            w.setAssignedStaff(staff);
        } else {
            w.setAssignedStaff(null);
        }
        return toDto(warehouseRepository.save(w));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Warehouse w = warehouseRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Warehouse not found"));
        w.softDelete();
        warehouseRepository.save(w);
    }

    private WarehouseDto toDto(Warehouse w) {
        return WarehouseDto.builder()
                .id(w.getId())
                .name(w.getName())
                .notes(w.getNotes())
                .staffId(w.getAssignedStaff() != null ? w.getAssignedStaff().getId() : null)
                .staffName(w.getAssignedStaff() != null
                        ? w.getAssignedStaff().getFirstName() + " " + w.getAssignedStaff().getLastName() : null)
                .createdAt(w.getCreatedAt())
                .build();
    }
}
