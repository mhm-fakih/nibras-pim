package com.nibras.pim.web;

import com.nibras.pim.domain.Excerpt;
import com.nibras.pim.service.ExcerptService;
import com.nibras.pim.web.dto.ExcerptDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/excerpts")
public class ExcerptController extends AbstractCrudController<Excerpt, ExcerptDto> {

    public ExcerptController(ExcerptService service) {
        super(service);
    }
}
