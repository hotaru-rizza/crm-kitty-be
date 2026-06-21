package com.inkflow.crm.module.client.mapper;

import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.module.client.dto.*;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    @Mapping(target = "status", expression = "java(client.getStatus().getValue())")
    @Mapping(target = "tags", expression = "java(new java.util.ArrayList<>(client.getTags()))")
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
    @Mapping(target = "status", expression = "java(com.inkflow.crm.domain.enums.ClientStatus.ACTIVE)")
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
    @Mapping(target = "status", expression = "java(request.getStatus() != null ? com.inkflow.crm.domain.enums.ClientStatus.fromValue(request.getStatus()) : client.getStatus())")
    void updateEntity(UpdateClientRequest request, @MappingTarget Client client);

    default ClientSummaryDto toSummaryDto(Client client) {
        return ClientSummaryDto.builder()
                .id(client.getId())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .phone(client.getPhone())
                .avatar(client.getAvatar())
                .hasMedicalConditions(client.hasMedicalConditions())
                .build();
    }
}
