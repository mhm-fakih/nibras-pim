package com.nibras.pim.web.dto;

import com.nibras.pim.domain.AppModule;

public record AppModuleDto(
        String moduleKey,
        String label,
        String code,
        String color,
        Boolean enabled,
        Integer sortOrder,
        Boolean bucketBy100,
        Boolean bucketByType) {

    public static AppModuleDto of(AppModule m) {
        return new AppModuleDto(m.getModuleKey(), m.getLabel(), m.getCode(), m.getColor(),
                m.getEnabled(), m.getSortOrder(), m.getBucketBy100(), m.getBucketByType());
    }
}
