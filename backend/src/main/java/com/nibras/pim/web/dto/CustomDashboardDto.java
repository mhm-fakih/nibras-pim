package com.nibras.pim.web.dto;

import java.util.List;

public record CustomDashboardDto(Long id, String name, Integer columnsCount, Integer refreshMinutes,
                                 List<CustomDashboardRegionDto> regions) {
}
