package com.pos.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record ProductStatusUpdateRequest(
    @NotNull Boolean isActive
) {}
