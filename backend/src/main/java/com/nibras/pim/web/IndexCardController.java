package com.nibras.pim.web;

import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.service.IndexCardService;
import com.nibras.pim.web.dto.IndexCardDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
public class IndexCardController extends AbstractCrudController<IndexCard, IndexCardDto> {

    public IndexCardController(IndexCardService service) {
        super(service);
    }
}
