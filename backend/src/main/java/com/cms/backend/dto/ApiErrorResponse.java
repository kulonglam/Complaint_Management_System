package com.cms.backend.dto;

public record ApiErrorResponse(String message, String code, int status) {
}
