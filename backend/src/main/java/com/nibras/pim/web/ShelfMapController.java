package com.nibras.pim.web;

import com.nibras.pim.service.places.ShelfMapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where resources physically are, room by room — the Shelf map report. */
@RestController
public class ShelfMapController {

    private final ShelfMapService service;

    public ShelfMapController(ShelfMapService service) {
        this.service = service;
    }

    @GetMapping("/api/shelf-map")
    public ShelfMapService.ShelfMap map() {
        return service.map();
    }
}
