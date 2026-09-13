package com.nibras.pim.repo;

import com.nibras.pim.domain.FlashCard;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FlashCardRepository extends JpaRepository<FlashCard, Long> {

    /**
     * The next undecided cards in bank order, capped.
     *
     * The cap is the feature: 22,000 passages is not a list anyone starts, so only a working set is
     * ever offered and the rest stays out of sight until it is earned.
     */
    List<FlashCard> findByStateOrderByPositionAsc(String state, Limit limit);

    /** Every card in a state, uncapped — for REPEAT and KEPT, which are commitments, not a queue. */
    List<FlashCard> findByStateOrderByPositionAsc(String state);

    long countByState(String state);

    Optional<FlashCard> findByCardKey(String cardKey);

    @Query("select count(c) from FlashCard c")
    long countAll();
}
