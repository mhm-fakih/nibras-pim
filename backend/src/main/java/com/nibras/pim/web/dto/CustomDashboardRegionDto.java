package com.nibras.pim.web.dto;

/** {@code config} is an opaque JSON string — the frontend defines the shape per componentType. */
public record CustomDashboardRegionDto(
        Long id, Integer orderIndex, String componentType, String config, Integer colSpan) {
}
