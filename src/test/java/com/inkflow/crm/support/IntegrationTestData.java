package com.inkflow.crm.support;

import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.ClientStatus;
import com.inkflow.crm.domain.enums.PricingType;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;

import java.math.BigDecimal;
import java.util.UUID;

public final class IntegrationTestData {

    private IntegrationTestData() {
    }

    public record TenantBundle(
            Tenant tenant,
            Staff owner,
            Client client,
            Service service,
            Location location
    ) {
    }

    public static TenantBundle seedTenant(
            TenantRepository tenantRepository,
            StaffRepository staffRepository,
            ClientRepository clientRepository,
            ServiceRepository serviceRepository,
            LocationRepository locationRepository
    ) {
        UUID suffix = UUID.randomUUID();

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Test Tenant " + suffix)
                .subdomain("test-" + suffix.toString().substring(0, 8))
                .currency("UAH")
                .timezone("Europe/Kyiv")
                .isActive(true)
                .accountType("STUDIO")
                .language("ua")
                .build());

        Location location = locationRepository.save(Location.builder()
                .tenantId(tenant.getId())
                .name("Main Studio")
                .address("Kyiv")
                .color("#6366f1")
                .isActive(true)
                .build());

        Staff owner = staffRepository.save(Staff.builder()
                .tenantId(tenant.getId())
                .firstName("Owner")
                .lastName("User")
                .email("owner-" + suffix + "@test.com")
                .role(UserRole.OWNER)
                .calendarColor("#6366f1")
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        Client client = clientRepository.save(Client.builder()
                .tenantId(tenant.getId())
                .firstName("Client")
                .lastName("One")
                .phone("+38099" + suffix.toString().replace("-", "").substring(0, 7).replaceAll("[^0-9]", "0123456"))
                .status(ClientStatus.ACTIVE)
                .totalVisits(0)
                .cancelledVisits(0)
                .ltv(BigDecimal.ZERO)
                .location(location)
                .build());

        Service service = serviceRepository.save(Service.builder()
                .tenantId(tenant.getId())
                .title("Tattoo Session")
                .pricingType(PricingType.FIXED)
                .price(BigDecimal.valueOf(1000))
                .duration(60)
                .color("#6366f1")
                .isActive(true)
                .build());

        return new TenantBundle(tenant, owner, client, service, location);
    }

    public static Staff seedArtist(StaffRepository staffRepository, Tenant tenant) {
        UUID suffix = UUID.randomUUID();
        return staffRepository.save(Staff.builder()
                .tenantId(tenant.getId())
                .firstName("Artist")
                .lastName("Member")
                .email("artist-" + suffix + "@test.com")
                .role(UserRole.ARTIST)
                .calendarColor("#22c55e")
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }
}
