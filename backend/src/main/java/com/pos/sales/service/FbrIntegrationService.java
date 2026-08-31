package com.pos.sales.service;

import com.pos.sales.domain.Sale;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * FBR Integration Service boundary.
 * 
 * Responsible for submitting completed sales to the Federal Board of Revenue
 * via a licensed integrator's API.
 */
@Service
public class FbrIntegrationService {

    public record FbrData(String invoiceNumber, String qrCode) {}

    /**
     * Submits a sale to the FBR integration.
     * 
     * @param sale The completed sale to submit.
     * @return Optional FbrData if successful, Optional.empty() if not configured or failed.
     */
    public Optional<FbrData> submitSale(Sale sale) {
        // Architecture boundary: Real integration goes here.
        // For now, FBR configuration is just a frontend shell, and we are strictly forbidden
        // from generating fake FBR invoice numbers or QR codes.
        // Returning empty preserves the sale normally and signals the UI to show "Not connected".
        return Optional.empty();
    }
}
