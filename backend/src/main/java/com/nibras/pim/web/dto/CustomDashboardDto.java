package com.nibras.pim.web.dto;

import java.util.List;

public record CustomDashboardDto(Long id, String name, Integer columnsCount, Integer refreshMinutes,
                                 String columnWidths,
                                 /** Read-only here: saved through PUT /{id}/row-heights. */
                                 String rowHeights,
                                 List<CustomDashboardRegionDto> regions) {
}
