package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.service.service.ServiceLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentEntityResolverTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ServiceLookup serviceLookup;

    @InjectMocks
    private AppointmentEntityResolver resolver;

    @Test
    void shouldReturnAppointmentWhenFoundInTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));

        Appointment result = resolver.requireAppointment(tenantId, appointmentId);

        assertEquals(appointmentId, result.getId());
        verify(appointmentRepository).findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId);
    }

    @Test
    void shouldThrowWhenAppointmentNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireAppointment(tenantId, appointmentId));

        assertEquals(ErrorCode.APPOINTMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenAppointmentBelongsToAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireAppointment(tenantId, appointmentId));

        assertEquals(ErrorCode.APPOINTMENT_NOT_FOUND, ex.getErrorCode());
        verify(appointmentRepository).findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId);
    }

    @Test
    void shouldReturnClientWhenFoundInTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Client client = new Client();
        client.setId(clientId);

        when(clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(clientId, tenantId))
                .thenReturn(Optional.of(client));

        Client result = resolver.requireClient(tenantId, clientId);

        assertEquals(clientId, result.getId());
        verify(clientRepository).findByIdAndTenantIdAndDeletedAtIsNull(clientId, tenantId);
    }

    @Test
    void shouldThrowWhenClientNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        when(clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(clientId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireClient(tenantId, clientId));

        assertEquals(ErrorCode.CLIENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenClientBelongsToAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        when(clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(clientId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireClient(tenantId, clientId));

        assertEquals(ErrorCode.CLIENT_NOT_FOUND, ex.getErrorCode());
        verify(clientRepository).findByIdAndTenantIdAndDeletedAtIsNull(clientId, tenantId);
    }

    @Test
    void shouldReturnStaffWhenFoundInTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();

        when(staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId))
                .thenReturn(Optional.of(staff));

        Staff result = resolver.requireStaff(tenantId, staffId);

        assertEquals(staffId, result.getId());
        verify(staffRepository).findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId);
    }

    @Test
    void shouldThrowWhenStaffNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        when(staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireStaff(tenantId, staffId));

        assertEquals(ErrorCode.STAFF_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenStaffBelongsToAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        when(staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireStaff(tenantId, staffId));

        assertEquals(ErrorCode.STAFF_NOT_FOUND, ex.getErrorCode());
        verify(staffRepository).findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId);
    }

    @Test
    void shouldReturnServiceWhenFoundInTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Service service = Service.builder().id(serviceId).tenantId(tenantId).title("Tattoo").build();

        when(serviceLookup.require(tenantId, serviceId)).thenReturn(service);

        Service result = resolver.requireService(tenantId, serviceId);

        assertEquals(serviceId, result.getId());
        verify(serviceLookup).require(tenantId, serviceId);
    }

    @Test
    void shouldThrowWhenServiceNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        when(serviceLookup.require(tenantId, serviceId))
                .thenThrow(ResourceNotFoundException.service(serviceId.toString()));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireService(tenantId, serviceId));

        assertEquals(ErrorCode.SERVICE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldReturnLocationWhenFoundInTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        Location location = Location.builder().id(locationId).tenantId(tenantId).name("Studio").build();

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.of(location));

        Location result = resolver.requireLocation(tenantId, locationId);

        assertEquals(locationId, result.getId());
        verify(locationRepository).findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId);
    }

    @Test
    void shouldThrowWhenLocationNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireLocation(tenantId, locationId));

        assertEquals(ErrorCode.LOCATION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenLocationBelongsToAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireLocation(tenantId, locationId));

        assertEquals(ErrorCode.LOCATION_NOT_FOUND, ex.getErrorCode());
        verify(locationRepository).findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId);
    }

    @Test
    void shouldReturnProjectWhenFoundInTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).tenantId(tenantId).title("Sleeve").build();

        when(projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(projectId, tenantId))
                .thenReturn(Optional.of(project));

        Project result = resolver.requireProject(tenantId, projectId);

        assertEquals(projectId, result.getId());
        verify(projectRepository).findByIdAndTenantIdAndDeletedAtIsNull(projectId, tenantId);
    }

    @Test
    void shouldThrowWhenProjectNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(projectId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireProject(tenantId, projectId));

        assertEquals(ErrorCode.PROJECT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenProjectBelongsToAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(projectId, tenantId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> resolver.requireProject(tenantId, projectId));

        assertEquals(ErrorCode.PROJECT_NOT_FOUND, ex.getErrorCode());
        verify(projectRepository).findByIdAndTenantIdAndDeletedAtIsNull(projectId, tenantId);
    }
}
