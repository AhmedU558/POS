package com.pos.customers.dto;

import com.pos.customers.domain.Customer;
import com.pos.customers.domain.CustomerCredit;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerCreditResponse(
        UUID customerId,
        String customerCode,
        String name,
        BigDecimal creditLimit,
        BigDecimal balance,
        String currencyCode,
        String status,
        Page<CustomerCreditTransactionResponse> transactions
) {
    public static CustomerCreditResponse of(
            Customer customer,
            CustomerCredit credit,
            Page<CustomerCreditTransactionResponse> transactions) {
        return new CustomerCreditResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getName(),
                customer.getCreditLimit(),
                credit == null ? BigDecimal.ZERO : credit.getBalance(),
                credit == null ? null : credit.getCurrencyCode().trim(),
                credit == null ? null : credit.getStatus(),
                transactions
        );
    }
}
