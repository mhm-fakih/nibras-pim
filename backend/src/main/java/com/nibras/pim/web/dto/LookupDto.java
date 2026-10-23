package com.nibras.pim.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A dropdown option. [active] is set only for task statuses (work_status.marked_as_active), so the
 * list's status filter can offer Active / Not active first; every other lookup leaves it out.
 */
public record LookupDto(Long id, String name, String code, String style,
                        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean active) {

    public LookupDto(Long id, String name, String code, String style) {
        this(id, name, code, style, null);
    }
}
