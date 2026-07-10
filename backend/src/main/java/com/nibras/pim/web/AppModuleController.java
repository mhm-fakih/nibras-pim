package com.nibras.pim.web;

import com.nibras.pim.service.AppModuleService;
import com.nibras.pim.web.dto.AppModuleDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Module display settings: enable/disable and assign command codes. */
@RestController
@RequestMapping("/api/modules")
public class AppModuleController {

    private final AppModuleService service;

    public AppModuleController(AppModuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<AppModuleDto> list() {
        return service.list();
    }

    @PutMapping("/{key}")
    public AppModuleDto update(@PathVariable String key, @RequestBody AppModuleDto dto) {
        return service.update(key, dto);
    }
}
