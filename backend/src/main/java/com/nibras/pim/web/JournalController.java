package com.nibras.pim.web;

import com.nibras.pim.domain.Journal;
import com.nibras.pim.service.JournalService;
import com.nibras.pim.web.dto.JournalDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/journals")
public class JournalController extends AbstractCrudController<Journal, JournalDto> {

    public JournalController(JournalService service) {
        super(service);
    }
}
