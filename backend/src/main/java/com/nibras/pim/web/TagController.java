package com.nibras.pim.web;

import com.nibras.pim.service.TagService;
import com.nibras.pim.web.dto.TagDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** All tags, for the detail-pane tag autocomplete. */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tags;

    public TagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping
    public List<TagDto> all() {
        return tags.all().stream().map(t -> new TagDto(t.getId(), t.getName())).toList();
    }
}
