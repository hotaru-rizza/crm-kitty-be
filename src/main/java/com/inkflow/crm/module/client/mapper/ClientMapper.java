package com.inkflow.crm.module.client.mapper;

import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.module.client.dto.*;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    ClientDto toDto(Client client);

    List<ClientDto> toDtoList(List<Client> clients);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "lastVisit", ignore = true)
    @Mapping(target = "firstVisit", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "balance", expression = "java(java.math.BigDecimal.ZERO)")
    @Mapping(target = "totalVisits", constant = "0")
    @Mapping(target = "cancelledVisits", constant = "0")
    @Mapping(target = "ltv", expression = "java(java.math.BigDecimal.ZERO)")
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "appointments", ignore = true)
    @Mapping(target = "dormant", constant = "false")
    @Mapping(target = "blacklisted", constant = "false")
    @Mapping(target = "source", expression = "java(request.getSource() != null ? com.inkflow.crm.domain.enums.RequestSource.fromValue(request.getSource()) : null)")
    Client toEntity(CreateClientRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "lastVisit", ignore = true)
    @Mapping(target = "firstVisit", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "totalVisits", ignore = true)
    @Mapping(target = "cancelledVisits", ignore = true)
    @Mapping(target = "ltv", ignore = true)
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "appointments", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "dormant", ignore = true)
    void updateEntity(UpdateClientRequest request, @MappingTarget Client client);

    default ClientSummaryDto toSummaryDto(Client client) {
        return ClientSummaryDto.builder()
                .id(client.getId())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .phone(client.getPhone())
                .avatar(client.getAvatar())
                .blacklisted(client.isBlacklisted())
                .hasMedicalConditions(client.hasMedicalConditions())
                .build();
    }
}
