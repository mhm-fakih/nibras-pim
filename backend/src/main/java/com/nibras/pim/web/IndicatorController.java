package com.nibras.pim.web;

import com.nibras.pim.domain.Indicator;
import com.nibras.pim.service.IndicatorService;
import com.nibras.pim.web.dto.IndicatorDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/indicators")
public class IndicatorController extends AbstractCrudController<Indicator, IndicatorDto> {

    public IndicatorController(IndicatorService service) {
        super(service);
    }
}
