package com.nibras.pim.web;

import com.nibras.pim.service.TrashService;
import com.nibras.pim.web.dto.TrashEntryDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Soft-deleted records across every module, and restoring them. */
@RestController
@RequestMapping("/api/trash")
public class TrashController {

    private final TrashService service;

    public TrashController(TrashService service) {
        this.service = service;
    }

    @GetMapping
    public List<TrashEntryDto> list() {
        return service.list();
    }

    @PostMapping("/{module}/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable String module, @PathVariable long id) {
        service.restore(module, id);
    }
}
