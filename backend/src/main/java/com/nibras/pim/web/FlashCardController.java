package com.nibras.pim.web;

import com.nibras.pim.service.FlashCardService;
import com.nibras.pim.web.dto.FlashCardDto;
import com.nibras.pim.web.dto.FlashCardSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The flash-card review job, shared by the phone and the web page.
 *
 * <p>Both clients read the same lists and write the same decisions here, so a card reviewed on the
 * phone is gone from the browser too. That is the whole reason this moved out of the app's own
 * assets and into a table.
 */
@RestController
@RequestMapping("/api/flashcards")
public class FlashCardController {

    private final FlashCardService service;

    public FlashCardController(FlashCardService service) {
        this.service = service;
    }

    /** Cards in one state: NEW is capped to the working set, the rest are complete. */
    @GetMapping
    public List<FlashCardDto> list(@RequestParam(defaultValue = "NEW") String state) {
        return service.list(state);
    }

    @GetMapping("/summary")
    public FlashCardSummary summary() {
        return service.summary();
    }

    /** Record a decision. {@code NEW} puts the card back in the queue. */
    @PutMapping("/{id}/state")
    public FlashCardDto decide(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.decide(id, body.get("state"));
    }
}
