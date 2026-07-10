package com.nibras.pim.web;

import com.nibras.pim.domain.Writing;
import com.nibras.pim.service.WritingService;
import com.nibras.pim.web.dto.WritingDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/writings")
public class WritingController extends AbstractCrudController<Writing, WritingDto> {

    public WritingController(WritingService service) {
        super(service);
    }
}
