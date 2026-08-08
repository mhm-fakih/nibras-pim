package com.nibras.pim.web.dto;

/** Plain-text content of a file read for the dashboard builder's "file" component. */
public record DashboardFileContentDto(String path, String content, boolean truncated) {
}
