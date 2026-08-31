package com.pos.sales.service;

import com.pos.sales.domain.Sale;

/**
 * Contract for a licensed FBR integrator provider.
 */
public interface FbrProvider {
    /**
     * Checks if the provider is fully configured with credentials.
     */
    boolean isConfigured();

    /**
     * Submits an invoice to the provider.
     */
    FbrIntegrationService.FbrSubmissionResult submitInvoice(Sale sale);
}
