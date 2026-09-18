package com.insurance.customer.service;

import com.insurance.common.exception.DuplicateResourceException;
import com.insurance.common.exception.ValidationException;
import com.insurance.customer.config.CustomerProperties;
import com.insurance.customer.dto.VehicleRequest;
import com.insurance.customer.entity.Customer;
import com.insurance.customer.entity.FuelType;
import com.insurance.customer.entity.Vehicle;
import com.insurance.customer.entity.VehicleType;
import com.insurance.customer.mapper.VehicleMapper;
import com.insurance.customer.repository.VehicleRepository;
import com.insurance.customer.security.CustomerAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private CustomerService customerService;

    private VehicleService service() {
        return new VehicleService(vehicleRepository, customerService, Mappers.getMapper(VehicleMapper.class),
                new CustomerAccessPolicy(), new CustomerProperties());
    }

    private static VehicleRequest request(String registration, int year) {
        return new VehicleRequest(registration, VehicleType.CAR, "Honda", "City", "VX", FuelType.PETROL, year, 1498,
                null, LocalDate.of(Math.max(year, 2000), 6, 1), new BigDecimal("650000.00"));
    }

    @Test
    void registrationNumberIsNormalisedAndOwnerAttached() {
        Customer owner = Customer.builder().userId(9L).email("o@x.com").firstName("O").lastName("W").build();
        when(customerService.findMine()).thenReturn(owner);
        when(vehicleRepository.existsByRegistrationNumber("MH12AB1234")).thenReturn(false);
        when(vehicleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().addToMine(request("mh 12 ab-1234", Year.now().getValue() - 2));

        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        assertThat(saved.getValue().getRegistrationNumber()).isEqualTo("MH12AB1234");
        assertThat(saved.getValue().getCustomer()).isSameAs(owner);
    }

    @Test
    void duplicateRegistrationIsRejected() {
        when(customerService.findMine()).thenReturn(Customer.builder().userId(9L).email("o@x.com").firstName("O").lastName("W").build());
        when(vehicleRepository.existsByRegistrationNumber("MH12AB1234")).thenReturn(true);

        assertThatThrownBy(() -> service().addToMine(request("MH12AB1234", Year.now().getValue() - 1)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void vehicleOlderThanConfiguredMaxAgeIsNotEligible() {
        assertThatThrownBy(() -> service().validateBusinessRules(request("MH12AB1234", Year.now().getValue() - 21)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("older than 20 years");
    }

    @Test
    void manufacturingYearInTheFutureIsRejected() {
        assertThatThrownBy(() -> service().validateBusinessRules(request("MH12AB1234", Year.now().getValue() + 1)))
                .hasMessage("Manufacturing year cannot be in the future");
    }

    @Test
    void registrationBeforeManufacturingIsRejected() {
        VehicleRequest r = new VehicleRequest("MH12AB1234", VehicleType.BIKE, "Bajaj", "Pulsar", null, FuelType.PETROL,
                Year.now().getValue() - 1, 150, null, LocalDate.of(2010, 1, 1), new BigDecimal("90000"));
        assertThatThrownBy(() -> service().validateBusinessRules(r))
                .hasMessage("Registration date cannot be before the manufacturing year");
    }
}
