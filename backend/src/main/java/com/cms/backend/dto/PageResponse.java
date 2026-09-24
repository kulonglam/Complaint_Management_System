package com.cms.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PageResponse(JsonNode data, Page page) {

    public record Page(int offset, int limit, long total) {
    }
}
