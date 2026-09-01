package com.pos.sales.service;

import com.pos.sales.domain.Sale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * FBR Integration Service boundary.
 * 
 * Responsible for submitting completed sales to the Federal Board of Revenue
 * via a licensed integrator's API.
 */
@Service
public class FbrIntegrationService {

    private final FbrProvider provider;

    @Autowired
    public FbrIntegrationService(@Autowired(required = false) FbrProvider provider) {
        this.provider = provider;
    }

    public record FbrSubmissionResult(
        String providerCode,
        String environment,
        String status,
        String requestId,
        String invoiceNumber,
        String qrCode,
        String errorMessage
    ) {}

    /**
     * Submits a sale to the FBR integration.
     * 
     * @param sale The completed sale to submit.
     * @return Structured FbrSubmissionResult.
     */
    public FbrSubmissionResult submitSale(Sale sale) {
        if (provider != null && provider.isConfigured()) {
            return provider.submitInvoice(sale);
        }

        // Architecture boundary: Real integration goes here.
        // Returning NOT_CONFIGURED safely without credentials.
        return new FbrSubmissionResult(
                null,
                null,
                "NOT_CONFIGURED",
                null,
                null,
                null,
                null
        );
    }
}
