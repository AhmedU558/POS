package com.pos.common.response;

/**
 * Field-level error detail, per REST API Specification section 5.2.
 */
public record ApiErrorDetail(String field, String message) {}
