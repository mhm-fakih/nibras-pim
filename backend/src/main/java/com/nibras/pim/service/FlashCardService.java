package com.nibras.pim.service;

import com.nibras.pim.domain.FlashCard;
import com.nibras.pim.domain.FlashCardState;
import com.nibras.pim.repo.FlashCardRepository;
import com.nibras.pim.web.dto.FlashCardDto;
import com.nibras.pim.web.dto.FlashCardSummary;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The flash-card review job: hand out a working set, record what was decided.
 *
 * <p>One rule shapes the whole thing: the undecided queue is capped and everything else is not.
 * A bank of twenty-odd thousand passages is not a list anyone starts, so only {@link #QUEUE_SIZE}
 * are ever offered and the rest stays out of sight. REPEAT and KEPT are the opposite — having said
 * "come back to this" or "I want to edit that note", the app has no business truncating the result.
 */
@Service
public class FlashCardService {

    /** How many undecided cards are offered at once. The rest of the bank stays hidden. */
    public static final int QUEUE_SIZE = 40;

    private final FlashCardRepository repo;

    public FlashCardService(FlashCardRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<FlashCardDto> list(String stateName) {
        FlashCardState state = requireState(stateName);
        List<FlashCard> rows = state == FlashCardState.NEW
                ? repo.findByStateOrderByPositionAsc(state.name(), Limit.of(QUEUE_SIZE))
                : repo.findByStateOrderByPositionAsc(state.name());
        return rows.stream().map(FlashCardDto::of).toList();
    }

    @Transactional(readOnly = true)
    public FlashCardSummary summary() {
        return new FlashCardSummary(
                repo.countAll(),
                repo.countByState(FlashCardState.NEW.name()),
                repo.countByState(FlashCardState.REVIEWED.name()),
                repo.countByState(FlashCardState.REPEAT.name()),
                repo.countByState(FlashCardState.KEPT.name()));
    }

    /**
     * Record a decision. Passing {@code NEW} puts a card back into the queue, which is how a
     * repeated or kept card is un-set.
     */
    @Transactional
    public FlashCardDto decide(Long id, String stateName) {
        FlashCardState state = requireState(stateName);
        FlashCard card = repo.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No flash card " + id));
        card.setState(state.name());
        // Cleared rather than kept when a card goes back to NEW: "decided on" would otherwise
        // outlive the decision it dates.
        card.setDecidedOn(state == FlashCardState.NEW ? null : LocalDateTime.now());
        return FlashCardDto.of(repo.save(card));
    }

    private FlashCardState requireState(String name) {
        FlashCardState state = FlashCardState.of(name);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown flash card state '" + name + "'");
        }
        return state;
    }
}
