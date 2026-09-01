package com.pos.customers.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.customers.domain.Customer;
import com.pos.customers.dto.CustomerCreateRequest;
import com.pos.customers.dto.CustomerResponse;
import com.pos.customers.dto.CustomerUpdateRequest;
import com.pos.customers.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuditRecorder auditRecorder;

    public CustomerService(CustomerRepository customerRepository, AuditRecorder auditRecorder) {
        this.customerRepository = customerRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String query, Boolean isActive, Pageable pageable) {
        String normalized = query == null ? "" : query.trim();
        return customerRepository.search(normalized, isActive, pageable).map(CustomerResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return CustomerResponse.fromEntity(requireCustomer(id));
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request) {
        if (customerRepository.existsByCustomerCode(request.customerCode().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Customer code already exists");
        }
        Customer customer = new Customer();
        apply(
                customer,
                request.customerCode(),
                request.name(),
                request.phone(),
                request.email(),
                request.address(),
                request.creditLimit(),
                request.isActive());
        Customer saved = customerRepository.save(customer);
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                "CUSTOMER_CREATED",
                "Customer",
                saved.getId()));
        return CustomerResponse.fromEntity(saved);
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerUpdateRequest request) {
        Customer customer = requireCustomer(id);
        if (customerRepository.existsByCustomerCodeAndIdNot(request.customerCode().trim(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Customer code already exists");
        }
        apply(
                customer,
                request.customerCode(),
                request.name(),
                request.phone(),
                request.email(),
                request.address(),
                request.creditLimit(),
                request.isActive());
        Customer saved = customerRepository.save(customer);
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUserId()),
                "CUSTOMER_UPDATED",
                "Customer",
                saved.getId()));
        return CustomerResponse.fromEntity(saved);
    }

    private Customer requireCustomer(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Customer not found"));
    }

    private static void apply(
            Customer customer,
            String customerCode,
            String name,
            String phone,
            String email,
            String address,
            java.math.BigDecimal creditLimit,
            boolean active) {
        customer.setCustomerCode(customerCode.trim());
        customer.setName(name.trim());
        customer.setPhone(blankToNull(phone));
        customer.setEmail(blankToNull(email));
        customer.setAddress(blankToNull(address));
        customer.setCreditLimit(creditLimit);
        customer.setActive(active);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UUID currentUserId() {
        CustomUserDetails user = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return user.getId();
    }
}
