package com.nibras.pim.web.dto;

import java.util.List;

public record SetRolePermissionsRequest(
        List<String> permissions) {
}
