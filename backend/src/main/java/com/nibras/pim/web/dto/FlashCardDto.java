package com.nibras.pim.web.dto;

import com.nibras.pim.domain.FlashCard;

import java.time.LocalDate;

/**
 * One card as the clients render it.
 *
 * {@code recordRef} is built here rather than in each client, so the phone and the web page cannot
 * disagree about how a source note is named.
 */
public record FlashCardDto(
        Long id,
        Long noteId,
        String recordRef,
        LocalDate writtenOn,
        String passage,
        boolean rtl,
        String topic,
        String state) {

    public static FlashCardDto of(FlashCard c) {
        return new FlashCardDto(
                c.getId(),
                c.getNoteId(),
                "N" + c.getNoteId(),
                c.getWrittenOn(),
                c.getPassage(),
                Boolean.TRUE.equals(c.getRtl()),
                c.getTopic(),
                c.getState());
    }
}
