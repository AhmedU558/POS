package com.pos.users.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @NotNull Boolean active
) {}
